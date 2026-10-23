package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.CoverageValidationEvidence;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Direct immutable state of an admitted source coverage result. */
public final class AdmittedLanguageFeatureCoverageResult implements LanguageFeatureCoverageResult {
  private final LanguageFeatureCoverage coverageContract;
  private final List<LanguageFeatureOccurrence> occurrenceValues;
  private final List<LanguageFeatureObservation> observationValues;
  private final List<AnalysisDiagnostic> capabilityErrors;
  private final CoverageValidationEvidence retainedEvidence;

  private final Set<String> capabilityCodes;
  /**
   * Stores the canonical immutable coverage admitted by source ownership.
   *
   * @param coverage
   *          exact release contract
   * @param evidence
   *          independent validation evidence
   * @param occurrences
   *          ordered feature occurrences
   * @param observations
   *          ordered feature observations
   * @param diagnostics
   *          ordered capability diagnostics
   * @param codes
   *          exact diagnosed capability codes
   */
  public AdmittedLanguageFeatureCoverageResult(LanguageFeatureCoverage coverage, CoverageValidationEvidence evidence,
      List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations, List<AnalysisDiagnostic> diagnostics,
      Set<String> codes) {
    coverageContract = coverage;
    retainedEvidence = evidence;
    occurrenceValues = List.copyOf(occurrences);
    observationValues = List.copyOf(observations);
    capabilityErrors = List.copyOf(diagnostics);
    capabilityCodes = Set.copyOf(codes);
  }
  /**
   * Returns the exact release-bound contract.
   *
   * @return immutable contract
   */
  @Override
  public LanguageFeatureCoverage coverage() {
    return coverageContract;
  }

  /**
   * Returns known policy occurrences in producer partition order.
   *
   * @return immutable occurrences
   */
  @Override
  public List<LanguageFeatureOccurrence> occurrences() {
    return occurrenceValues;
  }

  /**
   * Returns known policy observations, including those with separately failed support.
   *
   * @return immutable observations
   */
  @Override
  public List<LanguageFeatureObservation> observations() {
    return observationValues;
  }

  /**
   * Returns the catalog capability subset of retained diagnostic events.
   *
   * @return immutable diagnostics
   */
  @Override
  public List<AnalysisDiagnostic> capabilityDiagnostics() {
    return capabilityErrors;
  }

  /**
   * Returns immutable evidence for actual source/fact/event admission.
   *
   * @return complete validation evidence
   */
  @Override
  public CoverageValidationEvidence validationEvidence() {
    return retainedEvidence;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof AdmittedLanguageFeatureCoverageResult result
        && coverageContract.equals(result.coverageContract) && retainedEvidence.equals(result.retainedEvidence));
  }

  @Override
  public int hashCode() {
    return Objects.hash(coverageContract, retainedEvidence);
  }

  @Override
  public String toString() {
    return "LanguageFeatureCoverageResult[coverage=" + coverageContract + ", validationEvidence=" + retainedEvidence + ']';
  }

  /**
   * Indicates whether a diagnostic code belongs to a diagnosed feature in this contract.
   *
   * @param diagnostic
   *          analysis diagnostic
   * @return {@code true} when the exact code is declared by a diagnosed feature
   */
  @Override
  public boolean isCapabilityDiagnostic(AnalysisDiagnostic diagnostic) {
    Objects.requireNonNull(diagnostic, "diagnostic");
    return capabilityCodes.contains(diagnostic.code());
  }

}
