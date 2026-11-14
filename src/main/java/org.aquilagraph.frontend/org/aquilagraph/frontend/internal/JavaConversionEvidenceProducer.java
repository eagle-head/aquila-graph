package org.aquilagraph.frontend.internal;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.Projection;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Owns target selection and conversion evidence for corroborated executable uses. */
final class JavaConversionEvidenceProducer {
  private final SemanticResolver semantics;
  private final JavaConstantExpression constants;

  JavaConversionEvidenceProducer(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    constants = new JavaConstantExpression(semantics);
  }

  Optional<ChildUseEvidence> childUse(String scopeKey, NodeProjection consumer, SyntaxChild child, JavaExecutableProjectionProof bindings) {
    if (!child.node().syntaxKind().requiresType()) {
      return Optional.empty();
    }
    var childBinding = bindings.binding(child.node());
    return target(consumer, child, bindings).flatMap(selected -> conversion(scopeKey, consumer.node(), child, source(childBinding),
        selected, consumer.node().syntaxKind() == SyntaxKind.TYPE_CAST, assignmentContext(consumer.node())));
  }

  Optional<ChildUseEvidence> argument(String scopeKey, SyntaxNode consumer, SyntaxChild child, NodeProjection projection,
      TypeMirror target) {
    return conversion(scopeKey, consumer, child, source(projection), target, false, false);
  }

  Optional<ChildUseEvidence> argument(String scopeKey, SyntaxNode consumer, SyntaxChild child, TreePath path, TypeMirror target) {
    return conversion(scopeKey, consumer, child, source(path), target, false, false);
  }

  Optional<ChildUseEvidence> fieldInitializer(Projection projection, SyntaxNode field) {
    var bindings = JavaExecutableProjectionProof.corroborate(projection, semantics);
    var scopeKey = projection.scopeKey();
    var binding = bindings.binding(projection.translation().value());
    var initializerPath = binding.compilerAnchor();
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
    return semantics.attributedMirror(fieldPath).flatMap(target -> conversion(scopeKey, field,
        new SyntaxChild(SyntaxRole.INITIALIZER, 0, initializer.orElseThrow()), source(binding), target, false, true));
  }

  private Optional<TypeMirror> target(NodeProjection consumer, SyntaxChild child, JavaExecutableProjectionProof bindings) {
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
      case ASSIGNMENT ->
        child.role() == SyntaxRole.VALUE ? mirror(bindings.binding(node.requiredChild(SyntaxRole.LOCATION))) : Optional.empty();
      case RETURN -> child.role() == SyntaxRole.EXPRESSION ? enclosingReturn(consumer.compilerAnchor()) : Optional.empty();
      case CASE -> child.role() == SyntaxRole.BODY ? enclosingSwitch(consumer.compilerAnchor()) : Optional.empty();
      case YIELD -> child.role() == SyntaxRole.VALUE ? enclosingSwitch(consumer.compilerAnchor()) : Optional.empty();
      case SWITCH_EXPRESSION,
          SWITCH_STATEMENT ->
        child.role() == SyntaxRole.SELECTOR
            ? mirror(bindings.binding(child.node())).flatMap(mirror -> primitive(mirror)
                .<TypeMirror>map(type -> type.getKind() == TypeKind.BOOLEAN ? type : semantics.primitiveType(TypeKind.INT))
                .or(() -> Optional.of(mirror)))
            : Optional.empty();
      case BINARY -> child.role() == SyntaxRole.LEFT_OPERAND || child.role() == SyntaxRole.RIGHT_OPERAND
          ? binaryTarget(consumer, child, bindings)
          : Optional.empty();
      default -> Optional.empty();
    };
  }

  private Optional<TypeMirror> binaryTarget(NodeProjection consumer, SyntaxChild child, JavaExecutableProjectionProof bindings) {
    var operator = Tree.Kind.valueOf(consumer.node().sourceKind().name());
    var left = mirror(bindings.binding(consumer.node().requiredChild(SyntaxRole.LEFT_OPERAND)));
    var right = mirror(bindings.binding(consumer.node().requiredChild(SyntaxRole.RIGHT_OPERAND)));
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

  Optional<TypeMirror> mirror(NodeProjection projection) {
    return projection.derivation().map(JavaWrittenExpressionProjector.Facts::type)
        .or(() -> semantics.attributedMirror(projection.compilerAnchor()));
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
