package org.aquilagraph.source.api;

import java.util.Optional;

/** Test-only construction of independent operand classifications for supplied source shapes. */
final class StringEvidenceFixture {
  private StringEvidenceFixture() {
  }

  static StringExpressionEvidence known(String methodKey, String nodeKey, JavaTypeIdentity stringType,
      Optional<SyntaxRole> nonStringOperand, EvidenceValue<Boolean> constant) {
    var range = constant.origin().sourceRange();
    var left = EvidenceValue.known(!nonStringOperand.equals(Optional.of(SyntaxRole.LEFT_OPERAND)),
        new EvidenceOrigin(methodKey, nodeKey, StringExpressionEvidence.LEFT_SLOT, range));
    var right = EvidenceValue.known(!nonStringOperand.equals(Optional.of(SyntaxRole.RIGHT_OPERAND)),
        new EvidenceOrigin(methodKey, nodeKey, StringExpressionEvidence.RIGHT_SLOT, range));
    return new StringExpressionEvidence(methodKey, nodeKey, stringType, left, right, constant);
  }
}
