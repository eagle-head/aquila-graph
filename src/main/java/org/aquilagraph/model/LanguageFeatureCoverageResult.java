package org.aquilagraph.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable per-input language-feature outcome paired with its release-bound contract.
 *
 * @param coverage
 *          complete release-bound feature contract
 * @param occurrences
 *          complete source-context occurrence manifest produced by the frontend
 * @param observations
 *          exact source-owned language-feature outcomes
 * @param capabilityDiagnostics
 *          all encountered diagnosed kind and declaration-property outcomes
 */
public record LanguageFeatureCoverageResult(LanguageFeatureCoverage coverage, List<LanguageFeatureOccurrence> occurrences,
    List<LanguageFeatureObservation> observations, List<AnalysisDiagnostic> capabilityDiagnostics) {
  private static final Map<JavaRelease, String> AUTHORITATIVE_CONTRACT_IDENTITIES = Map.of(JavaRelease.JAVA_25,
      "java-language-feature-coverage:a25c92e01edc77ae5f5cde75658f03851744f4ab21a27582c18817713523c0ee");

  /** Validates exact contract membership and diagnostic ownership. */
  public LanguageFeatureCoverageResult {
    Objects.requireNonNull(coverage, "coverage");
    occurrences = List.copyOf(Objects.requireNonNull(occurrences, "occurrences"));
    observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
    capabilityDiagnostics = List.copyOf(Objects.requireNonNull(capabilityDiagnostics, "capabilityDiagnostics"));
    validateAuthoritativeContract(coverage);
    validateOccurrences(coverage, occurrences);
    validateObservations(coverage, observations, capabilityDiagnostics);
    validateOccurrenceInventory(occurrences, observations);
    validateCapabilityDiagnostics(coverage, capabilityDiagnostics);
    validateDiagnosticOwnership(observations, capabilityDiagnostics);
  }

  private static void validateOccurrences(LanguageFeatureCoverage coverage, List<LanguageFeatureOccurrence> occurrences) {
    occurrences.forEach(occurrence -> validateOccurrence(coverage, occurrence));
  }

  private static void validateOccurrence(LanguageFeatureCoverage coverage, LanguageFeatureOccurrence occurrence) {
    var classification = Objects.requireNonNull(occurrence, "occurrence").classification();
    var declared = coverage.classificationOf(classification.feature())
        .orElseThrow(() -> new IllegalArgumentException("Occurred language feature is absent from the coverage contract"));
    if (!declared.equals(classification)) {
      throw new IllegalArgumentException("Occurred language feature classification differs from the coverage contract");
    }
  }

  private static void validateOccurrenceInventory(List<LanguageFeatureOccurrence> occurrences,
      List<LanguageFeatureObservation> observations) {
    var observedOccurrences = observations.stream().map(LanguageFeatureOccurrence::fromObservation).toList();
    if (!occurrences.equals(observedOccurrences)) {
      throw new IllegalArgumentException("Language-feature observations must exactly match the source occurrence manifest");
    }
  }

  /**
   * Indicates whether a diagnostic code belongs to a diagnosed feature in this contract.
   *
   * @param diagnostic
   *          analysis diagnostic
   * @return {@code true} when the exact code is declared by a diagnosed feature
   */
  public boolean isCapabilityDiagnostic(AnalysisDiagnostic diagnostic) {
    Objects.requireNonNull(diagnostic, "diagnostic");
    return diagnosedCodes(coverage).contains(diagnostic.code());
  }

  private static void validateObservations(LanguageFeatureCoverage coverage, List<LanguageFeatureObservation> observations,
      List<AnalysisDiagnostic> capabilityDiagnostics) {
    for (var observation : observations) {
      validateObservation(coverage, capabilityDiagnostics, observation);
    }
  }

  private static void validateObservation(LanguageFeatureCoverage coverage, List<AnalysisDiagnostic> capabilityDiagnostics,
      LanguageFeatureObservation observation) {
    var classification = Objects.requireNonNull(observation, "observation").classification();
    var declared = coverage.classificationOf(classification.feature())
        .orElseThrow(() -> new IllegalArgumentException("Observed language feature is absent from the coverage contract"));
    if (!declared.equals(classification)) {
      throw new IllegalArgumentException("Observed language feature classification differs from the coverage contract");
    }
    observation.diagnostic().ifPresent(diagnostic -> {
      if (!capabilityDiagnostics.contains(diagnostic)) {
        throw new IllegalArgumentException("Observed capability diagnostic is absent from the coverage result");
      }
    });
  }

  private static void validateCapabilityDiagnostics(LanguageFeatureCoverage coverage, List<AnalysisDiagnostic> capabilityDiagnostics) {
    for (var diagnostic : capabilityDiagnostics) {
      var requiredDiagnostic = Objects.requireNonNull(diagnostic, "capabilityDiagnostic");
      var classifications = coverage.classifications().stream()
          .filter(classification -> classification.diagnosticCode().filter(requiredDiagnostic.code()::equals).isPresent()).toList();
      if (classifications.isEmpty()) {
        throw new IllegalArgumentException("Capability diagnostic code is absent from the coverage contract");
      }
      if (requiredDiagnostic.stage() != DiagnosticStage.FRONTEND || !requiredDiagnostic.isError()) {
        throw new IllegalArgumentException("Language feature capability diagnostics must be frontend errors");
      }
      var expectedOutcomes = classifications.stream().map(LanguageFeatureObservation::expectedOutcomeKind).distinct().toList();
      if (expectedOutcomes.size() != 1 || requiredDiagnostic.outcomeKind() != expectedOutcomes.getFirst()) {
        throw new IllegalArgumentException("Language feature capability diagnostic has an inconsistent outcome kind");
      }
    }
  }

  private static void validateAuthoritativeContract(LanguageFeatureCoverage coverage) {
    var expectedIdentity = AUTHORITATIVE_CONTRACT_IDENTITIES.get(coverage.release());
    if (expectedIdentity == null || !expectedIdentity.equals(coverage.contractIdentity())) {
      throw new IllegalArgumentException("Language-feature coverage must match the authoritative release contract");
    }
  }

  private static void validateDiagnosticOwnership(List<LanguageFeatureObservation> observations,
      List<AnalysisDiagnostic> capabilityDiagnostics) {
    for (var diagnostic : capabilityDiagnostics) {
      var observed = observations.stream().anyMatch(observation -> observation.diagnostic().filter(diagnostic::equals).isPresent());
      if (!observed) {
        throw new IllegalArgumentException("Capability diagnostic has no contextual language-feature observation");
      }
    }
  }

  private static Set<String> diagnosedCodes(LanguageFeatureCoverage coverage) {
    return coverage.classifications().stream().filter(LanguageFeatureClassification::blocksCompletion)
        .map(classification -> classification.diagnosticCode().orElseThrow()).collect(Collectors.toUnmodifiableSet());
  }
}
