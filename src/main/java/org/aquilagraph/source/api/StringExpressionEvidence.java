package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Independent compiler classifications for one actual String PLUS expression.
 *
 * @param methodKey
 *          canonical owning method key
 * @param binaryNodeId
 *          binary syntax identity
 * @param stringType
 *          selected String result type
 * @param leftIsString
 *          independent left operand classification
 * @param rightIsString
 *          independent right operand classification
 * @param compileTimeConstant
 *          independently retained whole-expression constant classification
 */
public record StringExpressionEvidence(String methodKey, String binaryNodeId, JavaTypeIdentity stringType,
    EvidenceValue<Boolean> leftIsString, EvidenceValue<Boolean> rightIsString, EvidenceValue<Boolean> compileTimeConstant) {
  /** Exact left operand classification slot. */
  public static final String LEFT_SLOT = "string-expression.left-is-string";
  /** Exact right operand classification slot. */
  public static final String RIGHT_SLOT = "string-expression.right-is-string";
  /** Exact whole expression classification slot. */
  public static final String CONSTANT_SLOT = "string-expression.constant";
  /** Validates the supplied result and side shape without authenticating constant-expression evidence. */
  public StringExpressionEvidence {
    DecisionValueValidation.requireKey(methodKey, "methodKey");
    DecisionValueValidation.requireKey(binaryNodeId, "binaryNodeId");
    Objects.requireNonNull(stringType, "stringType");
    Objects.requireNonNull(leftIsString, "leftIsString");
    Objects.requireNonNull(rightIsString, "rightIsString");
    Objects.requireNonNull(compileTimeConstant, "compileTimeConstant");
    validateSlot(methodKey, binaryNodeId, LEFT_SLOT, leftIsString);
    validateSlot(methodKey, binaryNodeId, RIGHT_SLOT, rightIsString);
    validateSlot(methodKey, binaryNodeId, CONSTANT_SLOT, compileTimeConstant);
    if (stringType.category() != JavaTypeCategory.DECLARED || !"java.lang.String".equals(stringType.erasedName())) {
      throw new IllegalArgumentException("String concatenation requires a declared java.lang.String result");
    }
    if (leftIsString.knownValue().equals(Optional.of(false)) && rightIsString.knownValue().equals(Optional.of(false))) {
      throw new IllegalArgumentException("A String PLUS requires at least one String operand");
    }
  }

  private static void validateSlot(String methodKey, String nodeKey, String slot, EvidenceValue<Boolean> evidence) {
    var origin = evidence.origin();
    if (!origin.methodKey().equals(methodKey) || !origin.nodeKey().equals(nodeKey) || !origin.slot().equals(slot)) {
      throw new IllegalArgumentException("String evidence must retain its exact creating slot");
    }
    if (evidence.state() == EvidenceState.NOT_APPLICABLE) {
      throw new IllegalArgumentException("Every String PLUS has applicable operand and constant classification slots");
    }
  }
}
