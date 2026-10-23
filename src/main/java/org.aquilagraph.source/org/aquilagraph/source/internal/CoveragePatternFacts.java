package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;

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
   * @param snapshot
   *          snapshot
   * @return whether the facts satisfy the required relation
   */
  public static boolean testedType(CoveragePropertyValue expected, CoverageSyntaxFactValue test, CoverageActualFactView facts,
      InputSnapshot snapshot) {
    var pattern = child(test, SyntaxRole.PATTERN, facts);
    if (pattern.isEmpty()) {
      return CoverageWrittenAttachments.matches(expected, test, List.of(SyntaxRole.TYPE_OPERAND), facts, snapshot, true);
    }
    if (pattern.orElseThrow().syntaxKind() != SyntaxKind.BINDING_PATTERN) {
      return expected.state() == CoveragePropertyValue.State.ABSENT;
    }
    return child(pattern.orElseThrow(), SyntaxRole.PARAMETER, facts)
        .map(binding -> CoverageWrittenAttachments.matches(expected, binding, List.of(SyntaxRole.DECLARED_TYPE), facts, snapshot, true))
        .orElse(false);
  }

  private static Optional<CoverageSyntaxFactValue> child(CoverageSyntaxFactValue owner, SyntaxRole role, CoverageActualFactView facts) {
    return owner.children().stream().filter(child -> child.role() == role)
        .flatMap(child -> facts.stream().filter(fact -> fact.identity().equals(child.nodeKey())).flatMap(facts::syntaxValues)).findFirst();
  }
}
