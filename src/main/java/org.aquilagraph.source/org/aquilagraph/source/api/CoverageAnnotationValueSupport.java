package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/** Shared construction constraints and lossless text projection of typed annotation values. */
final class CoverageAnnotationValueSupport {
  private CoverageAnnotationValueSupport() {
  }

  static void requireKey(String value) {
    if (Objects.requireNonNull(value, "value").isBlank()) {
      throw new IllegalArgumentException("Typed annotation values require canonical declaration identities");
    }
  }

  static String canonicalText(CoverageAnnotationValue value) {
    var result = new StringBuilder();
    appendValue(result, value);
    return result.toString();
  }

  private static void appendValue(StringBuilder result, CoverageAnnotationValue value) {
    switch (value) {
      case CoverageAnnotationValue.Scalar scalar -> {
        result.append("S(");
        appendText(result, scalar.kind().name());
        appendText(result, scalar.value());
        result.append(')');
      }
      case CoverageAnnotationValue.EnumConstant constant -> {
        result.append("E(");
        appendText(result, constant.declarationKey());
        appendText(result, constant.ownerTypeKey());
        result.append(')');
      }
      case CoverageAnnotationValue.ClassLiteral literal -> {
        result.append("C(");
        appendOperand(result, literal.operand());
        result.append(')');
      }
      case CoverageAnnotationValue.Annotation annotation -> {
        result.append("N(");
        appendText(result, annotation.typeKey());
        appendText(result, annotation.provenance().name());
        result.append(annotation.explicitValues().size()).append(":[");
        for (var entry : annotation.explicitValues()) {
          appendText(result, entry.elementKey());
          appendValue(result, entry.value());
        }
        result.append("])");
      }
      case CoverageAnnotationValue.Array array -> {
        result.append("A(").append(array.elements().size()).append(":[");
        for (var element : array.elements()) {
          appendValue(result, element);
        }
        result.append("])");
      }
    }
  }

  private static void appendOperand(StringBuilder result, CoverageAnnotationValue.TypeOperand operand) {
    result.append("T(");
    appendText(result, operand.category().name());
    appendOptionalText(result, operand.declarationKey());
    appendOptionalText(result, operand.lexicalOwnerKey());
    if (operand.enclosingType().isPresent()) {
      result.append("P(");
      appendOperand(result, operand.enclosingType().orElseThrow());
      result.append(')');
    } else {
      result.append("O()");
    }
    result.append(operand.components().size()).append(":[");
    for (var component : operand.components()) {
      appendOperand(result, component);
    }
    result.append("])");
  }

  private static void appendOptionalText(StringBuilder result, Optional<String> value) {
    if (value.isPresent()) {
      result.append("P(");
      appendText(result, value.orElseThrow());
      result.append(')');
    } else {
      result.append("O()");
    }
  }

  private static void appendText(StringBuilder result, String value) {
    result.append(value.length()).append(':').append(value);
  }
}
