package org.aquilagraph.cfg.internal;

import java.util.Optional;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxRole;

/** Consumer-owned choice of the runtime String conversion operand. */
record StringOperandPlan(Optional<SyntaxRole> convertedSide, boolean compileTimeConstant) {
  static StringOperandPlan from(StringExpressionEvidence evidence) {
    var left = evidence.leftIsString().knownValue().orElseThrow();
    var right = evidence.rightIsString().knownValue().orElseThrow();
    if (left && right) {
      return new StringOperandPlan(Optional.empty(), evidence.compileTimeConstant().knownValue().orElseThrow());
    }
    return new StringOperandPlan(Optional.of(left ? SyntaxRole.RIGHT_OPERAND : SyntaxRole.LEFT_OPERAND),
        evidence.compileTimeConstant().knownValue().orElseThrow());
  }
}
