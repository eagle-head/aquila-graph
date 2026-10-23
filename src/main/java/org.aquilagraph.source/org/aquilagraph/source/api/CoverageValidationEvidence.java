package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;

/**
 * Public immutable route from controlled result admission to actual input admission.
 *
 * @param writtenInventory
 *          independently captured source obligations
 * @param attributedExpectations
 *          independently copied semantic projections
 * @param outcomes
 *          ordered context policy and support dispositions
 * @param unmatchedFacts
 *          diagnosed actual facts without a matching expected slot
 * @param diagnosticEmissions
 *          creating-site diagnostic events with final ordered slots
 */
public record CoverageValidationEvidence(SourceCoverageInventory writtenInventory, AttributedCoverageExpectations attributedExpectations,
    List<FeatureContextOutcome> outcomes, List<UnmatchedSourceFact> unmatchedFacts, List<DiagnosticEmission> diagnosticEmissions) {
  /** Defensively copies all crossing collections. */
  public CoverageValidationEvidence {
    Objects.requireNonNull(writtenInventory, "writtenInventory");
    Objects.requireNonNull(attributedExpectations, "attributedExpectations");
    outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
    unmatchedFacts = List.copyOf(Objects.requireNonNull(unmatchedFacts, "unmatchedFacts"));
    diagnosticEmissions = List.copyOf(Objects.requireNonNull(diagnosticEmissions, "diagnosticEmissions"));
  }
}
