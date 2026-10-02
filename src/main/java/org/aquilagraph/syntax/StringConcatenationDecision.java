package org.aquilagraph.syntax;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;

/**
 * Selected String context following ordinary operand uses of one binary PLUS.
 *
 * @param methodKey
 *          canonical owning method key
 * @param binaryNodeId
 *          binary syntax identity
 * @param stringType
 *          selected String result type
 * @param convertedSide
 *          sole non-String operand, or empty when both operands are String
 * @param compileTimeConstant
 *          compiler-backed whole-expression constant classification
 */
public record StringConcatenationDecision(String methodKey, String binaryNodeId, JavaTypeIdentity stringType,
    Optional<SyntaxRole> convertedSide, boolean compileTimeConstant) {
  /** Validates the supplied result and side shape without authenticating constant-expression evidence. */
  public StringConcatenationDecision {
    DecisionValueValidation.requireKey(methodKey, "methodKey");
    DecisionValueValidation.requireKey(binaryNodeId, "binaryNodeId");
    Objects.requireNonNull(stringType, "stringType");
    Objects.requireNonNull(convertedSide, "convertedSide");
    if (stringType.category() != JavaTypeCategory.DECLARED || !"java.lang.String".equals(stringType.erasedName())) {
      throw new IllegalArgumentException("String concatenation requires a declared java.lang.String result");
    }
    if (convertedSide.isPresent() && convertedSide.orElseThrow() != SyntaxRole.LEFT_OPERAND
        && convertedSide.orElseThrow() != SyntaxRole.RIGHT_OPERAND) {
      throw new IllegalArgumentException("String conversion may select only the left or right operand");
    }
  }
}
