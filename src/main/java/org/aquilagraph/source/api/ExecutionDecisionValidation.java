package org.aquilagraph.source.api;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.ExecutionDecisionIndex.UseKey;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;

/** Checks supplied lowering facts against the admitted body and locally available source evidence. */
final class ExecutionDecisionValidation {

  private ExecutionDecisionValidation() {
  }

  static ExecutionDecisionIndex validate(String scopeKey, SyntaxNode root, ExecutionDecisionRegistration registration,
      Map<String, SyntaxNode> sourceNodes, List<AttributedBodyOccurrence> attributions, Map<String, DeclarationMemberOrder> memberOrders) {
    var nodes = new HashMap<String, SyntaxNode>();
    SourceExecutionTraversal.nodes(root).forEach(node -> nodes.put(node.nodeId(), node));
    var uses = validateUses(scopeKey, root, registration.uses(), nodes, sourceNodes);
    var constructors = validateEnumContexts(scopeKey, root, registration.constructors(), nodes, sourceNodes, attributions, memberOrders);
    var invocations = validateInvocations(scopeKey, registration.invocations(), nodes, uses, constructors);
    validateParameterTargets(nodes, sourceNodes, uses, invocations);
    var staticAccesses = validateStaticAccesses(scopeKey, registration.staticAccesses(), nodes, sourceNodes);
    var concatenations = validateConcatenations(scopeKey, registration.concatenations(), nodes);
    nodes.values().stream().filter(ExecutionDecisionValidation::isCreation).forEach(ExecutionDecisionValidation::validateCreation);
    nodes.values().stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_ACCESS)
        .forEach(ExecutionDecisionValidation::validateArray);
    return new ExecutionDecisionIndex(uses, invocations, staticAccesses, concatenations, constructors);
  }

  private static Map<UseKey, ChildUseEvidence> validateUses(String scopeKey, SyntaxNode root, List<ChildUseEvidence> decisions,
      Map<String, SyntaxNode> nodes, Map<String, SyntaxNode> sourceNodes) {
    var uses = new HashMap<UseKey, ChildUseEvidence>();
    var resultTargets = new HashMap<String, JavaTypeIdentity>();
    var returnTarget = sourceNodes.values().stream()
        .filter(
            node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration && declaration.declarationKey().equals(scopeKey))
        .flatMap(node -> node.children(SyntaxRole.RETURN_TYPE).stream()).findFirst().flatMap(SyntaxNode::resolvedType);
    collectExecutionTargets(root, Optional.empty(), returnTarget, resultTargets);
    for (var decision : decisions) {
      var consumer = ownedNode(scopeKey, decision.scopeKey(), decision.consumerNodeId(), nodes);
      var child = eligibleChild(consumer, decision);
      requireEqual(consumer.origin(), decision.conversion().origin().sourceRange(), "Conversion evidence source range");
      requireEqual(consumer.origin(), decision.referenceCheck().origin().sourceRange(), "Reference-check evidence source range");
      child.node().resolvedType().ifPresent(type -> requireEqual(type, decision.sourceType(), "Typed-use source type"));
      if (decision.conversion().knownValue().flatMap(ConversionEvidence::checkedReferenceTarget).isPresent()
          && consumer.syntaxKind() != SyntaxKind.TYPE_CAST) {
        throw new IllegalArgumentException("A checked-reference unboxing step requires a cast operand");
      }
      if (decision.referenceCheck().knownValue().isPresent() && consumer.syntaxKind() != SyntaxKind.TYPE_CAST) {
        throw new IllegalArgumentException("A reference-cast classification requires a cast operand");
      }
      if (consumer.syntaxKind() == SyntaxKind.TYPE_CAST && decision.role() == SyntaxRole.EXPRESSION
          && DecisionValueValidation.isReference(decision.selectedTarget())
          && (DecisionValueValidation.isReference(decision.sourceType()) || decision.sourceType().category() == JavaTypeCategory.NULL)
          && decision.referenceCheck().state() == EvidenceState.NOT_APPLICABLE) {
        throw new IllegalArgumentException("An applicable reference-cast classification cannot be not applicable");
      }
      validatePrimitiveContext(consumer, decision);
      expectedTarget(consumer, decision.role(), resultTargets)
          .ifPresent(target -> requireEqual(target, decision.selectedTarget(), "Typed-use selected target"));
      putUnique(uses, UseKey.from(decision.consumerNodeId(), decision.role(), decision.position()), decision, "typed-use");
    }
    return uses;
  }

  private static SyntaxChild eligibleChild(SyntaxNode consumer, ChildUseEvidence decision) {
    return consumer.children().stream().filter(child -> child.node().syntaxKind().requiresType())
        .filter(candidate -> candidate.role() == decision.role() && candidate.position() == decision.position()).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("A typed-use decision must select an eligible direct child"));
  }

  private static Optional<JavaTypeIdentity> expectedTarget(SyntaxNode consumer, SyntaxRole role,
      Map<String, JavaTypeIdentity> resultTargets) {
    return switch (consumer.syntaxKind()) {
      case FIELD_DECLARATION -> role == SyntaxRole.INITIALIZER
          ? Optional.of(consumer.value(SyntaxValue.VariableDeclaration.class).declaredType())
          : Optional.empty();
      case LOCAL_DECLARATION, TYPE_CAST, UNARY -> consumer.resolvedType();
      case CONDITIONAL_EXPRESSION -> role == SyntaxRole.CONDITION ? Optional.empty() : consumer.resolvedType();
      case ASSIGNMENT -> consumer.requiredChild(SyntaxRole.LOCATION).resolvedType();
      case LAMBDA_EXPRESSION -> consumer.value(SyntaxValue.Lambda.class).returnType() instanceof SyntaxType.Resolved resolved
          ? Optional.of(resolved.identity())
          : Optional.empty();
      case CASE -> role == SyntaxRole.BODY ? Optional.ofNullable(resultTargets.get(consumer.nodeId())) : Optional.empty();
      case RETURN, YIELD -> Optional.ofNullable(resultTargets.get(consumer.nodeId()));
      default -> Optional.empty();
    };
  }

  private static void validatePrimitiveContext(SyntaxNode consumer, ChildUseEvidence decision) {
    if (decision.role() == SyntaxRole.INDEX && decision.selectedTarget().category() != JavaTypeCategory.INT) {
      throw new IllegalArgumentException("An array index use must select int");
    }
    if ((decision.role() == SyntaxRole.CONDITION || decision.role() == SyntaxRole.GUARD
        || consumer.syntaxKind() == SyntaxKind.CONDITIONAL_AND || consumer.syntaxKind() == SyntaxKind.CONDITIONAL_OR)
        && decision.selectedTarget().category() != JavaTypeCategory.BOOLEAN) {
      throw new IllegalArgumentException("A condition use must select boolean");
    }
  }

  private static void collectExecutionTargets(SyntaxNode node, Optional<JavaTypeIdentity> enclosingSwitch,
      Optional<JavaTypeIdentity> enclosingReturn, Map<String, JavaTypeIdentity> targets) {
    var lambda = node.value() instanceof SyntaxValue.Lambda value ? Optional.of(value) : Optional.<SyntaxValue.Lambda>empty();
    var selectedReturn = lambda.map(SyntaxValue.Lambda::returnType)
        .map(type -> type instanceof SyntaxType.Resolved resolved ? Optional.of(resolved.identity()) : Optional.<JavaTypeIdentity>empty())
        .orElse(enclosingReturn);
    var selectedSwitch = node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION
        ? node.resolvedType()
        : lambda.isPresent() ? Optional.<JavaTypeIdentity>empty() : enclosingSwitch;
    if (node.syntaxKind() == SyntaxKind.CASE || node.syntaxKind() == SyntaxKind.YIELD) {
      selectedSwitch.ifPresent(type -> targets.put(node.nodeId(), type));
    }
    if (node.syntaxKind() == SyntaxKind.RETURN) {
      selectedReturn.ifPresent(type -> targets.put(node.nodeId(), type));
    }
    node.children().forEach(child -> collectExecutionTargets(child.node(), selectedSwitch, selectedReturn, targets));
  }

  private static Map<String, InvocationEvidence> validateInvocations(String scopeKey, List<InvocationEvidence> decisions,
      Map<String, SyntaxNode> nodes, Map<UseKey, ChildUseEvidence> uses, Map<String, ConstructorEvidence> constructors) {
    var invocations = new HashMap<String, InvocationEvidence>();
    for (var evidence : decisions) {
      var node = ownedNode(scopeKey, evidence.scopeKey(), evidence.consumerNodeId(), nodes);
      if (!isInvocation(node)) {
        throw new IllegalArgumentException("An invocation-arity decision requires an invocation node");
      }
      requireEqual(node.origin(), evidence.applicability().origin().sourceRange(), "Invocation applicability source range");
      putUnique(invocations, node.nodeId(), evidence, "invocation-applicability");
      if (evidence.applicability().knownValue().isEmpty()) {
        continue;
      }
      var decision = evidence.applicability().knownValue().orElseThrow();
      var argumentCount = node.children(SyntaxRole.ARGUMENT).size();
      var insertedSuperclass = node.origin().isEmpty()
          && node.value(SyntaxValue.Invocation.class).form() == InvocationForm.SUPER_CONSTRUCTOR;
      Optional.ofNullable(constructors.get(node.nodeId())).flatMap(value -> value.corroboration().knownValue()).ifPresent(context -> {
        if (insertedSuperclass && decision.formalCount() != context.superclassParameterTypes().size()) {
          throw new IllegalArgumentException("Inserted applicability contradicts the actual selected superclass parameters");
        }
      });
      if ((decision.mode() == InvocationApplicabilityMode.FIXED && !insertedSuperclass && argumentCount != decision.formalCount())
          || (decision.mode() == InvocationApplicabilityMode.VARIABLE && argumentCount < decision.formalCount() - 1)) {
        throw new IllegalArgumentException("Invocation applicability contradicts source argument count");
      }
      if (decision.mode() == InvocationApplicabilityMode.VARIABLE) {
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
    }
    return invocations;
  }

  private static void validateParameterTargets(Map<String, SyntaxNode> nodes, Map<String, SyntaxNode> sourceNodes,
      Map<UseKey, ChildUseEvidence> uses, Map<String, InvocationEvidence> invocations) {
    for (var use : uses.values()) {
      var call = Objects.requireNonNull(nodes.get(use.consumerNodeId()), "owned use consumer");
      if (use.role() != SyntaxRole.ARGUMENT || !isInvocation(call)) {
        continue;
      }
      var selected = call.semanticReference().flatMap(SemanticReference::symbolKey);
      var declaration = sourceNodes.values().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable
          && selected.equals(Optional.of(executable.declarationKey()))).findFirst();
      if (declaration.isEmpty()) {
        continue;
      }
      var parameters = sourceParameters(declaration.orElseThrow(), sourceNodes);
      if (parameters.isEmpty()) {
        continue;
      }
      var parameter = parameters.get(Math.min(use.position(), parameters.size() - 1));
      var typeNode = parameter.children(SyntaxRole.DECLARED_TYPE).stream().findFirst();
      if (!(typeNode.map(SyntaxNode::type).orElse(SyntaxType.NotApplicable.INSTANCE) instanceof SyntaxType.Resolved resolved)) {
        continue;
      }
      var variable = (parameter.value() instanceof SyntaxValue.ParameterDeclaration formal && formal.varArgs())
          || (parameter.value() instanceof SyntaxValue.RecordComponentDeclaration component && component.varArgs());
      if (!variable && use.position() >= parameters.size()) {
        continue;
      }
      var mode = Optional.ofNullable(invocations.get(call.nodeId())).flatMap(value -> value.applicability().knownValue())
          .map(InvocationApplicability::mode);
      if (variable && call.children(SyntaxRole.ARGUMENT).size() > parameters.size()) {
        mode = Optional.of(InvocationApplicabilityMode.VARIABLE);
      }
      var exact = unchangedParameterTypes(declaration.orElseThrow(), sourceNodes);
      var targets = parameterTargets(resolved, variable, mode, exactIdentity(resolved, exact),
          componentType(typeNode.orElseThrow()).flatMap(component -> exactIdentity(component, exact)));
      if (!targets.isEmpty() && targets.stream().noneMatch(target -> target.matches(use.selectedTarget()))) {
        throw new IllegalArgumentException("Argument parameter target contradicts the selected source declaration");
      }
    }
  }

  private static List<SyntaxNode> sourceParameters(SyntaxNode node, Map<String, SyntaxNode> sourceNodes) {
    var declaration = node.value(SyntaxValue.ExecutableDeclaration.class);
    if (declaration.kind() != ExecutableKind.COMPACT_CONSTRUCTOR) {
      return node.children(SyntaxRole.PARAMETER);
    }
    return sourceNodes.values().stream()
        .filter(candidate -> candidate.value() instanceof SyntaxValue.TypeDeclaration type
            && type.declarationKey().equals(declaration.ownerKey()))
        .findFirst().map(owner -> owner.children(SyntaxRole.RECORD_COMPONENT)).orElse(List.of());
  }

  private static List<ParameterTarget> parameterTargets(SyntaxType.Resolved type, boolean variable,
      Optional<InvocationApplicabilityMode> mode, Optional<JavaTypeIdentity> exact, Optional<JavaTypeIdentity> exactComponent) {
    if (type.identity().category() == JavaTypeCategory.TYPE_VARIABLE || (type.identity().category() == JavaTypeCategory.ARRAY
        && !type.structure().map(ExecutionDecisionValidation::stableErasure).orElse(false))) {
      return List.of();
    }
    var fixed = new ParameterTarget(type.identity().category(), type.identity().erasedName(), exact);
    if (!variable || mode.equals(Optional.of(InvocationApplicabilityMode.FIXED))) {
      return List.of(fixed);
    }
    if (!(type.structure().orElse(null) instanceof JavaTypeStructure.Array array) || !type.identity().erasedName().endsWith("[]")) {
      return List.of();
    }
    var category = switch (array.component()) {
      case JavaTypeStructure.Primitive primitive -> primitive.kind();
      case JavaTypeStructure.Declared _ -> JavaTypeCategory.DECLARED;
      case JavaTypeStructure.LocalDeclared _ -> JavaTypeCategory.DECLARED;
      case JavaTypeStructure.Array _ -> JavaTypeCategory.ARRAY;
      default -> JavaTypeCategory.TYPE_VARIABLE;
    };
    var component = new ParameterTarget(category, type.identity().erasedName().substring(0, type.identity().erasedName().length() - 2),
        exactComponent);
    return mode.equals(Optional.of(InvocationApplicabilityMode.VARIABLE)) ? List.of(component) : List.of(fixed, component);
  }

  private static boolean stableErasure(JavaTypeStructure type) {
    return switch (type) {
      case JavaTypeStructure.Variable _ -> false;
      case JavaTypeStructure.Captured _ -> false;
      case JavaTypeStructure.CaptureReference _ -> false;
      case JavaTypeStructure.Array array -> stableErasure(array.component());
      default -> true;
    };
  }

  private static boolean unchangedParameterTypes(SyntaxNode node, Map<String, SyntaxNode> sourceNodes) {
    var declaration = node.value(SyntaxValue.ExecutableDeclaration.class);
    if (declaration.shape().typeParameters() != 0) {
      return false;
    }
    if (declaration.effectiveModifiers().contains(JavaDeclarationModifier.STATIC)) {
      return true;
    }
    var owners = sourceNodes.values().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.TypeDeclaration)
        .filter(candidate -> candidate.descendantsAndSelf().contains(node)).toList();
    return owners.stream().filter(owner -> owner.value(SyntaxValue.TypeDeclaration.class).shape().typeParameters() != 0)
        .noneMatch(generic -> owners.stream()
            .noneMatch(boundary -> !boundary.equals(generic)
                && boundary.value(SyntaxValue.TypeDeclaration.class).effectiveModifiers().contains(JavaTypeModifier.STATIC)
                && generic.descendantsAndSelf().contains(boundary)));
  }

  private static Optional<JavaTypeIdentity> exactIdentity(SyntaxType.Resolved type, boolean unchanged) {
    return unchanged
        && type.structure().map(ExecutionDecisionValidation::closedType).orElse(DecisionValueValidation.isPrimitive(type.identity()))
            ? Optional.of(type.identity())
            : Optional.empty();
  }

  private static boolean closedType(JavaTypeStructure type) {
    return switch (type) {
      case JavaTypeStructure.Primitive _ -> true;
      case JavaTypeStructure.Declared declared -> declared.enclosingType().map(ExecutionDecisionValidation::closedType).orElse(true)
          && declared.arguments().stream().allMatch(ExecutionDecisionValidation::closedType);
      case JavaTypeStructure.LocalDeclared local -> local.enclosingType().map(ExecutionDecisionValidation::closedType).orElse(true)
          && local.arguments().stream().allMatch(ExecutionDecisionValidation::closedType);
      case JavaTypeStructure.Array array -> closedType(array.component());
      case JavaTypeStructure.Wildcard wildcard -> wildcard.bound().map(ExecutionDecisionValidation::closedType).orElse(true);
      default -> false;
    };
  }

  private static Optional<SyntaxType.Resolved> componentType(SyntaxNode node) {
    var component = node.children(SyntaxRole.COMPONENT_TYPE).stream().findFirst();
    if (component.isPresent()) {
      return component.map(SyntaxNode::type).filter(SyntaxType.Resolved.class::isInstance).map(SyntaxType.Resolved.class::cast);
    }
    return node.children(SyntaxRole.UNDERLYING_TYPE).stream().findFirst().flatMap(ExecutionDecisionValidation::componentType);
  }

  private record ParameterTarget(JavaTypeCategory category, String erasedName, Optional<JavaTypeIdentity> exactIdentity) {
    boolean matches(JavaTypeIdentity type) {
      return category == type.category() && erasedName.equals(type.erasedName()) && exactIdentity.map(type::equals).orElse(true);
    }
  }

  private static Map<String, ConstructorEvidence> validateEnumContexts(String scopeKey, SyntaxNode root,
      List<ConstructorEvidence> decisions, Map<String, SyntaxNode> nodes, Map<String, SyntaxNode> sourceNodes,
      List<AttributedBodyOccurrence> attributions, Map<String, DeclarationMemberOrder> memberOrders) {
    var contexts = new HashMap<String, ConstructorEvidence>();
    var directInvocations = new IdentityHashMap<SyntaxNode, Boolean>();
    root.children(SyntaxRole.STATEMENT).stream().filter(statement -> statement.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT)
        .forEach(statement -> directInvocations.put(statement.requiredChild(SyntaxRole.EXPRESSION), Boolean.TRUE));
    for (var decision : decisions) {
      var node = ownedNode(scopeKey, decision.scopeKey(), decision.invocationNodeId(), nodes);
      requireEqual(node.origin(), decision.corroboration().origin().sourceRange(), "Constructor corroboration source range");
      memberOrders.values().stream().filter(order -> order.members().stream().anyMatch(member -> member.declarationKey().equals(scopeKey)))
          .forEach(order -> {
            requireEqual(root.sourceFileKey(), order.sourceFileKey(), "Constructor containing source");
            requireEqual(decision.ownerTypeKey(), order.ownerTypeKey(), "Constructor containing declaration");
          });
      sourceNodes.values().stream().filter(
          value -> value.value() instanceof SyntaxValue.ExecutableDeclaration declaration && declaration.declarationKey().equals(scopeKey))
          .forEach(value -> {
            var declaration = value.value(SyntaxValue.ExecutableDeclaration.class);
            requireEqual(decision.ownerTypeKey(), declaration.ownerKey(), "Constructor source owner");
            if (declaration.kind() != ExecutableKind.CONSTRUCTOR && declaration.kind() != ExecutableKind.COMPACT_CONSTRUCTOR) {
              throw new IllegalArgumentException("Constructor evidence requires a constructor declaration");
            }
          });
      if (node.equals(root) && node.syntaxKind() == SyntaxKind.BLOCK) {
        validateInsertedBody(decision, root, attributions);
        putUnique(contexts, node.nodeId(), decision, "constructor");
        continue;
      }
      if (!isInvocation(node) || !directInvocations.containsKey(node)) {
        throw new IllegalArgumentException("Constructor facts require an actual source owner and direct constructor invocation");
      }
      var invocation = node.value(SyntaxValue.Invocation.class);
      var semantics = selectedEnumInvocationSemantics(invocation);
      var reference = node.requiredReference();
      if (semantics.mode() != InvocationMode.CONSTRUCTOR || reference.referenceKind() != ReferenceKind.CONSTRUCTOR
          || reference.status() != ResolutionStatus.RESOLVED
          || !reference.symbolKey().equals(Optional.of(decision.selectedConstructorKey()))) {
        throw new IllegalArgumentException("Enum context contradicts the selected constructor reference");
      }
      requireEqual(invocation.form(), decision.form(), "Constructor delegation form");
      decision.corroboration().knownValue().flatMap(ConstructorContext::insertedInvocation).ifPresent(inserted -> {
        requireEqual(node.sourceFileKey(), inserted.sourceFileKey(), "Inserted invocation source");
        requireEqual(node.structuralPath(), inserted.compilerPath(), "Inserted invocation attribution address");
        requireEqual(reference, inserted.reference(), "Inserted invocation reference");
        requireEqual(semantics, inserted.semantics(), "Inserted invocation semantics");
      });
      if (decision.form() == InvocationForm.SUPER_CONSTRUCTOR) {
        if (invocation.form() != InvocationForm.SUPER_CONSTRUCTOR || node.origin().isPresent()
            || !node.children(SyntaxRole.ARGUMENT).isEmpty()) {
          throw new IllegalArgumentException("Superclass context requires an inserted zero-source-argument call");
        }
      } else if (invocation.form() != InvocationForm.THIS_CONSTRUCTOR
          || !semantics.qualifyingType().declarationKey().equals(Optional.of(decision.ownerTypeKey()))) {
        throw new IllegalArgumentException("Enum delegation context requires a selected constructor in the same enum");
      }
      putUnique(contexts, node.nodeId(), decision, "enum-constructor-context");
    }
    return contexts;
  }

  private static void validateInsertedBody(ConstructorEvidence evidence, SyntaxNode root, List<AttributedBodyOccurrence> attributions) {
    if (evidence.form() != InvocationForm.SUPER_CONSTRUCTOR) {
      throw new IllegalArgumentException("Body-scoped constructor evidence requires an inserted superclass invocation");
    }
    evidence.corroboration().knownValue().ifPresent(context -> {
      var inserted = context.insertedInvocation()
          .orElseThrow(() -> new IllegalArgumentException("Known inserted selection requires its provenance"));
      var address = attributions.stream().filter(value -> value.nodeKey().equals(root.nodeId())).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Inserted selection requires the actual copied body address"));
      requireEqual(root.sourceFileKey(), inserted.sourceFileKey(), "Inserted invocation source");
      requireEqual(address.compilerPath().child(SyntaxRole.STATEMENT, 0).child(SyntaxRole.EXPRESSION, 0), inserted.compilerPath(),
          "Inserted invocation attribution address");
      requireEqual(evidence.scopeKey(), inserted.applicability().scopeKey(), "Inserted applicability method");
      requireEqual(root.nodeId(), inserted.applicability().consumerNodeId(), "Inserted applicability creating body");
      requireEqual(root.origin(), inserted.applicability().applicability().origin().sourceRange(), "Inserted applicability creating range");
    });
  }

  private static InvocationSemantics selectedEnumInvocationSemantics(SyntaxValue.Invocation invocation) {
    return invocation.semantics().orElseThrow(() -> new IllegalArgumentException("Enum context requires selected semantics"));
  }

  private static Map<String, StaticMemberEvidence> validateStaticAccesses(String scopeKey, List<StaticMemberEvidence> decisions,
      Map<String, SyntaxNode> nodes, Map<String, SyntaxNode> sourceNodes) {
    var accesses = new HashMap<String, StaticMemberEvidence>();
    for (var decision : decisions) {
      var node = ownedNode(scopeKey, decision.scopeKey(), decision.accessNodeId(), nodes);
      requireEqual(node.origin(), decision.constantVariable().origin().sourceRange(), "Static constant evidence source range");
      requireEqual(node.origin(), decision.declaringOwner().origin().sourceRange(), "Static owner evidence source range");
      if (!staticKind(node, nodes).equals(Optional.of(decision.kind()))) {
        throw new IllegalArgumentException("Static-access decision contradicts the access kind or receiver form");
      }
      var reference = node.requiredReference();
      var expectedKind = decision.kind() == StaticMemberKind.METHOD_INVOKE ? ReferenceKind.METHOD : ReferenceKind.FIELD;
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

  private static void validateSourceOwner(StaticMemberEvidence decision, ReferenceKind referenceKind, Map<String, SyntaxNode> sourceNodes) {
    for (var node : sourceNodes.values()) {
      Optional<String> owner = Optional.empty();
      if (node.value() instanceof SyntaxValue.VariableDeclaration declaration
          && declaration.declarationKey().equals(decision.selectedMemberKey())) {
        validateSourceField(decision, referenceKind, declaration);
        owner = Optional.of(declaration.ownerKey());
      } else if (node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
          && declaration.declarationKey().equals(decision.selectedMemberKey())) {
        if (decision.kind() != StaticMemberKind.METHOD_INVOKE || !declaration.effectiveModifiers().contains(JavaDeclarationModifier.STATIC)
            || declaration.kind() != SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD) {
          throw new IllegalArgumentException("Static-access decision contradicts its source executable declaration");
        }
        owner = Optional.of(declaration.ownerKey());
      }
      var declaringKey = decision.declaringOwner().knownValue().flatMap(JavaTypeReference::declarationKey);
      if (owner.isPresent() && declaringKey.isPresent()) {
        requireEqual(owner.orElseThrow(), declaringKey.orElseThrow(), "Static-access declaring owner");
      }
    }
  }

  private static void validateSourceField(StaticMemberEvidence decision, ReferenceKind referenceKind,
      SyntaxValue.VariableDeclaration declaration) {
    var expectedKind = declaration.kind() == SyntaxValue.VariableDeclaration.Kind.FIELD ? ReferenceKind.FIELD : ReferenceKind.ENUM_CONSTANT;
    if (decision.kind() == StaticMemberKind.METHOD_INVOKE || referenceKind != expectedKind
        || !declaration.effectiveModifiers().contains(JavaDeclarationModifier.STATIC)) {
      throw new IllegalArgumentException("Static-access decision contradicts its source field declaration");
    }
    if (decision.constantVariable().knownValue().orElse(false)
        && (!declaration.effectiveModifiers().contains(JavaDeclarationModifier.FINAL) || !declaration.initializer()
            || !(DecisionValueValidation.isPrimitive(declaration.declaredType()) || isString(declaration.declaredType())))) {
      throw new IllegalArgumentException("Constant-variable read contradicts its source field declaration");
    }
  }

  private static Map<String, StringExpressionEvidence> validateConcatenations(String scopeKey, List<StringExpressionEvidence> decisions,
      Map<String, SyntaxNode> nodes) {
    var concatenations = new HashMap<String, StringExpressionEvidence>();
    for (var decision : decisions) {
      var node = ownedNode(scopeKey, decision.scopeKey(), decision.binaryNodeId(), nodes);
      if (!isPlus(node)) {
        throw new IllegalArgumentException("String concatenation requires a binary PLUS node");
      }
      node.resolvedType().ifPresent(type -> requireEqual(type, decision.stringType(), "String concatenation result"));
      requireEqual(node.origin(), decision.compileTimeConstant().origin().sourceRange(), "String constant evidence source range");
      requireEqual(node.origin(), decision.leftIsString().origin().sourceRange(), "String left evidence source range");
      requireEqual(node.origin(), decision.rightIsString().origin().sourceRange(), "String right evidence source range");
      var left = node.requiredChild(SyntaxRole.LEFT_OPERAND).resolvedType();
      var right = node.requiredChild(SyntaxRole.RIGHT_OPERAND).resolvedType();
      if (left.isPresent() && right.isPresent()) {
        var leftString = isString(left.orElseThrow());
        var rightString = isString(right.orElseThrow());
        if (!leftString && !rightString) {
          throw new IllegalArgumentException("String concatenation requires a String operand");
        }
      }
      left.ifPresent(type -> decision.leftIsString().knownValue()
          .ifPresent(value -> requireEqual(isString(type), value, "String left operand classification")));
      right.ifPresent(type -> decision.rightIsString().knownValue()
          .ifPresent(value -> requireEqual(isString(type), value, "String right operand classification")));
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

  private static Optional<StaticMemberKind> staticKind(SyntaxNode node, Map<String, SyntaxNode> nodes) {
    if (node.syntaxKind() == SyntaxKind.FIELD_ACCESS && node.value() instanceof SyntaxValue.FieldAccess access
        && access.receiver().isStatic()) {
      return Optional.of(isAssignmentTarget(node, nodes) ? StaticMemberKind.FIELD_WRITE : StaticMemberKind.FIELD_READ);
    }
    if (node.syntaxKind() == SyntaxKind.INVOCATION && node.value() instanceof SyntaxValue.Invocation invocation
        && (invocation.form() == InvocationForm.STATIC_UNQUALIFIED || invocation.form() == InvocationForm.STATIC_TYPE
            || invocation.form() == InvocationForm.STATIC_EXPRESSION)) {
      return Optional.of(StaticMemberKind.METHOD_INVOKE);
    }
    return Optional.empty();
  }

  private static boolean isAssignmentTarget(SyntaxNode node, Map<String, SyntaxNode> nodes) {
    return nodes.values().stream().filter(candidate -> candidate.syntaxKind() == SyntaxKind.ASSIGNMENT)
        .anyMatch(candidate -> assignmentTarget(candidate.requiredChild(SyntaxRole.LOCATION)).equals(node));
  }

  private static SyntaxNode assignmentTarget(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.PARENTHESIZED ? assignmentTarget(node.requiredChild(SyntaxRole.EXPRESSION)) : node;
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

  private static boolean isString(JavaTypeIdentity type) {
    return type.category() == JavaTypeCategory.DECLARED && "java.lang.String".equals(type.erasedName());
  }

  private static SyntaxNode ownedNode(String scopeKey, String decisionScopeKey, String nodeId, Map<String, SyntaxNode> nodes) {
    if (!scopeKey.equals(decisionScopeKey) || !nodes.containsKey(nodeId)) {
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
