package org.aquilagraph.frontend;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;

/** Renders compiler-selected declarations for private constructor witnesses. */
final class ConstructorTypeRenderer {
  private final Elements elements;
  private final Map<Element, String> names;

  ConstructorTypeRenderer(Elements elements, Map<Element, String> names) {
    this.elements = Objects.requireNonNull(elements, "elements");
    this.names = Map.copyOf(names);
  }

  String source(TypeMirror type) {
    var annotations = type.getAnnotationMirrors().stream().map(Object::toString).collect(Collectors.joining(" "));
    var annotatedPrefix = annotations.isEmpty() ? "" : annotations + " ";
    if (type instanceof ArrayType array) {
      return source(array.getComponentType()) + (annotations.isEmpty() ? "" : " " + annotations + " ") + "[]";
    }
    if (type instanceof TypeVariable variable) {
      return annotatedPrefix + names.getOrDefault(variable.asElement(), variable.asElement().getSimpleName().toString());
    }
    if (type instanceof DeclaredType declared && declared.asElement() instanceof TypeElement owner) {
      var enclosing = declared.getEnclosingType();
      var prefix = enclosing.getKind() == TypeKind.DECLARED
          ? source(enclosing) + "." + annotatedPrefix + owner.getSimpleName()
          : owner.getQualifiedName().toString();
      if (prefix.isEmpty()) {
        prefix = annotatedPrefix + owner.getSimpleName();
      } else if (enclosing.getKind() != TypeKind.DECLARED && !annotations.isEmpty()) {
        var suffix = owner.getSimpleName().length();
        prefix = prefix.substring(0, prefix.length() - suffix) + annotatedPrefix + owner.getSimpleName();
      }
      return prefix + (declared.getTypeArguments().isEmpty() ? "" : "<" + joinedSource(declared.getTypeArguments(), ",") + ">");
    }
    if (type instanceof WildcardType wildcard) {
      var upper = wildcard.getExtendsBound();
      var lower = wildcard.getSuperBound();
      return annotatedPrefix + (upper != null ? "? extends " + source(upper) : lower != null ? "? super " + source(lower) : "?");
    }
    if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID) {
      return annotatedPrefix + type.getKind().name().toLowerCase(Locale.ROOT);
    }
    throw new IllegalArgumentException("A constructor declaration requires a denotable type");
  }

  String sourceParameters(List<? extends TypeParameterElement> parameters) {
    if (parameters.isEmpty()) {
      return "";
    }
    return parameters.stream()
        .map(parameter -> parameter.getAnnotationMirrors().stream().map(Object::toString)
            .collect(Collectors.joining(" ", "", parameter.getAnnotationMirrors().isEmpty() ? "" : " "))
            + names.getOrDefault(parameter, parameter.getSimpleName().toString()) + " extends "
            + joinedSource(parameter.getBounds(), " & "))
        .collect(Collectors.joining(",", "<", "> "));
  }

  String diamondReceiver(DeclaredType declared) {
    var owner = (TypeElement) declared.asElement();
    var enclosing = declared.getEnclosingType();
    var prefix = enclosing.getKind() == TypeKind.DECLARED
        ? source(enclosing) + "." + owner.getSimpleName()
        : owner.getQualifiedName().toString();
    if (prefix.isEmpty()) {
      prefix = owner.getSimpleName().toString();
    }
    return prefix + (owner.getTypeParameters().isEmpty()
        ? ""
        : owner.getTypeParameters().stream().map(ignored -> "?").collect(Collectors.joining(",", "<", ">")));
  }

  String signature(TypeMirror type) {
    if (type instanceof ArrayType array) {
      return "[" + signature(array.getComponentType());
    }
    if (type instanceof TypeVariable variable) {
      return "T" + names.getOrDefault(variable.asElement(), variable.asElement().getSimpleName().toString()) + ";";
    }
    if (type instanceof DeclaredType declared && declared.asElement() instanceof TypeElement owner) {
      var enclosing = declared.getEnclosingType();
      var prefix = enclosing instanceof DeclaredType outer && !outer.getTypeArguments().isEmpty()
          ? signature(outer).substring(0, signature(outer).length() - 1) + "." + owner.getSimpleName()
          : "L" + elements.getBinaryName(owner).toString().replace('.', '/');
      return prefix + (declared.getTypeArguments().isEmpty()
          ? ""
          : declared.getTypeArguments().stream().map(this::signature).collect(Collectors.joining("", "<", ">"))) + ";";
    }
    if (type instanceof WildcardType wildcard) {
      var upper = wildcard.getExtendsBound();
      var lower = wildcard.getSuperBound();
      return upper != null ? "+" + signature(upper) : lower != null ? "-" + signature(lower) : "*";
    }
    return switch (type.getKind()) {
      case BOOLEAN -> "Z";
      case BYTE -> "B";
      case SHORT -> "S";
      case INT -> "I";
      case LONG -> "J";
      case CHAR -> "C";
      case FLOAT -> "F";
      case DOUBLE -> "D";
      case VOID -> "V";
      default -> throw new IllegalArgumentException("A constructor declaration requires a signature type");
    };
  }

  String signatureParameters(List<? extends TypeParameterElement> parameters) {
    if (parameters.isEmpty()) {
      return "";
    }
    return parameters.stream().map(parameter -> {
      var bounds = parameter.getBounds();
      var firstIsInterface = bounds.getFirst() instanceof DeclaredType first && first.asElement().getKind().isInterface();
      return names.getOrDefault(parameter, parameter.getSimpleName().toString()) + (firstIsInterface ? ":" : "")
          + bounds.stream().map(bound -> ":" + signature(bound)).collect(Collectors.joining());
    }).collect(Collectors.joining("", "<", ">"));
  }

  private String joinedSource(List<? extends TypeMirror> types, String delimiter) {
    return types.stream().map(this::source).collect(Collectors.joining(delimiter));
  }
}
