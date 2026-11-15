package org.aquilagraph.source.internal;

import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.OPTIONAL_VALUE;

import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import java.util.List;

/** Reconciles a compiler type accessor that aliases the written type inside a binding pattern. */
public final class CoveragePatternFacts {
  private CoveragePatternFacts() {
  }

  /**
   * Checks the copied pattern test and its selected type evidence.
   *
   * @param expected
   *          expected
   * @param test
   *          test
   * @param facts
   *          facts
   * @return whether the facts satisfy the required relation
   */
  public static boolean testedType(CoveragePropertyValue expected, CoverageFactReference test, CoverageActualFactView facts) {
    var pattern = facts.child(test, SyntaxRole.PATTERN, 0);
    if (pattern.isEmpty()) {
      return CoverageWrittenAttachments.matches(expected, test, List.of(SyntaxRole.TYPE_OPERAND), facts, OPTIONAL_VALUE);
    }
    if (facts.requiredSyntax(pattern.orElseThrow()).syntaxKind() != SyntaxKind.BINDING_PATTERN) {
      return expected.state() == CoveragePropertyValue.State.ABSENT;
    }
    return facts.child(pattern.orElseThrow(), SyntaxRole.PARAMETER, 0)
        .map(binding -> CoverageWrittenAttachments.matches(expected, binding, List.of(SyntaxRole.DECLARED_TYPE), facts, OPTIONAL_VALUE))
        .orElse(false);
  }

}
