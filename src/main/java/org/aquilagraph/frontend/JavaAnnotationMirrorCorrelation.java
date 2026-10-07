package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Compares attributed annotation values and their exact positions within counterpart types. */
final class JavaAnnotationMirrorCorrelation implements RecordAnnotationMirrorCorrelation {
  private final SemanticResolver semantics;
  private final JavaAnnotationValues values;

  JavaAnnotationMirrorCorrelation(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    values = new JavaAnnotationValues(semantics);
  }

  @Override
  public boolean containsAll(List<? extends AnnotationMirror> mirrors, List<SyntaxNode> expected) {
    var available = expanded(mirrors);
    for (var item : expected) {
      var match = available.stream().filter(candidate -> matches(candidate, item)).findFirst();
      if (match.isEmpty()) {
        return false;
      }
      available.remove(match.orElseThrow());
    }
    return true;
  }

  boolean matches(AnnotationMirror mirror, SyntaxNode annotation) {
    return semantics.declarationKey(mirror.getAnnotationType().asElement())
        .equals(annotation.semanticReference().flatMap(reference -> reference.symbolKey()))
        && matchesArguments(mirror, annotation.children(SyntaxRole.ANNOTATION_ARGUMENT));
  }

  boolean matchesArguments(AnnotationMirror mirror, List<SyntaxNode> arguments) {
    var attributed = mirror.getElementValues();
    if (attributed.size() != arguments.size()) {
      return false;
    }
    for (var argument : arguments) {
      var expected = argument.value(SyntaxValue.AnnotationArgument.class);
      var entry = attributed.entrySet().stream()
          .filter(item -> semantics.declarationKey(item.getKey()).filter(expected.selectedElementKey()::equals).isPresent()).findFirst();
      if (entry.isEmpty() || !matchesValue(entry.orElseThrow().getValue(), expected.value(), argument.requiredChild(SyntaxRole.VALUE))) {
        return false;
      }
    }
    return true;
  }

  boolean matchesValue(AnnotationValue attributed, AnnotationElementValue expected, SyntaxNode expression) {
    if (expected instanceof AnnotationElementValue.Nested) {
      return attributed.getValue() instanceof AnnotationMirror mirror && matches(mirror, expression);
    }
    if (expected instanceof AnnotationElementValue.Array array) {
      if (!(attributed.getValue() instanceof List<?> items) || items.size() != array.elements().size()) {
        return false;
      }
      var expressions = expression.syntaxKind() == SyntaxKind.ANNOTATION_ARRAY
          ? expression.children(SyntaxRole.VALUE)
          : List.of(expression);
      if (expressions.size() != items.size()) {
        return false;
      }
      for (var index = 0; index < items.size(); index++) {
        if (!(items.get(index) instanceof AnnotationValue value)
            || !matchesValue(value, array.elements().get(index), expressions.get(index))) {
          return false;
        }
      }
      return true;
    }
    return values.compilerScalar(attributed).filter(expected::equals).isPresent();
  }

  private List<AnnotationMirror> expanded(List<? extends AnnotationMirror> mirrors) {
    var available = new ArrayList<AnnotationMirror>();
    for (var mirror : mirrors) {
      available.add(mirror);
      for (var value : mirror.getElementValues().values()) {
        if (value.getValue() instanceof List<?> nested) {
          for (var item : nested) {
            if (item instanceof AnnotationValue annotationValue && annotationValue.getValue() instanceof AnnotationMirror nestedMirror
                && nestedMirror.getAnnotationType().asElement() instanceof TypeElement nestedType
                && repeatableContainer(nestedType).filter(mirror.getAnnotationType().asElement()::equals).isPresent()) {
              available.add(nestedMirror);
            }
          }
        }
      }
    }
    return available;
  }

  private static Optional<TypeElement> repeatableContainer(TypeElement annotationType) {
    for (var annotation : annotationType.getAnnotationMirrors()) {
      if (!(annotation.getAnnotationType().asElement() instanceof TypeElement metaType)
          || !metaType.getQualifiedName().contentEquals("java.lang.annotation.Repeatable")) {
        continue;
      }
      for (var value : annotation.getElementValues().values()) {
        if (value.getValue() instanceof DeclaredType type && type.asElement() instanceof TypeElement container) {
          return Optional.of(container);
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean sameAnnotatedType(TypeMirror source, TypeMirror counterpart) {
    if (source.getKind() != counterpart.getKind() || !sameAnnotations(source.getAnnotationMirrors(), counterpart.getAnnotationMirrors())) {
      return false;
    }
    if (source instanceof WildcardType sourceWildcard && counterpart instanceof WildcardType targetWildcard) {
      return sameOptionalBound(Optional.ofNullable(sourceWildcard.getExtendsBound()), Optional.ofNullable(targetWildcard.getExtendsBound()))
          && sameOptionalBound(Optional.ofNullable(sourceWildcard.getSuperBound()), Optional.ofNullable(targetWildcard.getSuperBound()));
    }
    if (!semantics.sameType(source, counterpart)) {
      return false;
    }
    if (source instanceof ArrayType sourceArray && counterpart instanceof ArrayType targetArray) {
      return sameAnnotatedType(sourceArray.getComponentType(), targetArray.getComponentType());
    }
    if (source instanceof DeclaredType sourceDeclared && counterpart instanceof DeclaredType targetDeclared) {
      if (!sameEnclosingType(sourceDeclared.getEnclosingType(), targetDeclared.getEnclosingType())
          || sourceDeclared.getTypeArguments().size() != targetDeclared.getTypeArguments().size()) {
        return false;
      }
      for (var index = 0; index < sourceDeclared.getTypeArguments().size(); index++) {
        if (!sameAnnotatedType(sourceDeclared.getTypeArguments().get(index), targetDeclared.getTypeArguments().get(index))) {
          return false;
        }
      }
      return true;
    }
    return true;
  }

  private boolean sameEnclosingType(TypeMirror source, TypeMirror target) {
    return (source.getKind() == TypeKind.NONE && target.getKind() == TypeKind.NONE) || sameAnnotatedType(source, target);
  }

  private boolean sameOptionalBound(Optional<TypeMirror> source, Optional<TypeMirror> target) {
    return (source.isEmpty() && target.isEmpty())
        || (source.isPresent() && target.isPresent() && sameAnnotatedType(source.orElseThrow(), target.orElseThrow()));
  }

  private boolean sameAnnotations(List<? extends AnnotationMirror> source, List<? extends AnnotationMirror> target) {
    if (source.size() != target.size()) {
      return false;
    }
    var remaining = new ArrayList<>(target);
    for (var annotation : source) {
      var match = remaining.stream().filter(candidate -> sameAnnotation(annotation, candidate)).findFirst();
      if (match.isEmpty()) {
        return false;
      }
      remaining.remove(match.orElseThrow());
    }
    return remaining.isEmpty();
  }

  private boolean sameAnnotation(AnnotationMirror source, AnnotationMirror target) {
    if (!source.getAnnotationType().asElement().equals(target.getAnnotationType().asElement())) {
      return false;
    }
    var sourceValues = source.getElementValues();
    var targetValues = target.getElementValues();
    if (sourceValues.size() != targetValues.size()) {
      return false;
    }
    for (var entry : sourceValues.entrySet()) {
      var targetValue = targetValues.get(entry.getKey());
      if (targetValue == null || !sameValue(entry.getValue().getValue(), targetValue.getValue())) {
        return false;
      }
    }
    return true;
  }

  private boolean sameValue(Object source, Object target) {
    if (source instanceof AnnotationMirror annotation && target instanceof AnnotationMirror other) {
      return sameAnnotation(annotation, other);
    }
    if (source instanceof TypeMirror type && target instanceof TypeMirror other) {
      return semantics.sameType(type, other);
    }
    if (source instanceof List<?> values && target instanceof List<?> others) {
      if (values.size() != others.size()) {
        return false;
      }
      for (var index = 0; index < values.size(); index++) {
        if (!(values.get(index) instanceof AnnotationValue first) || !(others.get(index) instanceof AnnotationValue second)
            || !sameValue(first.getValue(), second.getValue())) {
          return false;
        }
      }
      return true;
    }
    return Objects.equals(source, target);
  }
}
