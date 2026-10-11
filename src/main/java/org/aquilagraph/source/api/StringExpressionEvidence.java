package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Independent compiler classifications for one actual String PLUS expression.
 *
 * @param scopeKey
 *          canonical owning execution scope key
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
public record StringExpressionEvidence(String scopeKey, String binaryNodeId, JavaTypeIdentity stringType,
    EvidenceValue<Boolean> leftIsString, EvidenceValue<Boolean> rightIsString, EvidenceValue<Boolean> compileTimeConstant) {
  /** Exact left operand classification slot. */
  public static final String LEFT_SLOT = "string-expression.left-is-string";
  /** Exact right operand classification slot. */
  public static final String RIGHT_SLOT = "string-expression.right-is-string";
  /** Exact whole expression classification slot. */
  public static final String CONSTANT_SLOT = "string-expression.constant";
  /** Validates the supplied result and side shape without authenticating constant-expression evidence. */
  public StringExpressionEvidence {
    DecisionValueValidation.requireKey(scopeKey, "scopeKey");
    DecisionValueValidation.requireKey(binaryNodeId, "binaryNodeId");
    Objects.requireNonNull(stringType, "stringType");
    Objects.requireNonNull(leftIsString, "leftIsString");
    Objects.requireNonNull(rightIsString, "rightIsString");
    Objects.requireNonNull(compileTimeConstant, "compileTimeConstant");
    validateSlot(scopeKey, binaryNodeId, LEFT_SLOT, leftIsString);
    validateSlot(scopeKey, binaryNodeId, RIGHT_SLOT, rightIsString);
    validateSlot(scopeKey, binaryNodeId, CONSTANT_SLOT, compileTimeConstant);
    if (stringType.category() != JavaTypeCategory.DECLARED || !"java.lang.String".equals(stringType.erasedName())) {
      throw new IllegalArgumentException("String concatenation requires a declared java.lang.String result");
    }
    if (leftIsString.knownValue().equals(Optional.of(false)) && rightIsString.knownValue().equals(Optional.of(false))) {
      throw new IllegalArgumentException("A String PLUS requires at least one String operand");
    }
  }

  private static void validateSlot(String scopeKey, String nodeKey, String slot, EvidenceValue<Boolean> evidence) {
    var origin = evidence.origin();
    if (!origin.scopeKey().equals(scopeKey) || !origin.nodeKey().equals(nodeKey) || !origin.slot().equals(slot)) {
      throw new IllegalArgumentException("String evidence must retain its exact creating slot");
    }
    if (evidence.state() == EvidenceState.NOT_APPLICABLE) {
      throw new IllegalArgumentException("Every String PLUS has applicable operand and constant classification slots");
    }
  }
}
