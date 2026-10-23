package org.aquilagraph.frontend.internal;

import com.sun.source.tree.VariableTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ClassTree;
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
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.Projection;
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
import org.aquilagraph.source.api.SourceExecutionTraversal;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.SemanticReference;

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

  JavaBodyDecisions produce(Projection projection) {
    projection.requireSemantics(semantics);
    var scopeKey = projection.scopeKey();
    var root = projection.translation().value();
    var bindings = corroborate(root, projection.nodes());
    var uses = new ArrayList<ChildUseEvidence>();
    var invocations = new ArrayList<InvocationApplicability>();
    var staticAccesses = new ArrayList<StaticMemberEvidence>();
    var concatenations = new ArrayList<StringExpressionEvidence>();
    var constructorContexts = new ArrayList<ConstructorEvidence>();
    for (var consumer : SourceExecutionTraversal.nodes(root)) {
      var binding = Objects.requireNonNull(bindings.get(consumer));
      if (consumer.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
        continue;
      }
      if (consumer.syntaxKind() == SyntaxKind.INVOCATION || consumer.syntaxKind() == SyntaxKind.OBJECT_CREATION) {
        var context = constructors.produce(projection, consumer);
        context.ifPresent(constructorContexts::add);
        invocation(scopeKey, binding, bindings, uses, invocations);
      } else {
        for (var child : consumer.children()) {
          if (child.node().syntaxKind().requiresType()) {
            var childBinding = Objects.requireNonNull(bindings.get(child.node()));
            target(binding, child, bindings).flatMap(selected -> conversion(scopeKey, consumer, child, source(childBinding), selected,
                consumer.syntaxKind() == SyntaxKind.TYPE_CAST, assignmentContext(consumer))).ifPresent(uses::add);
          }
        }
      }
      staticAccess(scopeKey, binding).ifPresent(staticAccesses::add);
      concatenation(scopeKey, binding, projection).ifPresent(concatenations::add);
    }
    var selectedInvocations = new HashMap<String, InvocationApplicability>();
    invocations.forEach(value -> selectedInvocations.put(value.consumerNodeId(), value));
    var retainedInvocations = SourceExecutionTraversal.nodes(root).stream()
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
    var nodes = SourceExecutionTraversal.nodes(root);
    if (nodes.size() != bindings.size()) {
      throw new IllegalArgumentException("The final registered body must retain exactly its produced compiler projection");
    }
    for (var node : nodes) {
      var binding = bindings.get(node);
      if (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
        if (binding == null || !(binding.compilerAnchor().getLeaf() instanceof ClassTree)) {
          throw new IllegalArgumentException("A nested declaration boundary requires its exact compiler path");
        }
        continue;
      }
      if (binding != null && binding.derivation().isPresent()) {
        var correspondence = binding.sourceAssociation().flatMap(value -> value.normalizedExpression()).orElseThrow();
        if (!semantics.declarationType(binding.derivation().orElseThrow().type()).equals(node.type())
            || !(correspondence.compilerAnchor() instanceof CompilerExpressionAnchor.Execution execution)
            || !binding.compilerPath().equals(execution.path())
            || !semantics.type(binding.compilerAnchor()).equals(correspondence.compilerType())
            || (correspondence.occurrencePath().isEmpty() && !correspondence.region().matches(node))) {
          throw new IllegalArgumentException("A derived occurrence requires its actual compiler-region and written topology proof");
        }
        continue;
      }
      if (binding == null || !binding.compilerAnchor().getLeaf().getKind().name().equals(node.sourceKind().name())
          || !semantics.type(binding.compilerAnchor()).equals(node.type()) || binding.children().size() != node.children().size()) {
        throw new IllegalArgumentException("A final body node requires its exact attributed source projection");
      }
      for (var index = 0; index < node.children().size(); index++) {
        var child = node.children().get(index);
        var mapping = binding.children().get(index);
        var childBinding = bindings.get(child.node());
        if (childBinding == null || mapping.role() != child.role() || mapping.position() != child.position()
            || !mapping.structuralPath().equals(childBinding.compilerPath())
            || !JavaProjectionIdentity.sameInstance(mapping.treePath(), childBinding.compilerAnchor())
            || !JavaProjectionIdentity.sameInstance(mapping.tree(), childBinding.compilerAnchor().getLeaf())) {
          throw new IllegalArgumentException("A selected child use requires its exact direct child and compiler path");
        }
      }
    }
    return bindings;
  }

  Optional<ChildUseEvidence> fieldInitializer(Projection projection, SyntaxNode field) {
    projection.requireSemantics(semantics);
    var scopeKey = projection.scopeKey();
    var initializerPath = projection.binding(projection.translation().value()).compilerAnchor();
    var fieldPath = initializerPath.getParentPath();
    if (fieldPath == null || !(fieldPath.getLeaf() instanceof VariableTree variable)
        || !JavaProjectionIdentity.sameInstance(variable.getInitializer(), initializerPath.getLeaf())
        || !(field.value() instanceof SyntaxValue.VariableDeclaration declaration)
        || declaration.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT || !declaration.declarationKey().equals(scopeKey)
        || !field.sourceFileKey().equals(projection.source().sourceFileKey())) {
      throw new IllegalArgumentException("A field initializer decision requires its producing projection and declaration");
    }
    var initializer = field.optionalChild(SyntaxRole.INITIALIZER);
    if (initializer.isEmpty()) {
      return Optional.empty();
    }
    return semantics.attributedMirror(fieldPath)
        .flatMap(target -> conversion(scopeKey, field, new SyntaxChild(SyntaxRole.INITIALIZER, 0, initializer.orElseThrow()),
            source(projection.binding(projection.translation().value())), target, false, true));
  }

  JavaBodyDecisions enumConstant(String scopeKey, SyntaxNode constant, TreePath creationPath, Optional<SemanticReference> constructor) {
    var fieldPath = creationPath.getParentPath();
    if (fieldPath == null || !(fieldPath.getLeaf() instanceof VariableTree field)
        || !JavaProjectionIdentity.sameInstance(field.getInitializer(), creationPath.getLeaf())
        || !(constant.value() instanceof SyntaxValue.VariableDeclaration declaration)
        || declaration.kind() != SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT || !declaration.declarationKey().equals(scopeKey)
        || !semantics.declarationKey(semantics.element(fieldPath)).equals(Optional.of(scopeKey))) {
      throw new IllegalArgumentException("Enum constructor selection requires its exact constant declaration");
    }
    var uses = new ArrayList<ChildUseEvidence>();
    var invocations = new ArrayList<InvocationApplicability>();
    var selectedPath = semantics.enumConstructorPath(creationPath);
    if (selectedPath.isEmpty() || !(semantics.element(selectedPath.orElseThrow()) instanceof ExecutableElement executable)
        || !constructor.flatMap(reference -> reference.symbolKey()).equals(semantics.declarationKey(executable))
        || !(creationPath.getLeaf() instanceof NewClassTree creation)) {
      return new JavaBodyDecisions(List.of(), List.of(), List.of(), List.of());
    }
    if (!executable.getTypeParameters().isEmpty()) {
      if (semantics.element(creationPath) instanceof ExecutableElement actual) {
        constructorInvocation(scopeKey, constant, creationPath, actual, uses, invocations);
      }
    } else if (executable.asType() instanceof ExecutableType signature) {
      var arguments = creation.getArguments().stream().map(argument -> semantics.attributedMirror(new TreePath(creationPath, argument)))
          .toList();
      if (arguments.stream().allMatch(Optional::isPresent)) {
        applicability.select(signature, executable.isVarArgs(), arguments.stream().map(Optional::orElseThrow).toList())
            .ifPresent(selected -> {
              var array = selected.variableArray();
              invocations.add(new InvocationApplicability(scopeKey, constant.nodeId(), selected.mode(), selected.formalCount(),
                  array.map(semantics::typeIdentity), array.map(value -> semantics.typeIdentity(semantics.erasedType(value))),
                  array.map(value -> semantics.typeIdentity(value.getComponentType()))));
              for (var child : constant.children()) {
                if (child.role() == SyntaxRole.ARGUMENT) {
                  conversion(scopeKey, constant, child, source(Objects
                      .requireNonNull(TreePath.getPath(creationPath, creation.getArguments().get(child.position())), "enum argument path")),
                      selected.argumentTargets().get(child.position()), false, false).ifPresent(uses::add);
                }
              }
            });
      }
    }
    return new JavaBodyDecisions(uses, invocations.stream().map(value -> InvocationEvidence.known(value, constant.origin())).toList(),
        List.of(), List.of());
  }

  private void invocation(String scopeKey, NodeProjection consumer, Map<SyntaxNode, NodeProjection> bindings, List<ChildUseEvidence> uses,
      List<InvocationApplicability> invocations) {
    if (!(semantics.element(consumer.compilerAnchor()) instanceof ExecutableElement executable)
        || !referenceMatches(consumer.node(), executable)) {
      return;
    }
    if (consumer.compilerAnchor().getLeaf() instanceof NewClassTree creation
        && (!executable.getTypeParameters().isEmpty() || (creation.getClassBody() != null && !creation.getArguments().isEmpty()))) {
      constructorInvocation(scopeKey, consumer.node(), consumer.compilerAnchor(), executable, uses, invocations);
      return;
    }
    var selectedType = selectedExecutable(consumer.compilerAnchor(), executable);
    if (selectedType.isEmpty()) {
      return;
    }
    var arguments = new ArrayList<TypeMirror>();
    for (var child : consumer.node().children()) {
      if (child.role() == SyntaxRole.ARGUMENT) {
        var mirror = mirror(Objects.requireNonNull(bindings.get(child.node())));
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
        conversion(scopeKey, consumer.node(), child, source(Objects.requireNonNull(bindings.get(child.node()))),
            selected.argumentTargets().get(child.position()), false, false).ifPresent(uses::add);
      }
    }
  }

  private void constructorInvocation(String scopeKey, SyntaxNode consumer, TreePath path, ExecutableElement executable,
      List<ChildUseEvidence> uses, List<InvocationApplicability> invocations) {
    var proof = semantics.selectedConstructor(path, executable);
    if (proof.isEmpty()) {
      return;
    }
    var facts = proof.orElseThrow();
    var children = consumer.children(SyntaxRole.ARGUMENT);
    if (children.size() != facts.arguments().size()) {
      return;
    }
    for (var index = 0; index < children.size(); index++) {
      if (!children.get(index).resolvedType().equals(Optional.of(facts.arguments().get(index).sourceType()))) {
        return;
      }
    }
    invocations.add(new InvocationApplicability(scopeKey, consumer.nodeId(), facts.mode(), facts.formalCount(), facts.varargArrayType(),
        facts.erasedArrayType(), facts.elementType()));
    for (var index = 0; index < children.size(); index++) {
      var argument = facts.arguments().get(index);
      if (children.get(index).syntaxKind().requiresType()) {
        uses.add(ChildUseEvidence.known(new ConversionEvidence(scopeKey, consumer.nodeId(), SyntaxRole.ARGUMENT, index,
            argument.sourceType(), argument.selectedTarget(), Optional.empty(), argument.unboxedType(), argument.effectivePrimitiveType(),
            argument.preBoxPrimitiveType(), argument.boxedType()), consumer.syntaxKind(), consumer.origin()));
      }
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
      case LAMBDA_EXPRESSION ->
        child.role() == SyntaxRole.BODY ? semantics.functionalReturnType(consumer.compilerAnchor()) : Optional.empty();
      case LOCAL_DECLARATION -> child.role() == SyntaxRole.INITIALIZER ? mirror(consumer) : Optional.empty();
      case TYPE_CAST -> child.role() == SyntaxRole.EXPRESSION ? mirror(consumer) : Optional.empty();
      case CONDITIONAL_EXPRESSION ->
        child.role() == SyntaxRole.TRUE_EXPRESSION || child.role() == SyntaxRole.FALSE_EXPRESSION ? mirror(consumer) : Optional.empty();
      case UNARY -> child.role() == SyntaxRole.OPERAND ? mirror(consumer) : Optional.empty();
      case ASSIGNMENT -> child.role() == SyntaxRole.VALUE
          ? mirror(Objects.requireNonNull(bindings.get(node.requiredChild(SyntaxRole.LOCATION))))
          : Optional.empty();
      case RETURN -> child.role() == SyntaxRole.EXPRESSION ? enclosingReturn(consumer.compilerAnchor()) : Optional.empty();
      case CASE -> child.role() == SyntaxRole.BODY ? enclosingSwitch(consumer.compilerAnchor()) : Optional.empty();
      case YIELD -> child.role() == SyntaxRole.VALUE ? enclosingSwitch(consumer.compilerAnchor()) : Optional.empty();
      case SWITCH_EXPRESSION,
          SWITCH_STATEMENT ->
        child.role() == SyntaxRole.SELECTOR
            ? mirror(Objects.requireNonNull(bindings.get(child.node()))).flatMap(mirror -> primitive(mirror)
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
    var operator = Tree.Kind.valueOf(consumer.node().sourceKind().name());
    var left = mirror(Objects.requireNonNull(bindings.get(consumer.node().requiredChild(SyntaxRole.LEFT_OPERAND))));
    var right = mirror(Objects.requireNonNull(bindings.get(consumer.node().requiredChild(SyntaxRole.RIGHT_OPERAND))));
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.empty();
    }
    var first = left.orElseThrow();
    var second = right.orElseThrow();
    var childType = child.role() == SyntaxRole.LEFT_OPERAND ? first : second;
    if (isString(consumer.node()) || ((operator == Tree.Kind.EQUAL_TO || operator == Tree.Kind.NOT_EQUAL_TO)
        && !first.getKind().isPrimitive() && !second.getKind().isPrimitive())) {
      return Optional.of(childType);
    }
    if (operator == Tree.Kind.LEFT_SHIFT || operator == Tree.Kind.RIGHT_SHIFT || operator == Tree.Kind.UNSIGNED_RIGHT_SHIFT) {
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

  private Optional<ChildUseEvidence> conversion(String scopeKey, SyntaxNode consumer, SyntaxChild child, UseSource childSource,
      TypeMirror target, boolean cast, boolean assignment) {
    if (!child.node().syntaxKind().requiresType() || child.node().resolvedType().isEmpty() || !valueType(target)) {
      return Optional.empty();
    }
    var sourceType = child.node().resolvedType().orElseThrow();
    var targetType = semantics.typeIdentity(target);
    var conversionOrigin = new EvidenceOrigin(scopeKey, consumer.nodeId(),
        "child-use.conversion/" + child.role().stableId() + "/" + child.position(), consumer.origin());
    var checkOrigin = new EvidenceOrigin(scopeKey, consumer.nodeId(),
        "child-use.reference-check/" + child.role().stableId() + "/" + child.position(), consumer.origin());
    var classification = classification(scopeKey, consumer, child, childSource, target, cast, assignment)
        .map(value -> EvidenceValue.known(value, conversionOrigin))
        .orElseGet(() -> EvidenceValue.missing(conversionOrigin, "The compiler conversion classification is unavailable"));
    var source = childSource.type();
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

  private Optional<ConversionEvidence> classification(String scopeKey, SyntaxNode consumer, SyntaxChild child, UseSource childSource,
      TypeMirror target, boolean cast, boolean assignment) {
    var source = childSource.type();
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
        if (!assignment || narrow.isEmpty() || !constantNarrowing(childSource.constant(), primitiveSource, narrow.orElseThrow())) {
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
            && constantNarrowing(childSource.constant(), sourcePrimitive, targetPrimitive))) {
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

  private static boolean constantNarrowing(Optional<Object> constant, PrimitiveType source, PrimitiveType target) {
    if (source.getKind() != TypeKind.BYTE && source.getKind() != TypeKind.SHORT && source.getKind() != TypeKind.CHAR
        && source.getKind() != TypeKind.INT) {
      return false;
    }
    var value = constant.orElse(null);
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
      case FIELD_ACCESS ->
        Optional.of(isAssignmentTarget(projection.compilerAnchor()) ? StaticMemberKind.FIELD_WRITE : StaticMemberKind.FIELD_READ);
      case INVOCATION -> Optional.of(StaticMemberKind.METHOD_INVOKE);
      default -> Optional.<StaticMemberKind>empty();
    };
    var element = semantics.element(projection.compilerAnchor());
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

  private Optional<StringExpressionEvidence> concatenation(String scopeKey, NodeProjection projection, Projection evidence) {
    var node = projection.node();
    if (!(node.value() instanceof SyntaxValue.Operator operator) || operator.operator() != SyntaxOperator.PLUS || !isString(node)) {
      return Optional.empty();
    }
    var left = node.requiredChild(SyntaxRole.LEFT_OPERAND);
    var right = node.requiredChild(SyntaxRole.RIGHT_OPERAND);
    if (left.resolvedType().isPresent() && right.resolvedType().isPresent() && !isString(left) && !isString(right)) {
      throw new IllegalArgumentException("An attributed String PLUS requires a String operand");
    }
    var origin = new EvidenceOrigin(scopeKey, node.nodeId(), StringExpressionEvidence.CONSTANT_SLOT, node.origin());
    var constant = constantStatus(projection).map(value -> EvidenceValue.known(value, origin))
        .orElseGet(() -> EvidenceValue.missing(origin, "The compiler constant-expression classification is unavailable"));
    return Optional.of(new StringExpressionEvidence(scopeKey, node.nodeId(), node.resolvedType().orElseThrow(),
        stringOperand(scopeKey, node, left, StringExpressionEvidence.LEFT_SLOT, evidence),
        stringOperand(scopeKey, node, right, StringExpressionEvidence.RIGHT_SLOT, evidence), constant));
  }

  private static EvidenceValue<Boolean> stringOperand(String scopeKey, SyntaxNode consumer, SyntaxNode operand, String slot,
      Projection evidence) {
    var origin = new EvidenceOrigin(scopeKey, consumer.nodeId(), slot, consumer.origin());
    if (operand.resolvedType().isPresent()) {
      return EvidenceValue.known(isString(operand), origin);
    }
    if (operand.type() == SyntaxType.Erroneous.INSTANCE) {
      var diagnostic = evidence.erroneousType(operand).orElseThrow();
      return EvidenceValue.erroneous(origin, "The attributed operand type is erroneous", diagnostic);
    }
    return EvidenceValue.missing(origin, "The compiler operand classification is unavailable");
  }

  private Optional<TypeMirror> mirror(NodeProjection projection) {
    return projection.derivation().map(JavaWrittenExpressionProjector.Facts::type)
        .or(() -> semantics.attributedMirror(projection.compilerAnchor()));
  }

  private Optional<Boolean> constantStatus(NodeProjection projection) {
    return projection.derivation().isPresent()
        ? projection.derivation().orElseThrow().constantStatus()
        : constants.constantStatus(projection.compilerAnchor());
  }

  private UseSource source(NodeProjection projection) {
    return new UseSource(mirror(projection),
        projection.derivation().isPresent()
            ? projection.derivation().orElseThrow().constant()
            : constants.value(projection.compilerAnchor()));
  }

  private UseSource source(TreePath path) {
    return new UseSource(semantics.attributedMirror(path), constants.value(path));
  }

  private record UseSource(Optional<TypeMirror> type, Optional<Object> constant) {
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
