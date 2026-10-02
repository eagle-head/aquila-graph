package org.aquilagraph.frontend;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
import org.aquilagraph.frontend.JavaMethodBodyTranslator.NodeProjection;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.syntax.InvocationArityDecision;
import org.aquilagraph.syntax.InvocationArityMode;
import org.aquilagraph.syntax.EnumConstructorContextDecision;
import org.aquilagraph.syntax.EnumConstructorContextDecision.Target;
import org.aquilagraph.syntax.StaticAccessDecision;
import org.aquilagraph.syntax.StaticAccessKind;
import org.aquilagraph.syntax.StringConcatenationDecision;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.TypedUseDecision;

/** Copies selected use facts while exact compiler paths and body instances remain live. */
final class JavaBodyDecisionProducer {
  private final SemanticResolver semantics;
  private final JavaInvocationApplicability applicability;
  private final JavaConstantExpression constants;
  private final JavaEnumConstructorContextProducer enumContexts;

  JavaBodyDecisionProducer(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    applicability = new JavaInvocationApplicability(semantics);
    constants = new JavaConstantExpression(semantics);
    enumContexts = new JavaEnumConstructorContextProducer(semantics);
  }

  JavaBodyDecisions produce(String methodKey, SyntaxNode root, List<NodeProjection> projections) {
    var bindings = corroborate(root, projections);
    var uses = new ArrayList<TypedUseDecision>();
    var invocations = new ArrayList<InvocationArityDecision>();
    var staticAccesses = new ArrayList<StaticAccessDecision>();
    var concatenations = new ArrayList<StringConcatenationDecision>();
    var constructorContexts = new ArrayList<EnumConstructorContextDecision>();
    for (var consumer : root.descendantsAndSelf()) {
      var binding = Objects.requireNonNull(bindings.get(consumer));
      if (consumer.syntaxKind() == SyntaxKind.INVOCATION || consumer.syntaxKind() == SyntaxKind.OBJECT_CREATION) {
        var context = enumContexts.produce(methodKey, root, binding);
        context.ifPresent(constructorContexts::add);
        if (context.map(decision -> decision.target() == Target.ENUM_SUPERCLASS).orElse(false)) {
          invocations.add(new InvocationArityDecision(methodKey, consumer.nodeId(), InvocationArityMode.FIXED, 2, Optional.empty(),
              Optional.empty(), Optional.empty()));
        } else {
          invocation(methodKey, binding, bindings, uses, invocations);
        }
      } else {
        for (var child : consumer.children()) {
          if (eligible(consumer, child)) {
            var childBinding = Objects.requireNonNull(bindings.get(child.node()));
            target(binding, child, bindings).flatMap(selected -> conversion(methodKey, consumer, child, childBinding.path(), selected,
                consumer.syntaxKind() == SyntaxKind.TYPE_CAST, assignmentContext(consumer))).ifPresent(uses::add);
          }
        }
      }
      staticAccess(methodKey, binding).ifPresent(staticAccesses::add);
      concatenation(methodKey, binding).ifPresent(concatenations::add);
    }
    return new JavaBodyDecisions(uses, invocations, staticAccesses, concatenations, constructorContexts);
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

  private void invocation(String methodKey, NodeProjection consumer, Map<SyntaxNode, NodeProjection> bindings, List<TypedUseDecision> uses,
      List<InvocationArityDecision> invocations) {
    if (!(semantics.element(consumer.path()) instanceof ExecutableElement executable) || !referenceMatches(consumer.node(), executable)) {
      return;
    }
    if (consumer.path().getLeaf() instanceof NewClassTree && !executable.getTypeParameters().isEmpty()) {
      constructorInvocation(methodKey, consumer, executable, uses, invocations);
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
    invocations.add(new InvocationArityDecision(methodKey, consumer.node().nodeId(), selected.mode(), selected.formalCount(),
        array.map(semantics::typeIdentity), array.map(value -> semantics.typeIdentity(semantics.erasedType(value))),
        array.map(value -> semantics.typeIdentity(value.getComponentType()))));
    for (var child : consumer.node().children()) {
      if (child.role() == SyntaxRole.ARGUMENT) {
        conversion(methodKey, consumer.node(), child, Objects.requireNonNull(bindings.get(child.node())).path(),
            selected.argumentTargets().get(child.position()), false, false).ifPresent(uses::add);
      }
    }
  }

  private void constructorInvocation(String methodKey, NodeProjection consumer, ExecutableElement executable, List<TypedUseDecision> uses,
      List<InvocationArityDecision> invocations) {
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
    invocations.add(new InvocationArityDecision(methodKey, consumer.node().nodeId(), facts.mode(), facts.formalCount(),
        facts.varargArrayType(), facts.erasedArrayType(), facts.elementType()));
    for (var index = 0; index < children.size(); index++) {
      var argument = facts.arguments().get(index);
      uses.add(new TypedUseDecision(methodKey, consumer.node().nodeId(), SyntaxRole.ARGUMENT, index, argument.sourceType(),
          argument.selectedTarget(), Optional.empty(), argument.unboxedType(), argument.effectivePrimitiveType(),
          argument.preBoxPrimitiveType(), argument.boxedType()));
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
    if (child.role() == SyntaxRole.CONDITION || node.syntaxKind() == SyntaxKind.CONDITIONAL_AND
        || node.syntaxKind() == SyntaxKind.CONDITIONAL_OR) {
      return Optional.of(semantics.primitiveType(TypeKind.BOOLEAN));
    }
    if (child.role() == SyntaxRole.INDEX) {
      return Optional.of(semantics.primitiveType(TypeKind.INT));
    }
    return switch (node.syntaxKind()) {
      case LOCAL_DECLARATION, TYPE_CAST, CONDITIONAL_EXPRESSION, UNARY -> semantics.attributedMirror(consumer.path());
      case ASSIGNMENT -> semantics.attributedMirror(Objects.requireNonNull(bindings.get(node.requiredChild(SyntaxRole.LOCATION))).path());
      case RETURN -> enclosingReturn(consumer.path());
      case CASE, YIELD -> enclosingSwitch(consumer.path());
      case SWITCH_EXPRESSION -> semantics.attributedMirror(Objects.requireNonNull(bindings.get(child.node())).path())
          .flatMap(mirror -> primitive(mirror)
              .<TypeMirror>map(type -> type.getKind() == TypeKind.BOOLEAN ? type : semantics.primitiveType(TypeKind.INT))
              .or(() -> Optional.of(mirror)));
      case BINARY -> binaryTarget(consumer, child, bindings);
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

  private Optional<TypedUseDecision> conversion(String methodKey, SyntaxNode consumer, SyntaxChild child, TreePath childPath,
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
      return Optional.of(decision(methodKey, consumer, child, sourceIdentity, targetIdentity, Optional.empty(), Optional.empty(),
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
      return Optional.of(decision(methodKey, consumer, child, sourceIdentity, targetIdentity, checked,
          Optional.of(semantics.typeIdentity(unboxed.orElseThrow())), Optional.of(targetIdentity), Optional.empty(), Optional.empty()));
    }
    if (sourceType.getKind().isPrimitive() && target.getKind().isPrimitive() && !cast && !semantics.assignable(sourceType, target)
        && !(assignment && sourceType instanceof PrimitiveType sourcePrimitive && target instanceof PrimitiveType targetPrimitive
            && constantNarrowing(childPath, sourcePrimitive, targetPrimitive))) {
      return Optional.empty();
    }
    var referenceCheck = cast && !sourceType.getKind().isPrimitive() && !target.getKind().isPrimitive()
        ? Optional.of(referenceCheckRequired(sourceType, target))
        : Optional.<Boolean>empty();
    return Optional.of(new TypedUseDecision(methodKey, consumer.nodeId(), child.role(), child.position(), sourceIdentity, targetIdentity,
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), referenceCheck));
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

  private Optional<StaticAccessDecision> staticAccess(String methodKey, NodeProjection projection) {
    var node = projection.node();
    var kind = switch (node.syntaxKind()) {
      case FIELD_ACCESS -> Optional.of(StaticAccessKind.FIELD_READ);
      case FIELD_LOCATION -> Optional.of(StaticAccessKind.FIELD_WRITE);
      case INVOCATION -> Optional.of(StaticAccessKind.METHOD_INVOKE);
      default -> Optional.<StaticAccessKind>empty();
    };
    var element = semantics.element(projection.path());
    if (kind.isEmpty() || element == null || !semantics.isStatic(element) || !referenceMatches(node, element)
        || !(element.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    var key = semantics.declarationKey(element);
    var declaringType = semantics.typeReference(owner.asType());
    if (key.isEmpty() || declaringType.isEmpty() || declaringType.orElseThrow().declarationKey().isEmpty()) {
      return Optional.empty();
    }
    var constantRead = kind.orElseThrow() == StaticAccessKind.FIELD_READ && element instanceof VariableElement variable
        && variable.getConstantValue() != null;
    return Optional.of(new StaticAccessDecision(methodKey, node.nodeId(), key.orElseThrow(), kind.orElseThrow(),
        declaringType.orElseThrow(), constantRead));
  }

  private Optional<StringConcatenationDecision> concatenation(String methodKey, NodeProjection projection) {
    var node = projection.node();
    if (!(projection.path().getLeaf() instanceof BinaryTree binary) || binary.getKind() != Tree.Kind.PLUS || !isString(node)) {
      return Optional.empty();
    }
    var left = node.requiredChild(SyntaxRole.LEFT_OPERAND);
    var right = node.requiredChild(SyntaxRole.RIGHT_OPERAND);
    if (left.resolvedType().isEmpty() || right.resolvedType().isEmpty()) {
      return Optional.empty();
    }
    if (!isString(left) && !isString(right)) {
      throw new IllegalArgumentException("An attributed String PLUS requires a String operand");
    }
    var side = isString(left) && isString(right)
        ? Optional.<SyntaxRole>empty()
        : Optional.of(isString(left) ? SyntaxRole.RIGHT_OPERAND : SyntaxRole.LEFT_OPERAND);
    return constants.constantStatus(projection.path())
        .map(constant -> new StringConcatenationDecision(methodKey, node.nodeId(), node.resolvedType().orElseThrow(), side, constant));
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

  private static TypedUseDecision decision(String methodKey, SyntaxNode consumer, SyntaxChild child, JavaTypeIdentity source,
      JavaTypeIdentity target, Optional<JavaTypeIdentity> checked, Optional<JavaTypeIdentity> unboxed, Optional<JavaTypeIdentity> effective,
      Optional<JavaTypeIdentity> preBox, Optional<JavaTypeIdentity> boxed) {
    return new TypedUseDecision(methodKey, consumer.nodeId(), child.role(), child.position(), source, target, checked, unboxed, effective,
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

  private static boolean eligible(SyntaxNode node, SyntaxChild child) {
    if (node.syntaxKind() == SyntaxKind.CASE) {
      return child.role() == SyntaxRole.BODY && child.node().syntaxKind() != SyntaxKind.BLOCK
          && child.node().syntaxKind() != SyntaxKind.THROW && child.node().syntaxKind() != SyntaxKind.UNSUPPORTED;
    }
    return TypedUseDecision.supportsFixedRole(node.syntaxKind(), child.role());
  }
}
