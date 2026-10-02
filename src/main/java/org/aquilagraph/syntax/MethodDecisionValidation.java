package org.aquilagraph.syntax;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.InvocationMode;
import org.aquilagraph.model.InvocationSemantics;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.syntax.MethodDecisionIndex.UseKey;

/** Checks supplied lowering facts against the admitted body and locally available source evidence. */
final class MethodDecisionValidation {
  private static final int MAX_ENUM_CONTEXT_CONSUMERS = 1;

  private MethodDecisionValidation() {
  }

  static MethodDecisionIndex validate(String methodKey, SyntaxNode root, MethodDecisionRegistration registration,
      Map<String, SyntaxNode> sourceNodes) {
    var nodes = new HashMap<String, SyntaxNode>();
    root.descendantsAndSelf().forEach(node -> nodes.put(node.nodeId(), node));
    var uses = validateUses(methodKey, root, registration.uses(), nodes, sourceNodes);
    var enumContexts = validateEnumContexts(methodKey, root, registration.enumContexts(), nodes, sourceNodes);
    var invocations = validateInvocations(methodKey, registration.invocations(), nodes, uses, sourceNodes);
    var staticAccesses = validateStaticAccesses(methodKey, registration.staticAccesses(), nodes, sourceNodes);
    var concatenations = validateConcatenations(methodKey, registration.concatenations(), nodes);
    nodes.values().stream().filter(MethodDecisionValidation::isCreation).forEach(MethodDecisionValidation::validateCreation);
    nodes.values().stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_ACCESS || node.syntaxKind() == SyntaxKind.ARRAY_LOCATION)
        .forEach(MethodDecisionValidation::validateArray);
    var requiredEnumContextCalls = nodes.values().stream().filter(node -> requiresEnumContext(methodKey, node, sourceNodes))
        .map(SyntaxNode::nodeId).collect(Collectors.toSet());
    return new MethodDecisionIndex(uses, invocations, staticAccesses, concatenations, enumContexts, requiredEnumContextCalls);
  }

  private static Map<UseKey, TypedUseDecision> validateUses(String methodKey, SyntaxNode root, List<TypedUseDecision> decisions,
      Map<String, SyntaxNode> nodes, Map<String, SyntaxNode> sourceNodes) {
    var uses = new HashMap<UseKey, TypedUseDecision>();
    var resultTargets = new HashMap<String, JavaTypeIdentity>();
    collectSwitchTargets(root, Optional.empty(), resultTargets);
    var returnTarget = sourceNodes.values().stream()
        .filter(
            node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration && declaration.declarationKey().equals(methodKey))
        .flatMap(node -> node.children(SyntaxRole.RETURN_TYPE).stream()).findFirst().flatMap(SyntaxNode::resolvedType);
    for (var decision : decisions) {
      var consumer = ownedNode(methodKey, decision.methodKey(), decision.consumerNodeId(), nodes);
      var child = eligibleChild(consumer, decision);
      child.node().resolvedType().ifPresent(type -> requireEqual(type, decision.sourceType(), "Typed-use source type"));
      if (decision.checkedReferenceTarget().isPresent() && consumer.syntaxKind() != SyntaxKind.TYPE_CAST) {
        throw new IllegalArgumentException("A checked-reference unboxing step requires a cast operand");
      }
      if (decision.referenceCheckRequired().isPresent() && consumer.syntaxKind() != SyntaxKind.TYPE_CAST) {
        throw new IllegalArgumentException("A reference-cast classification requires a cast operand");
      }
      validatePrimitiveContext(consumer, decision);
      expectedTarget(consumer, decision.role(), resultTargets, returnTarget)
          .ifPresent(target -> requireEqual(target, decision.selectedTarget(), "Typed-use selected target"));
      putUnique(uses, UseKey.from(decision.consumerNodeId(), decision.role(), decision.position()), decision, "typed-use");
    }
    return uses;
  }

  private static SyntaxChild eligibleChild(SyntaxNode consumer, TypedUseDecision decision) {
    return eligibleUses(consumer).stream()
        .filter(candidate -> candidate.role() == decision.role() && candidate.position() == decision.position()).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("A typed-use decision must select an eligible direct child"));
  }

  private static Optional<JavaTypeIdentity> expectedTarget(SyntaxNode consumer, SyntaxRole role,
      Map<String, JavaTypeIdentity> resultTargets, Optional<JavaTypeIdentity> returnTarget) {
    return switch (consumer.syntaxKind()) {
      case LOCAL_DECLARATION, TYPE_CAST, UNARY -> consumer.resolvedType();
      case CONDITIONAL_EXPRESSION -> role == SyntaxRole.CONDITION ? Optional.empty() : consumer.resolvedType();
      case ASSIGNMENT -> consumer.requiredChild(SyntaxRole.LOCATION).resolvedType();
      case RETURN -> returnTarget;
      case CASE, YIELD -> Optional.ofNullable(resultTargets.get(consumer.nodeId()));
      default -> Optional.empty();
    };
  }

  private static void validatePrimitiveContext(SyntaxNode consumer, TypedUseDecision decision) {
    if (decision.role() == SyntaxRole.INDEX && decision.selectedTarget().category() != JavaTypeCategory.INT) {
      throw new IllegalArgumentException("An array index use must select int");
    }
    if ((decision.role() == SyntaxRole.CONDITION || consumer.syntaxKind() == SyntaxKind.CONDITIONAL_AND
        || consumer.syntaxKind() == SyntaxKind.CONDITIONAL_OR) && decision.selectedTarget().category() != JavaTypeCategory.BOOLEAN) {
      throw new IllegalArgumentException("A condition use must select boolean");
    }
  }

  private static void collectSwitchTargets(SyntaxNode node, Optional<JavaTypeIdentity> enclosingSwitch,
      Map<String, JavaTypeIdentity> targets) {
    var selectedSwitch = node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION ? node.resolvedType() : enclosingSwitch;
    if (node.syntaxKind() == SyntaxKind.CASE || node.syntaxKind() == SyntaxKind.YIELD) {
      selectedSwitch.ifPresent(type -> targets.put(node.nodeId(), type));
    }
    node.children().forEach(child -> collectSwitchTargets(child.node(), selectedSwitch, targets));
  }

  private static Map<String, InvocationArityDecision> validateInvocations(String methodKey, List<InvocationArityDecision> decisions,
      Map<String, SyntaxNode> nodes, Map<UseKey, TypedUseDecision> uses, Map<String, SyntaxNode> sourceNodes) {
    var invocations = new HashMap<String, InvocationArityDecision>();
    for (var decision : decisions) {
      var node = ownedNode(methodKey, decision.methodKey(), decision.consumerNodeId(), nodes);
      if (!isInvocation(node)) {
        throw new IllegalArgumentException("An invocation-arity decision requires an invocation node");
      }
      var argumentCount = node.children(SyntaxRole.ARGUMENT).size();
      var implicitEnumSuper = isInsertedEnumSuperclass(methodKey, node, sourceNodes);
      if ((implicitEnumSuper && (decision.mode() != InvocationArityMode.FIXED || decision.formalCount() != 2 || argumentCount != 0))
          || (decision.mode() == InvocationArityMode.FIXED && !implicitEnumSuper && argumentCount != decision.formalCount())
          || (decision.mode() == InvocationArityMode.VARIABLE && argumentCount < decision.formalCount() - 1)) {
        throw new IllegalArgumentException("Invocation applicability contradicts source argument count");
      }
      if (decision.mode() == InvocationArityMode.VARIABLE) {
        var element = decision.elementType().orElseThrow();
        if (element.category() == JavaTypeCategory.NULL) {
          throw new IllegalArgumentException("A variable-arity allocation cannot have a null-type component");
        }
        requireEqual(element.erasedName() + "[]", decision.erasedArrayType().orElseThrow().erasedName(),
            "Variable-arity allocation component erasure");
        for (var position = decision.formalCount() - 1; position < argumentCount; position++) {
          var use = uses.get(UseKey.from(node.nodeId(), SyntaxRole.ARGUMENT, position));
          if (use != null) {
            requireEqual(element, use.selectedTarget(), "Variable-arity argument target");
          }
        }
      }
      putUnique(invocations, node.nodeId(), decision, "invocation-arity");
    }
    return invocations;
  }

  private static Map<String, EnumConstructorContextDecision> validateEnumContexts(String methodKey, SyntaxNode root,
      List<EnumConstructorContextDecision> decisions, Map<String, SyntaxNode> nodes, Map<String, SyntaxNode> sourceNodes) {
    var contexts = new HashMap<String, EnumConstructorContextDecision>();
    var directInvocations = new IdentityHashMap<SyntaxNode, Boolean>();
    root.children(SyntaxRole.STATEMENT).stream().filter(statement -> statement.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT)
        .forEach(statement -> directInvocations.put(statement.requiredChild(SyntaxRole.EXPRESSION), Boolean.TRUE));
    for (var decision : decisions) {
      var node = ownedNode(methodKey, decision.methodKey(), decision.invocationNodeId(), nodes);
      if (!requiresEnumContext(methodKey, node, sourceNodes) || sourceNodes.values().stream()
          .noneMatch(owner -> owner.sourceKind() == JavaSourceKind.ENUM && owner.sourceFileKey().equals(root.sourceFileKey())
              && owner.value() instanceof SyntaxValue.TypeDeclaration declaration
              && declaration.declarationKey().equals(decision.enumOwnerKey()))
          || !directInvocations.containsKey(node)) {
        throw new IllegalArgumentException("Enum context requires an actual source enum and direct constructor invocation");
      }
      var invocation = node.value(SyntaxValue.Invocation.class);
      var semantics = selectedEnumInvocationSemantics(invocation);
      var reference = node.requiredReference();
      if (semantics.mode() != InvocationMode.CONSTRUCTOR || reference.referenceKind() != ReferenceKind.CONSTRUCTOR
          || reference.status() != ResolutionStatus.RESOLVED
          || !reference.symbolKey().equals(Optional.of(decision.selectedConstructorKey()))) {
        throw new IllegalArgumentException("Enum context contradicts the selected constructor reference");
      }
      if (decision.target() == EnumConstructorContextDecision.Target.ENUM_SUPERCLASS) {
        if (invocation.form() != InvocationForm.SUPER_CONSTRUCTOR || node.origin().isPresent()
            || !node.children(SyntaxRole.ARGUMENT).isEmpty()
            || !semantics.qualifyingType().declarationKey().equals(Optional.of("type:java.lang.Enum"))
            || !"java.lang.Enum".equals(semantics.qualifyingType().identity().erasedName())) {
          throw new IllegalArgumentException("Enum superclass context requires an inserted zero-source-argument Enum call");
        }
      } else if (invocation.form() != InvocationForm.THIS_CONSTRUCTOR
          || !semantics.qualifyingType().declarationKey().equals(Optional.of(decision.enumOwnerKey()))) {
        throw new IllegalArgumentException("Enum delegation context requires a selected constructor in the same enum");
      }
      putUnique(contexts, node.nodeId(), decision, "enum-constructor-context");
    }
    if (contexts.size() > MAX_ENUM_CONTEXT_CONSUMERS) {
      throw new IllegalArgumentException("One enum constructor body has one incoming-context consumer");
    }
    return contexts;
  }

  private static InvocationSemantics selectedEnumInvocationSemantics(SyntaxValue.Invocation invocation) {
    return invocation.semantics().orElseThrow(() -> new IllegalArgumentException("Enum context requires selected semantics"));
  }

  private static boolean requiresEnumContext(String methodKey, SyntaxNode node, Map<String, SyntaxNode> sourceNodes) {
    if (node.syntaxKind() != SyntaxKind.INVOCATION || !(node.value() instanceof SyntaxValue.Invocation invocation)
        || (invocation.form() != InvocationForm.THIS_CONSTRUCTOR && invocation.form() != InvocationForm.SUPER_CONSTRUCTOR)) {
      return false;
    }
    return sourceNodes.values().stream()
        .anyMatch(owner -> owner.sourceKind() == JavaSourceKind.ENUM && owner.sourceFileKey().equals(node.sourceFileKey())
            && owner.value() instanceof SyntaxValue.TypeDeclaration declaration
            && methodKey.startsWith(declaration.declarationKey() + "#<init>("));
  }

  static boolean hasMissingEnumContext(SyntaxNode node, MethodDecisionIndex decisions) {
    return decisions.requiredEnumContextCalls().contains(node.nodeId()) && !decisions.enumContexts().containsKey(node.nodeId());
  }

  private static boolean isInsertedEnumSuperclass(String methodKey, SyntaxNode node, Map<String, SyntaxNode> sourceNodes) {
    if (!requiresEnumContext(methodKey, node, sourceNodes) || node.origin().isPresent() || !node.children(SyntaxRole.ARGUMENT).isEmpty()) {
      return false;
    }
    var invocation = node.value(SyntaxValue.Invocation.class);
    if (invocation.form() != InvocationForm.SUPER_CONSTRUCTOR) {
      return false;
    }
    return invocation.semantics()
        .filter(semantics -> semantics.mode() == InvocationMode.CONSTRUCTOR
            && semantics.qualifyingType().declarationKey().equals(Optional.of("type:java.lang.Enum"))
            && "java.lang.Enum".equals(semantics.qualifyingType().identity().erasedName()))
        .isPresent()
        && node.semanticReference()
            .filter(reference -> reference.referenceKind() == ReferenceKind.CONSTRUCTOR && reference.status() == ResolutionStatus.RESOLVED
                && reference.symbolKey().equals(Optional.of("type:java.lang.Enum#<init>(java.lang.String,int)")))
            .isPresent();
  }

  private static Map<String, StaticAccessDecision> validateStaticAccesses(String methodKey, List<StaticAccessDecision> decisions,
      Map<String, SyntaxNode> nodes, Map<String, SyntaxNode> sourceNodes) {
    var accesses = new HashMap<String, StaticAccessDecision>();
    for (var decision : decisions) {
      var node = ownedNode(methodKey, decision.methodKey(), decision.accessNodeId(), nodes);
      if (!staticKind(node).equals(Optional.of(decision.kind()))) {
        throw new IllegalArgumentException("Static-access decision contradicts the access kind or receiver form");
      }
      var reference = node.requiredReference();
      var expectedKind = decision.kind() == StaticAccessKind.METHOD_INVOKE ? ReferenceKind.METHOD : ReferenceKind.FIELD;
      if (reference.referenceKind() != expectedKind
          && !(expectedKind == ReferenceKind.FIELD && reference.referenceKind() == ReferenceKind.ENUM_CONSTANT)) {
        throw new IllegalArgumentException("Static-access decision requires the selected field or method reference");
      }
      if (reference.status() == ResolutionStatus.RESOLVED) {
        reference.symbolKey().ifPresent(key -> requireEqual(key, decision.selectedMemberKey(), "Static-access member"));
      }
      validateSourceOwner(decision, reference.referenceKind(), sourceNodes);
      putUnique(accesses, node.nodeId(), decision, "static-access");
    }
    return accesses;
  }

  private static void validateSourceOwner(StaticAccessDecision decision, ReferenceKind referenceKind, Map<String, SyntaxNode> sourceNodes) {
    for (var node : sourceNodes.values()) {
      Optional<String> owner = Optional.empty();
      if (node.value() instanceof SyntaxValue.VariableDeclaration declaration
          && declaration.declarationKey().equals(decision.selectedMemberKey())) {
        validateSourceField(decision, referenceKind, declaration);
        owner = Optional.of(declaration.ownerKey());
      } else if (node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
          && declaration.declarationKey().equals(decision.selectedMemberKey())) {
        if (decision.kind() != StaticAccessKind.METHOD_INVOKE || !declaration.effectiveModifiers().contains(JavaDeclarationModifier.STATIC)
            || declaration.kind() != SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD) {
          throw new IllegalArgumentException("Static-access decision contradicts its source executable declaration");
        }
        owner = Optional.of(declaration.ownerKey());
      }
      if (owner.isPresent() && decision.declaringType().declarationKey().isPresent()) {
        requireEqual(owner.orElseThrow(), decision.declaringType().declarationKey().orElseThrow(), "Static-access declaring owner");
      }
    }
  }

  private static void validateSourceField(StaticAccessDecision decision, ReferenceKind referenceKind,
      SyntaxValue.VariableDeclaration declaration) {
    var expectedKind = declaration.kind() == SyntaxValue.VariableDeclaration.Kind.FIELD ? ReferenceKind.FIELD : ReferenceKind.ENUM_CONSTANT;
    if (decision.kind() == StaticAccessKind.METHOD_INVOKE || referenceKind != expectedKind
        || !declaration.effectiveModifiers().contains(JavaDeclarationModifier.STATIC)) {
      throw new IllegalArgumentException("Static-access decision contradicts its source field declaration");
    }
    if (decision.constantVariableRead()
        && (!declaration.effectiveModifiers().contains(JavaDeclarationModifier.FINAL) || !declaration.initializer()
            || !(DecisionValueValidation.isPrimitive(declaration.declaredType()) || isString(declaration.declaredType())))) {
      throw new IllegalArgumentException("Constant-variable read contradicts its source field declaration");
    }
  }

  private static Map<String, StringConcatenationDecision> validateConcatenations(String methodKey,
      List<StringConcatenationDecision> decisions, Map<String, SyntaxNode> nodes) {
    var concatenations = new HashMap<String, StringConcatenationDecision>();
    for (var decision : decisions) {
      var node = ownedNode(methodKey, decision.methodKey(), decision.binaryNodeId(), nodes);
      if (!isPlus(node)) {
        throw new IllegalArgumentException("String concatenation requires a binary PLUS node");
      }
      node.resolvedType().ifPresent(type -> requireEqual(type, decision.stringType(), "String concatenation result"));
      var left = node.requiredChild(SyntaxRole.LEFT_OPERAND).resolvedType();
      var right = node.requiredChild(SyntaxRole.RIGHT_OPERAND).resolvedType();
      if (left.isPresent() && right.isPresent()) {
        var leftString = isString(left.orElseThrow());
        var rightString = isString(right.orElseThrow());
        if (!leftString && !rightString) {
          throw new IllegalArgumentException("String concatenation requires a String operand");
        }
        var side = leftString && rightString
            ? Optional.<SyntaxRole>empty()
            : Optional.of(leftString ? SyntaxRole.RIGHT_OPERAND : SyntaxRole.LEFT_OPERAND);
        requireEqual(side, decision.convertedSide(), "String concatenation converted side");
      }
      putUnique(concatenations, node.nodeId(), decision, "String-concatenation");
    }
    return concatenations;
  }

  private static void validateCreation(SyntaxNode node) {
    node.value(SyntaxValue.Invocation.class).semantics().ifPresent(semantics -> {
      var owner = semantics.qualifyingType().identity();
      if (owner.category() != JavaTypeCategory.DECLARED) {
        throw new IllegalArgumentException("Object creation initialization requires a declared constructor owner");
      }
      node.resolvedType().ifPresent(type -> requireEqual(type.erasedName(), owner.erasedName(), "Object creation owner erasure"));
    });
  }

  private static void validateArray(SyntaxNode node) {
    node.requiredChild(SyntaxRole.ARRAY).resolvedType().ifPresent(array -> {
      if (array.category() != JavaTypeCategory.ARRAY) {
        throw new IllegalArgumentException("Array access requires a resolved array expression");
      }
      node.resolvedType().ifPresent(element -> requireEqual(element.erasedName() + "[]", array.erasedName(), "Array component erasure"));
    });
  }

  static List<SyntaxChild> eligibleUses(SyntaxNode consumer) {
    return consumer.children().stream().filter(child -> eligibleRole(consumer, child)).toList();
  }

  private static boolean eligibleRole(SyntaxNode consumer, SyntaxChild child) {
    return switch (consumer.syntaxKind()) {
      case CASE -> child.role() == SyntaxRole.BODY && consumer.value(SyntaxValue.SwitchCase.class).form() == SyntaxValue.SwitchCaseForm.RULE
          && child.node().syntaxKind().requiresType();
      case INVOCATION, OBJECT_CREATION -> child.role() == SyntaxRole.ARGUMENT;
      default -> TypedUseDecision.supportsFixedRole(consumer.syntaxKind(), child.role());
    };
  }

  static boolean hasMissingDecisions(SyntaxNode node, MethodDecisionIndex decisions) {
    if (eligibleUses(node).stream().anyMatch(child -> decisions.use(node.nodeId(), child.role(), child.position()).isEmpty())) {
      return true;
    }
    if (node.syntaxKind() == SyntaxKind.TYPE_CAST) {
      var cast = decisions.use(node.nodeId(), SyntaxRole.EXPRESSION, 0);
      if (cast.isPresent() && !DecisionValueValidation.isPrimitive(cast.orElseThrow().sourceType())
          && DecisionValueValidation.isReference(cast.orElseThrow().selectedTarget())
          && cast.orElseThrow().referenceCheckRequired().isEmpty()) {
        return true;
      }
    }
    if (isInvocation(node) && !decisions.invocations().containsKey(node.nodeId())) {
      return true;
    }
    if (staticKind(node).isPresent() && hasMissingStaticFacts(node, decisions)) {
      return true;
    }
    if (isStringPlus(node) && !decisions.concatenations().containsKey(node.nodeId())) {
      return true;
    }
    return isCreation(node) && node.value(SyntaxValue.Invocation.class).semantics()
        .map(semantics -> semantics.qualifyingType().declarationKey().isEmpty()).orElse(true);
  }

  private static boolean hasMissingStaticFacts(SyntaxNode node, MethodDecisionIndex decisions) {
    var decision = decisions.staticAccesses().get(node.nodeId());
    return decision == null || node.requiredReference().symbolKey().isEmpty()
        || (decision.triggersInitialization() && decision.declaringType().declarationKey().isEmpty());
  }

  static Optional<StaticAccessKind> staticKind(SyntaxNode node) {
    if (node.value() instanceof SyntaxValue.FieldAccess access && !access.receiver().consumesReceiver()) {
      return switch (node.syntaxKind()) {
        case FIELD_ACCESS -> Optional.of(StaticAccessKind.FIELD_READ);
        case FIELD_LOCATION -> Optional.of(StaticAccessKind.FIELD_WRITE);
        default -> Optional.empty();
      };
    }
    if (node.syntaxKind() == SyntaxKind.INVOCATION && node.value() instanceof SyntaxValue.Invocation invocation
        && (invocation.form() == InvocationForm.STATIC_UNQUALIFIED || invocation.form() == InvocationForm.STATIC_TYPE
            || invocation.form() == InvocationForm.STATIC_EXPRESSION)) {
      return Optional.of(StaticAccessKind.METHOD_INVOKE);
    }
    return Optional.empty();
  }

  static boolean isInvocation(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION;
  }

  private static boolean isCreation(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.OBJECT_CREATION;
  }

  private static boolean isPlus(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.BINARY && node.value(SyntaxValue.Operator.class).operator() == SyntaxOperator.PLUS;
  }

  private static boolean isStringPlus(SyntaxNode node) {
    return isPlus(node) && (node.requiredChild(SyntaxRole.LEFT_OPERAND).resolvedType().map(MethodDecisionValidation::isString).orElse(false)
        || node.requiredChild(SyntaxRole.RIGHT_OPERAND).resolvedType().map(MethodDecisionValidation::isString).orElse(false));
  }

  private static boolean isString(JavaTypeIdentity type) {
    return type.category() == JavaTypeCategory.DECLARED && "java.lang.String".equals(type.erasedName());
  }

  private static SyntaxNode ownedNode(String methodKey, String decisionMethodKey, String nodeId, Map<String, SyntaxNode> nodes) {
    if (!methodKey.equals(decisionMethodKey) || !nodes.containsKey(nodeId)) {
      throw new IllegalArgumentException("Selected decision must belong to its registered method and body");
    }
    return nodes.get(nodeId);
  }

  private static <K, V> void putUnique(Map<K, V> values, K key, V value, String family) {
    if (values.putIfAbsent(key, value) != null) {
      throw new IllegalArgumentException("Duplicate " + family + " decision key");
    }
  }

  private static void requireEqual(Object expected, Object actual, String fact) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(fact + " contradicts retained syntax evidence");
    }
  }
}
