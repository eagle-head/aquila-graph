package org.aquilagraph.source.api;

import java.util.List;

import org.aquilagraph.source.internal.AdmittedLanguageFeatureCoverageResult;
/** Immutable admitted per-input language-feature outcome and independent validation evidence. */
public sealed interface LanguageFeatureCoverageResult permits AdmittedLanguageFeatureCoverageResult {

  /**
   * Returns the exact release-bound contract.
   *
   * @return immutable contract
   */
  LanguageFeatureCoverage coverage();

  /**
   * Returns known policy occurrences in producer partition order.
   *
   * @return immutable occurrences
   */
  List<LanguageFeatureOccurrence> occurrences();

  /**
   * Returns known policy observations, including those with separately failed support.
   *
   * @return immutable observations
   */
  List<LanguageFeatureObservation> observations();

  /**
   * Returns the catalog capability subset of retained diagnostic events.
   *
   * @return immutable diagnostics
   */
  List<AnalysisDiagnostic> capabilityDiagnostics();

  /**
   * Returns immutable evidence for actual source/fact/event admission.
   *
   * @return complete validation evidence
   */
  CoverageValidationEvidence validationEvidence();

  /**
   * Indicates whether a diagnostic code belongs to a diagnosed feature in this contract.
   *
   * @param diagnostic
   *          analysis diagnostic
   * @return {@code true} when the exact code is declared by a diagnosed feature
   */
  boolean isCapabilityDiagnostic(AnalysisDiagnostic diagnostic);
}
