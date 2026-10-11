package org.aquilagraph.frontend;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.frontend.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.ConstructorContext;
import org.aquilagraph.source.api.CompilerInsertedInvocation;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StaticMemberKind;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.ChildUseEvidence;

/** Copies selected use facts while exact compiler paths and body instances remain live. */
final class JavaBodyDecisionProducer {
  private final SemanticResolver semantics;
  private final JavaInvocationApplicability applicability;
  private final JavaConstantExpression constants;
  private final JavaConstructorEvidenceProducer constructors;

  JavaBodyDecisionProducer(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    applicability = new JavaInvocationApplicability(semantics);
    constants = new JavaConstantExpression(semantics);
    constructors = new JavaConstructorEvidenceProducer(semantics);
  }

  JavaBodyDecisions produce(String scopeKey, SyntaxNode root, List<NodeProjection> projections, List<AnalysisDiagnostic> diagnostics) {
    var bindings = corroborate(root, projections);
    var uses = new ArrayList<ChildUseEvidence>();
    var invocations = new ArrayList<InvocationApplicability>();
    var staticAccesses = new ArrayList<StaticMemberEvidence>();
    var concatenations = new ArrayList<StringExpressionEvidence>();
    var constructorContexts = new ArrayList<ConstructorEvidence>();
    for (var consumer : root.descendantsAndSelf()) {
      var binding = Objects.requireNonNull(bindings.get(consumer));
      if (consumer.syntaxKind() == SyntaxKind.INVOCATION || consumer.syntaxKind() == SyntaxKind.OBJECT_CREATION) {
        var context = constructors.produce(scopeKey, root, binding);
        context.ifPresent(constructorContexts::add);
        invocation(scopeKey, binding, bindings, uses, invocations);
      } else {
        for (var child : consumer.children()) {
          if (child.node().syntaxKind().requiresType()) {
            var childBinding = Objects.requireNonNull(bindings.get(child.node()));
            target(binding, child, bindings).flatMap(selected -> conversion(scopeKey, consumer, child, childBinding.path(), selected,
                consumer.syntaxKind() == SyntaxKind.TYPE_CAST, assignmentContext(consumer))).ifPresent(uses::add);
          }
        }
      }
      staticAccess(scopeKey, binding).ifPresent(staticAccesses::add);
      concatenation(scopeKey, binding, diagnostics).ifPresent(concatenations::add);
    }
    var selectedInvocations = new HashMap<String, InvocationApplicability>();
    invocations.forEach(value -> selectedInvocations.put(value.consumerNodeId(), value));
    var retainedInvocations = root.descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .map(
            node -> Optional.ofNullable(selectedInvocations.get(node.nodeId())).map(value -> InvocationEvidence.known(value, node.origin()))
                .orElseGet(() -> new InvocationEvidence(
                    EvidenceValue.missing(new EvidenceOrigin(scopeKey, node.nodeId(), InvocationEvidence.SLOT, node.origin()),
                        "Compiler applicability could not be established"))))
        .toList();
    var retainedConstructors = constructorContexts.stream().map(value -> {
      var evidence = value.corroboration();
      var known = evidence.knownValue()
          .map(
              context -> new ConstructorContext(context.superclassParameterTypes(),
                  context.insertedInvocation().map(inserted -> new CompilerInsertedInvocation(inserted.sourceFileKey(),
                      inserted.compilerPath(), inserted.reference(), inserted.semantics(), retainedInvocations.stream()
                          .filter(invocation -> invocation.consumerNodeId().equals(value.invocationNodeId())).findFirst().orElseThrow()))));
      return new ConstructorEvidence(value.scopeKey(), value.invocationNodeId(), value.ownerTypeKey(), value.selectedConstructorKey(),
          value.form(), new EvidenceValue<>(evidence.state(), known, evidence.origin(), evidence.explanation(), evidence.diagnostic()));
    }).toList();
    return new JavaBodyDecisions(uses, retainedInvocations, staticAccesses, concatenations, retainedConstructors);
  }

  Map<SyntaxNode, NodeProjection> corroborate(SyntaxNode root, List<NodeProjection> projections) {
    var bindings = new IdentityHashMap<SyntaxNode, NodeProjection>();
    for (var projection : projections) {
      if (bindings.put(projection.node(), projection) != null) {
        throw new IllegalArgumentException("One produced body instance cannot have two compiler projections");
      }
    }
    var nodes = root.descendantsAndSelf();
    if (nodes.size() != bindings.size()) {
      throw new IllegalArgumentException("The final registered body must retain exactly its produced compiler projection");
    }
    for (var node : nodes) {
      var binding = bindings.get(node);
      if (binding == null || !binding.path().getLeaf().getKind().name().equals(node.sourceKind().name())
          || !semantics.type(binding.path()).equals(node.type()) || binding.children().size() != node.children().size()) {
        throw new IllegalArgumentException("A final body node requires its exact attributed source projection");
      }
      for (var index = 0; index < node.children().size(); index++) {
        var child = node.children().get(index);
        var mapping = binding.children().get(index);
        var childBinding = bindings.get(child.node());
        if (childBinding == null || mapping.role() != child.role() || mapping.position() != child.position()
            || !JavaProjectionIdentity.sameInstance(mapping.treePath(), childBinding.path())
            || !JavaProjectionIdentity.sameInstance(mapping.tree(), childBinding.path().getLeaf())) {
          throw new IllegalArgumentException("A selected child use requires its exact direct child and compiler path");
        }
      }
    }
    return bindings;
  }

  Optional<ChildUseEvidence> fieldInitializer(String scopeKey, SyntaxNode field, TreePath fieldPath) {
    var initializer = field.optionalChild(SyntaxRole.INITIALIZER);
    if (initializer.isEmpty() || !(fieldPath.getLeaf() instanceof VariableTree variable) || variable.getInitializer() == null) {
      return Optional.empty();
    }
    return semantics.attributedMirror(fieldPath)
        .flatMap(target -> conversion(scopeKey, field, new SyntaxChild(SyntaxRole.INITIALIZER, 0, initializer.orElseThrow()),
            new TreePath(fieldPath, variable.getInitializer()), target, false, true));
  }

  private void invocation(String scopeKey, NodeProjection consumer, Map<SyntaxNode, NodeProjection> bindings, List<ChildUseEvidence> uses,
      List<InvocationApplicability> invocations) {
    if (!(semantics.element(consumer.path()) instanceof ExecutableElement executable) || !referenceMatches(consumer.node(), executable)) {
      return;
    }
    if (consumer.path().getLeaf() instanceof NewClassTree && !executable.getTypeParameters().isEmpty()) {
      constructorInvocation(scopeKey, consumer, executable, uses, invocations);
      return;
    }
    var selectedType = selectedExecutable(consumer.path(), executable);
    if (selectedType.isEmpty()) {
      return;
    }
    var arguments = new ArrayList<TypeMirror>();
    for (var child : consumer.node().children()) {
      if (child.role() == SyntaxRole.ARGUMENT) {
        var mirror = semantics.attributedMirror(Objects.requireNonNull(bindings.get(child.node())).path());
        if (mirror.isEmpty()) {
          return;
        }
        arguments.add(mirror.orElseThrow());
      }
    }
    var selection = applicability.select(selectedType.orElseThrow(), executable.isVarArgs(), arguments);
    if (selection.isEmpty()) {
      return;
    }
    var selected = selection.orElseThrow();
    var array = selected.variableArray();
    invocations.add(new InvocationApplicability(scopeKey, consumer.node().nodeId(), selected.mode(), selected.formalCount(),
        array.map(semantics::typeIdentity), array.map(value -> semantics.typeIdentity(semantics.erasedType(value))),
        array.map(value -> semantics.typeIdentity(value.getComponentType()))));
    for (var child : consumer.node().children()) {
      if (child.role() == SyntaxRole.ARGUMENT) {
        conversion(scopeKey, consumer.node(), child, Objects.requireNonNull(bindings.get(child.node())).path(),
            selected.argumentTargets().get(child.position()), false, false).ifPresent(uses::add);
      }
    }
  }

  private void constructorInvocation(String scopeKey, NodeProjection consumer, ExecutableElement executable, List<ChildUseEvidence> uses,
      List<InvocationApplicability> invocations) {
    var proof = semantics.selectedConstructor(consumer.path(), executable);
    if (proof.isEmpty()) {
      return;
    }
    var facts = proof.orElseThrow();
    var children = consumer.node().children(SyntaxRole.ARGUMENT);
    if (children.size() != facts.arguments().size()) {
      return;
    }
    for (var index = 0; index < children.size(); index++) {
      if (!children.get(index).resolvedType().equals(Optional.of(facts.arguments().get(index).sourceType()))) {
        return;
      }
    }
    invocations.add(new InvocationApplicability(scopeKey, consumer.node().nodeId(), facts.mode(), facts.formalCount(),
        facts.varargArrayType(), facts.erasedArrayType(), facts.elementType()));
    for (var index = 0; index < children.size(); index++) {
      var argument = facts.arguments().get(index);
      uses.add(ChildUseEvidence.known(new ConversionEvidence(scopeKey, consumer.node().nodeId(), SyntaxRole.ARGUMENT, index,
          argument.sourceType(), argument.selectedTarget(), Optional.empty(), argument.unboxedType(), argument.effectivePrimitiveType(),
          argument.preBoxPrimitiveType(), argument.boxedType()), consumer.node().syntaxKind(), consumer.node().origin()));
    }
  }

  private Optional<ExecutableType> selectedExecutable(TreePath path, ExecutableElement executable) {
    if (path.getLeaf() instanceof MethodInvocationTree invocation) {
      var mirror = semantics.attributedMirror(new TreePath(path, invocation.getMethodSelect()));
      return mirror.filter(ExecutableType.class::isInstance).map(ExecutableType.class::cast);
    }
    if (path.getLeaf() instanceof NewClassTree) {
      var owner = semantics.attributedMirror(path);
      if (owner.orElse(null) instanceof DeclaredType declared) {
        var member = semantics.memberType(declared, executable);
        return member instanceof ExecutableType selected ? Optional.of(selected) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<TypeMirror> target(NodeProjection consumer, SyntaxChild child, Map<SyntaxNode, NodeProjection> bindings) {
    var node = consumer.node();
    if (child.role() == SyntaxRole.CONDITION || child.role() == SyntaxRole.GUARD || node.syntaxKind() == SyntaxKind.CONDITIONAL_AND
        || node.syntaxKind() == SyntaxKind.CONDITIONAL_OR) {
      return Optional.of(semantics.primitiveType(TypeKind.BOOLEAN));
    }
    if (child.role() == SyntaxRole.INDEX) {
      return Optional.of(semantics.primitiveType(TypeKind.INT));
    }
    return switch (node.syntaxKind()) {
      case LAMBDA_EXPRESSION -> child.role() == SyntaxRole.BODY ? semantics.functionalReturnType(consumer.path()) : Optional.empty();
      case LOCAL_DECLARATION -> child.role() == SyntaxRole.INITIALIZER ? semantics.attributedMirror(consumer.path()) : Optional.empty();
      case TYPE_CAST -> child.role() == SyntaxRole.EXPRESSION ? semantics.attributedMirror(consumer.path()) : Optional.empty();
      case CONDITIONAL_EXPRESSION -> child.role() == SyntaxRole.TRUE_EXPRESSION || child.role() == SyntaxRole.FALSE_EXPRESSION
          ? semantics.attributedMirror(consumer.path())
          : Optional.empty();
      case UNARY -> child.role() == SyntaxRole.OPERAND ? semantics.attributedMirror(consumer.path()) : Optional.empty();
      case ASSIGNMENT -> child.role() == SyntaxRole.VALUE
          ? semantics.attributedMirror(Objects.requireNonNull(bindings.get(node.requiredChild(SyntaxRole.LOCATION))).path())
          : Optional.empty();
      case RETURN -> child.role() == SyntaxRole.EXPRESSION ? enclosingReturn(consumer.path()) : Optional.empty();
      case CASE -> child.role() == SyntaxRole.BODY ? enclosingSwitch(consumer.path()) : Optional.empty();
      case YIELD -> child.role() == SyntaxRole.VALUE ? enclosingSwitch(consumer.path()) : Optional.empty();
      case SWITCH_EXPRESSION,
          SWITCH_STATEMENT ->
        child.role() == SyntaxRole.SELECTOR
            ? semantics.attributedMirror(Objects.requireNonNull(bindings.get(child.node())).path())
                .flatMap(mirror -> primitive(mirror)
                    .<TypeMirror>map(type -> type.getKind() == TypeKind.BOOLEAN ? type : semantics.primitiveType(TypeKind.INT))
                    .or(() -> Optional.of(mirror)))
            : Optional.empty();
      case BINARY -> child.role() == SyntaxRole.LEFT_OPERAND || child.role() == SyntaxRole.RIGHT_OPERAND
          ? binaryTarget(consumer, child, bindings)
          : Optional.empty();
      default -> Optional.empty();
    };
  }

  private Optional<TypeMirror> binaryTarget(NodeProjection consumer, SyntaxChild child, Map<SyntaxNode, NodeProjection> bindings) {
    if (!(consumer.path().getLeaf() instanceof BinaryTree binary)) {
      return Optional.empty();
    }
    var left = semantics
        .attributedMirror(Objects.requireNonNull(bindings.get(consumer.node().requiredChild(SyntaxRole.LEFT_OPERAND))).path());
    var right = semantics
        .attributedMirror(Objects.requireNonNull(bindings.get(consumer.node().requiredChild(SyntaxRole.RIGHT_OPERAND))).path());
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.empty();
    }
    var first = left.orElseThrow();
    var second = right.orElseThrow();
    var childType = child.role() == SyntaxRole.LEFT_OPERAND ? first : second;
    if (isString(consumer.node()) || ((binary.getKind() == Tree.Kind.EQUAL_TO || binary.getKind() == Tree.Kind.NOT_EQUAL_TO)
        && !first.getKind().isPrimitive() && !second.getKind().isPrimitive())) {
      return Optional.of(childType);
    }
    if (binary.getKind() == Tree.Kind.LEFT_SHIFT || binary.getKind() == Tree.Kind.RIGHT_SHIFT
        || binary.getKind() == Tree.Kind.UNSIGNED_RIGHT_SHIFT) {
      return primitive(childType).map(type -> type.getKind() == TypeKind.LONG ? type : semantics.primitiveType(TypeKind.INT));
    }
    var firstPrimitive = primitive(first);
    var secondPrimitive = primitive(second);
    if (firstPrimitive.isEmpty() || secondPrimitive.isEmpty()) {
      return Optional.empty();
    }
    var firstKind = firstPrimitive.orElseThrow().getKind();
    var secondKind = secondPrimitive.orElseThrow().getKind();
    if (firstKind == TypeKind.BOOLEAN || secondKind == TypeKind.BOOLEAN) {
      return firstKind == secondKind ? Optional.of(semantics.primitiveType(TypeKind.BOOLEAN)) : Optional.empty();
    }
    var promoted = firstKind == TypeKind.DOUBLE || secondKind == TypeKind.DOUBLE
        ? TypeKind.DOUBLE
        : firstKind == TypeKind.FLOAT || secondKind == TypeKind.FLOAT
            ? TypeKind.FLOAT
            : firstKind == TypeKind.LONG || secondKind == TypeKind.LONG ? TypeKind.LONG : TypeKind.INT;
    return Optional.of(semantics.primitiveType(promoted));
  }

  private Optional<PrimitiveType> primitive(TypeMirror type) {
    return type instanceof PrimitiveType primitive ? Optional.of(primitive) : semantics.unboxedType(type);
  }

  private Optional<TypeMirror> enclosingReturn(TreePath path) {
    for (var cursor = path.getParentPath(); cursor != null; cursor = cursor.getParentPath()) {
      if (cursor.getLeaf() instanceof LambdaExpressionTree) {
        return semantics.functionalReturnType(cursor);
      }
      if (cursor.getLeaf() instanceof MethodTree && semantics.element(cursor) instanceof ExecutableElement executable) {
        return Optional.of(executable.getReturnType());
      }
    }
    return Optional.empty();
  }

  private Optional<TypeMirror> enclosingSwitch(TreePath path) {
    for (var cursor = path.getParentPath(); cursor != null; cursor = cursor.getParentPath()) {
      if (cursor.getLeaf() instanceof SwitchExpressionTree) {
        return semantics.attributedMirror(cursor);
      }
    }
    return Optional.empty();
  }

  private Optional<ChildUseEvidence> conversion(String scopeKey, SyntaxNode consumer, SyntaxChild child, TreePath childPath,
      TypeMirror target, boolean cast, boolean assignment) {
    if (child.node().resolvedType().isEmpty() || !valueType(target)) {
      return Optional.empty();
    }
    var sourceType = child.node().resolvedType().orElseThrow();
    var targetType = semantics.typeIdentity(target);
    var conversionOrigin = new EvidenceOrigin(scopeKey, consumer.nodeId(),
        "child-use.conversion/" + child.role().stableId() + "/" + child.position(), consumer.origin());
    var checkOrigin = new EvidenceOrigin(scopeKey, consumer.nodeId(),
        "child-use.reference-check/" + child.role().stableId() + "/" + child.position(), consumer.origin());
    var classification = classification(scopeKey, consumer, child, childPath, target, cast, assignment)
        .map(value -> EvidenceValue.known(value, conversionOrigin))
        .orElseGet(() -> EvidenceValue.missing(conversionOrigin, "The compiler conversion classification is unavailable"));
    var source = semantics.attributedMirror(childPath);
    var applicable = cast && !target.getKind().isPrimitive()
        && (sourceType.category() == JavaTypeCategory.NULL || referenceType(sourceType));
    var check = !applicable
        ? EvidenceValue.<Boolean>notApplicable(checkOrigin, "This source use has no reference-cast classification")
        : source.map(value -> EvidenceValue.known(referenceCheckRequired(value, target), checkOrigin))
            .orElseGet(() -> EvidenceValue.missing(checkOrigin, "The compiler reference-cast classification is unavailable"));
    return Optional.of(
        new ChildUseEvidence(scopeKey, consumer.nodeId(), child.role(), child.position(), sourceType, targetType, classification, check));
  }

  private static boolean referenceType(JavaTypeIdentity type) {
    return switch (type.category()) {
      case ARRAY, DECLARED, TYPE_VARIABLE, WILDCARD, UNION, INTERSECTION -> true;
      default -> false;
    };
  }

  private Optional<ConversionEvidence> classification(String scopeKey, SyntaxNode consumer, SyntaxChild child, TreePath childPath,
      TypeMirror target, boolean cast, boolean assignment) {
    var source = semantics.attributedMirror(childPath);
    if (source.isEmpty() || child.node().resolvedType().isEmpty() || !valueType(target)) {
      return Optional.empty();
    }
    var sourceType = source.orElseThrow();
    var sourceIdentity = child.node().resolvedType().orElseThrow();
    var targetIdentity = semantics.typeIdentity(target);
    if (sourceType instanceof PrimitiveType primitiveSource && !target.getKind().isPrimitive()) {
      var preBox = primitiveSource;
      if (!semantics.assignable(semantics.boxedType(preBox), target)) {
        var narrow = semantics.unboxedType(target);
        if (!assignment || narrow.isEmpty() || !constantNarrowing(childPath, primitiveSource, narrow.orElseThrow())) {
          return Optional.empty();
        }
        preBox = narrow.orElseThrow();
      }
      return Optional.of(decision(scopeKey, consumer, child, sourceIdentity, targetIdentity, Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.of(semantics.typeIdentity(preBox)), Optional.of(semantics.typeIdentity(semantics.boxedType(preBox)))));
    }
    if (!sourceType.getKind().isPrimitive() && target.getKind().isPrimitive()) {
      var unboxed = semantics.unboxedType(sourceType);
      var checked = Optional.<JavaTypeIdentity>empty();
      if (unboxed.isEmpty() && cast && target instanceof PrimitiveType primitiveTarget) {
        unboxed = Optional.of(primitiveTarget);
        checked = Optional.of(semantics.typeIdentity(semantics.boxedType(primitiveTarget)));
      }
      if (unboxed.isEmpty() || !semantics.assignable(unboxed.orElseThrow(), target)) {
        return Optional.empty();
      }
      return Optional.of(decision(scopeKey, consumer, child, sourceIdentity, targetIdentity, checked,
          Optional.of(semantics.typeIdentity(unboxed.orElseThrow())), Optional.of(targetIdentity), Optional.empty(), Optional.empty()));
    }
    if (sourceType.getKind().isPrimitive() && target.getKind().isPrimitive() && !cast && !semantics.assignable(sourceType, target)
        && !(assignment && sourceType instanceof PrimitiveType sourcePrimitive && target instanceof PrimitiveType targetPrimitive
            && constantNarrowing(childPath, sourcePrimitive, targetPrimitive))) {
      return Optional.empty();
    }
    return Optional.of(new ConversionEvidence(scopeKey, consumer.nodeId(), child.role(), child.position(), sourceIdentity, targetIdentity,
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
  }

  private boolean referenceCheckRequired(TypeMirror source, TypeMirror target) {
    if (source.getKind() == TypeKind.NULL || semantics.assignable(source, target)) {
      return false;
    }
    if (target instanceof IntersectionType intersection) {
      return intersection.getBounds().stream().anyMatch(bound -> referenceCheckRequired(source, bound));
    }
    return !semantics.assignable(semantics.erasedType(source), semantics.erasedType(target));
  }

  private boolean constantNarrowing(TreePath path, PrimitiveType source, PrimitiveType target) {
    if (source.getKind() != TypeKind.BYTE && source.getKind() != TypeKind.SHORT && source.getKind() != TypeKind.CHAR
        && source.getKind() != TypeKind.INT) {
      return false;
    }
    var value = constants.value(path).orElse(null);
    var number = value instanceof Character character
        ? (long) character.charValue()
        : value instanceof Number numeric ? numeric.longValue() : Long.MAX_VALUE;
    return switch (target.getKind()) {
      case BYTE -> number >= Byte.MIN_VALUE && number <= Byte.MAX_VALUE;
      case SHORT -> number >= Short.MIN_VALUE && number <= Short.MAX_VALUE;
      case CHAR -> number >= Character.MIN_VALUE && number <= Character.MAX_VALUE;
      default -> false;
    };
  }

  private Optional<StaticMemberEvidence> staticAccess(String scopeKey, NodeProjection projection) {
    var node = projection.node();
    var staticForm = (node.value() instanceof SyntaxValue.FieldAccess field && field.receiver().isStatic())
        || (node.value() instanceof SyntaxValue.Invocation invocation && invocation.form().isStatic());
    var kind = switch (node.syntaxKind()) {
      case FIELD_ACCESS -> Optional.of(isAssignmentTarget(projection.path()) ? StaticMemberKind.FIELD_WRITE : StaticMemberKind.FIELD_READ);
      case INVOCATION -> Optional.of(StaticMemberKind.METHOD_INVOKE);
      default -> Optional.<StaticMemberKind>empty();
    };
    var element = semantics.element(projection.path());
    if (kind.isEmpty() || !staticForm || node.semanticReference().flatMap(reference -> reference.symbolKey()).isEmpty()) {
      return Optional.empty();
    }
    if (element != null && (!semantics.isStatic(element) || !referenceMatches(node, element))) {
      throw new IllegalArgumentException("Selected static member contradicts its exact compiler projection");
    }
    var ownerOrigin = new EvidenceOrigin(scopeKey, node.nodeId(), StaticMemberEvidence.OWNER_SLOT, node.origin());
    var ownerType = element != null && element.getEnclosingElement() instanceof TypeElement owner
        ? semantics.typeReference(owner.asType()).filter(value -> value.declarationKey().isPresent())
        : Optional.<JavaTypeReference>empty();
    var declaringOwner = ownerType.map(value -> EvidenceValue.known(value, ownerOrigin))
        .orElseGet(() -> EvidenceValue.missing(ownerOrigin, "The selected static declaring owner could not be correlated"));
    var constantRead = kind.orElseThrow() == StaticMemberKind.FIELD_READ && element instanceof VariableElement variable
        && variable.getConstantValue() != null;
    var constantOrigin = new EvidenceOrigin(scopeKey, node.nodeId(), StaticMemberEvidence.CONSTANT_SLOT, node.origin());
    var classification = kind.orElseThrow() != StaticMemberKind.FIELD_READ
        ? EvidenceValue.<Boolean>notApplicable(constantOrigin, "This access form does not read a constant variable")
        : element == null
            ? EvidenceValue.<Boolean>missing(constantOrigin, "The selected constant-variable classification is unavailable")
            : EvidenceValue.known(constantRead, constantOrigin);
    return Optional.of(new StaticMemberEvidence(scopeKey, node.nodeId(), node.requiredReference().symbolKey().orElseThrow(),
        kind.orElseThrow(), declaringOwner, classification));
  }

  private static boolean isAssignmentTarget(TreePath path) {
    var target = path.getLeaf();
    var parent = path.getParentPath();
    while (parent != null && parent.getLeaf() instanceof ParenthesizedTree parentheses
        && JavaProjectionIdentity.sameInstance(parentheses.getExpression(), target)) {
      target = parent.getLeaf();
      parent = parent.getParentPath();
    }
    return parent != null && parent.getLeaf() instanceof AssignmentTree assignment
        && JavaProjectionIdentity.sameInstance(assignment.getVariable(), target);
  }

  private Optional<StringExpressionEvidence> concatenation(String scopeKey, NodeProjection projection,
      List<AnalysisDiagnostic> diagnostics) {
    var node = projection.node();
    if (!(projection.path().getLeaf() instanceof BinaryTree binary) || binary.getKind() != Tree.Kind.PLUS || !isString(node)) {
      return Optional.empty();
    }
    var left = node.requiredChild(SyntaxRole.LEFT_OPERAND);
    var right = node.requiredChild(SyntaxRole.RIGHT_OPERAND);
    if (left.resolvedType().isPresent() && right.resolvedType().isPresent() && !isString(left) && !isString(right)) {
      throw new IllegalArgumentException("An attributed String PLUS requires a String operand");
    }
    var origin = new EvidenceOrigin(scopeKey, node.nodeId(), StringExpressionEvidence.CONSTANT_SLOT, node.origin());
    var constant = constants.constantStatus(projection.path()).map(value -> EvidenceValue.known(value, origin))
        .orElseGet(() -> EvidenceValue.missing(origin, "The compiler constant-expression classification is unavailable"));
    return Optional.of(new StringExpressionEvidence(scopeKey, node.nodeId(), node.resolvedType().orElseThrow(),
        stringOperand(scopeKey, node, left, StringExpressionEvidence.LEFT_SLOT, diagnostics),
        stringOperand(scopeKey, node, right, StringExpressionEvidence.RIGHT_SLOT, diagnostics), constant));
  }

  private static EvidenceValue<Boolean> stringOperand(String scopeKey, SyntaxNode consumer, SyntaxNode operand, String slot,
      List<AnalysisDiagnostic> diagnostics) {
    var origin = new EvidenceOrigin(scopeKey, consumer.nodeId(), slot, consumer.origin());
    if (operand.resolvedType().isPresent()) {
      return EvidenceValue.known(isString(operand), origin);
    }
    if (operand.type() == SyntaxType.Erroneous.INSTANCE) {
      var key = StableId.sha256("diagnostic", scopeKey, operand.nodeId(), "frontend.error-type");
      var diagnostic = diagnostics.stream().filter(value -> value.diagnosticKey().equals(key)).findFirst().orElseThrow();
      return EvidenceValue.erroneous(origin, "The attributed operand type is erroneous", diagnostic);
    }
    return EvidenceValue.missing(origin, "The compiler operand classification is unavailable");
  }

  private boolean referenceMatches(SyntaxNode node, Element element) {
    var key = semantics.declarationKey(element);
    var reference = node.semanticReference();
    if (reference.isEmpty() || reference.orElseThrow().status() != ResolutionStatus.RESOLVED || key.isEmpty()) {
      return false;
    }
    if (!reference.orElseThrow().symbolKey().equals(key)) {
      throw new IllegalArgumentException("Selected compiler member contradicts the emitted syntax reference");
    }
    return true;
  }

  private static ConversionEvidence decision(String scopeKey, SyntaxNode consumer, SyntaxChild child, JavaTypeIdentity source,
      JavaTypeIdentity target, Optional<JavaTypeIdentity> checked, Optional<JavaTypeIdentity> unboxed, Optional<JavaTypeIdentity> effective,
      Optional<JavaTypeIdentity> preBox, Optional<JavaTypeIdentity> boxed) {
    return new ConversionEvidence(scopeKey, consumer.nodeId(), child.role(), child.position(), source, target, checked, unboxed, effective,
        preBox, boxed);
  }

  private static boolean isString(SyntaxNode node) {
    return node.resolvedType().map(type -> type.category() == JavaTypeCategory.DECLARED && "java.lang.String".equals(type.erasedName()))
        .orElse(false);
  }

  private static boolean valueType(TypeMirror type) {
    return switch (type.getKind()) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, ARRAY, DECLARED, TYPEVAR, WILDCARD, NULL, INTERSECTION -> true;
      default -> false;
    };
  }

  private static boolean assignmentContext(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION || node.syntaxKind() == SyntaxKind.ASSIGNMENT
        || node.syntaxKind() == SyntaxKind.RETURN || node.syntaxKind() == SyntaxKind.CONDITIONAL_EXPRESSION
        || node.syntaxKind() == SyntaxKind.CASE || node.syntaxKind() == SyntaxKind.YIELD;
  }

}
