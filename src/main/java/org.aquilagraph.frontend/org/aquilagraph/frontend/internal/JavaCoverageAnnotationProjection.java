package org.aquilagraph.frontend.internal;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.aquilagraph.source.api.CoverageAnnotationValue;
import org.aquilagraph.source.api.CoverageAnnotationValue.Scalar;
import org.aquilagraph.source.api.CoverageAnnotationValue.TypeOperand;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.JavaTypeCategory;

/** Copies public annotation values into immutable typed evidence before translation output. */
final class JavaCoverageAnnotationProjection {
  private JavaCoverageAnnotationProjection() {
  }

  static Optional<CoverageAnnotationValue.Annotation> annotation(AnnotationMirror mirror, Element owner, SemanticResolver semantics) {
    var provenance = semantics.annotationOrigin(owner, mirror) == Elements.Origin.EXPLICIT
        ? Provenance.WRITTEN
        : Provenance.COMPILER_DERIVED;
    return annotation(mirror, provenance, semantics);
  }

  private static Optional<CoverageAnnotationValue.Annotation> annotation(AnnotationMirror mirror, Provenance provenance,
      SemanticResolver semantics) {
    var typeKey = semantics.declarationKey(mirror.getAnnotationType().asElement());
    if (typeKey.isEmpty()) {
      return Optional.empty();
    }
    var values = new ArrayList<CoverageAnnotationValue.Entry>();
    for (var entry : mirror.getElementValues().entrySet()) {
      var key = semantics.declarationKey(entry.getKey());
      var copied = value(entry.getValue(), semantics);
      if (key.isEmpty() || copied.isEmpty()) {
        return Optional.empty();
      }
      values.add(new CoverageAnnotationValue.Entry(key.orElseThrow(), copied.orElseThrow()));
    }
    return Optional.of(new CoverageAnnotationValue.Annotation(typeKey.orElseThrow(), values, provenance));
  }

  static Optional<String> repeatableContainer(TypeElement annotationType, SemanticResolver semantics) {
    for (var meta : annotationType.getAnnotationMirrors()) {
      if (meta.getAnnotationType().asElement() instanceof TypeElement metaType
          && metaType.getQualifiedName().contentEquals("java.lang.annotation.Repeatable")) {
        return meta.getElementValues().values().stream().map(AnnotationValue::getValue).filter(DeclaredType.class::isInstance)
            .map(DeclaredType.class::cast).flatMap(type -> semantics.declarationKey(type.asElement()).stream()).findFirst();
      }
    }
    return Optional.empty();
  }

  static Optional<CoverageAnnotationValue> value(AnnotationValue annotation, SemanticResolver semantics) {
    return switch (annotation.getValue()) {
      case Boolean value -> Optional.of(new Scalar(Scalar.Kind.BOOLEAN, value.toString()));
      case Byte value -> Optional.of(new Scalar(Scalar.Kind.BYTE, value.toString()));
      case Short value -> Optional.of(new Scalar(Scalar.Kind.SHORT, value.toString()));
      case Character value -> Optional.of(new Scalar(Scalar.Kind.CHAR, Integer.toString(value)));
      case Integer value -> Optional.of(new Scalar(Scalar.Kind.INT, value.toString()));
      case Long value -> Optional.of(new Scalar(Scalar.Kind.LONG, value.toString()));
      case Float value -> Optional.of(new Scalar(Scalar.Kind.FLOAT, value.toString()));
      case Double value -> Optional.of(new Scalar(Scalar.Kind.DOUBLE, value.toString()));
      case String value -> Optional.of(new Scalar(Scalar.Kind.STRING, value));
      case VariableElement value -> {
        var key = semantics.declarationKey(value);
        var owner = semantics.declarationKey(Objects.requireNonNull(value.getEnclosingElement(), "enum owner"));
        yield key.isPresent() && owner.isPresent()
            ? Optional.of(new CoverageAnnotationValue.EnumConstant(key.orElseThrow(), owner.orElseThrow()))
            : Optional.empty();
      }
      case TypeMirror value -> value.getKind() == TypeKind.ERROR
          ? Optional.empty()
          : Optional.of(new CoverageAnnotationValue.ClassLiteral(operand(value, semantics)));
      case AnnotationMirror value -> annotation(value, Provenance.WRITTEN, semantics).map(CoverageAnnotationValue.class::cast);
      case List<?> values -> {
        var copied = new ArrayList<CoverageAnnotationValue>();
        for (var item : values) {
          if (!(item instanceof AnnotationValue componentValue)) {
            yield Optional.empty();
          }
          var component = value(componentValue, semantics);
          if (component.isEmpty()) {
            yield Optional.empty();
          }
          copied.add(component.orElseThrow());
        }
        yield Optional.of(new CoverageAnnotationValue.Array(copied));
      }
      default -> Optional.empty();
    };
  }

  private static TypeOperand operand(TypeMirror mirror, SemanticResolver semantics) {
    if (mirror instanceof ArrayType array) {
      return new TypeOperand(JavaTypeCategory.ARRAY, Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(operand(array.getComponentType(), semantics)));
    }
    if (mirror instanceof DeclaredType declared) {
      var declaration = declared.asElement();
      var enclosing = declaration.getEnclosingElement();
      var lexical = enclosing instanceof TypeElement ? semantics.declarationKey(enclosing) : Optional.<String>empty();
      var ownerType = declared.getEnclosingType().getKind() == TypeKind.NONE
          ? Optional.<TypeOperand>empty()
          : Optional.of(operand(declared.getEnclosingType(), semantics));
      return new TypeOperand(JavaTypeCategory.DECLARED, semantics.declarationKey(declaration), lexical, ownerType,
          declared.getTypeArguments().stream().map(type -> operand(type, semantics)).toList());
    }
    return new TypeOperand(semantics.typeIdentity(mirror).category(), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
  }
}
