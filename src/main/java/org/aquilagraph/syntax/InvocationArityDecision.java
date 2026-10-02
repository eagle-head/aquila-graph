package org.aquilagraph.syntax;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;

/**
 * Selected applicability and array value shapes of one invocation.
 *
 * @param methodKey
 *          canonical owning method key
 * @param consumerNodeId
 *          invocation syntax identity
 * @param mode
 *          selected fixed or variable applicability
 * @param formalCount
 *          selected formal count
 * @param varargArrayType
 *          selected final formal array type in variable mode
 * @param erasedArrayType
 *          erased synthetic allocation array type in variable mode
 * @param elementType
 *          selected trailing element use type in variable mode
 */
public record InvocationArityDecision(String methodKey, String consumerNodeId, InvocationArityMode mode, int formalCount,
    Optional<JavaTypeIdentity> varargArrayType, Optional<JavaTypeIdentity> erasedArrayType, Optional<JavaTypeIdentity> elementType) {
  /** Validates local mode and type shape; argument and formal correlation require the owned invocation. */
  public InvocationArityDecision {
    DecisionValueValidation.requireKey(methodKey, "methodKey");
    DecisionValueValidation.requireKey(consumerNodeId, "consumerNodeId");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(varargArrayType, "varargArrayType");
    Objects.requireNonNull(erasedArrayType, "erasedArrayType");
    Objects.requireNonNull(elementType, "elementType");
    if (formalCount < 0 || (mode == InvocationArityMode.VARIABLE && formalCount == 0)) {
      throw new IllegalArgumentException("Selected arity requires a nonnegative count and a final formal in variable mode");
    }
    if (mode == InvocationArityMode.FIXED) {
      if (varargArrayType.isPresent() || erasedArrayType.isPresent() || elementType.isPresent()) {
        throw new IllegalArgumentException("Fixed arity has no synthetic array type evidence");
      }
    } else {
      if (varargArrayType.isEmpty() || erasedArrayType.isEmpty() || elementType.isEmpty()) {
        throw new IllegalArgumentException("Variable arity requires formal array, erased array and element types");
      }
      var formalArray = varargArrayType.orElseThrow();
      var allocationArray = erasedArrayType.orElseThrow();
      if (formalArray.category() != JavaTypeCategory.ARRAY || allocationArray.category() != JavaTypeCategory.ARRAY
          || !formalArray.erasedName().equals(allocationArray.erasedName())) {
        throw new IllegalArgumentException("Variable arity array types must have array category and the same erasure");
      }
      DecisionValueValidation.requireValueType(elementType.orElseThrow(), "elementType");
    }
  }
}
