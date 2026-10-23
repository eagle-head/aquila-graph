package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * One source-owned observation of an exact release-bound language feature.
 *
 * @param sourceFileKey
 *          source file containing the observed feature
 * @param classification
 *          exact classification from the active coverage contract
 * @param origins
 *          available source spans for this occurrence
 * @param diagnostic
 *          inseparable capability diagnostic for a diagnosed occurrence
 */
public record LanguageFeatureObservation(String sourceFileKey, LanguageFeatureClassification classification, List<SourceSpan> origins,
    Optional<AnalysisDiagnostic> diagnostic) {
  /** Validates policy, source ownership, and diagnostic consistency. */
  public LanguageFeatureObservation {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    if (sourceFileKey.isBlank()) {
      throw new IllegalArgumentException("Language feature observations require a source-file identity");
    }
    Objects.requireNonNull(classification, "classification");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    Objects.requireNonNull(diagnostic, "diagnostic");
    if (origins.stream().anyMatch(origin -> !origin.sourceFileKey().equals(sourceFileKey))) {
      throw new IllegalArgumentException("Language feature origins must belong to the observed source file");
    }
    if (classification.blocksCompletion()) {
      validateDiagnosedObservation(classification, origins, diagnostic);
    } else if (diagnostic.isPresent()) {
      throw new IllegalArgumentException("Represented or irrelevant language features cannot carry a diagnostic");
    }
  }

  /**
   * Returns the typed outcome for this observed feature.
   *
   * @return compiler-neutral outcome kind
   */
  public AnalysisOutcomeKind outcomeKind() {
    return switch (classification.policy()) {
      case REPRESENTED -> AnalysisOutcomeKind.REPRESENTED_LANGUAGE_FEATURE;
      case IRRELEVANT -> AnalysisOutcomeKind.IRRELEVANT_LANGUAGE_FEATURE;
      case DIAGNOSED -> diagnostic.orElseThrow().outcomeKind();
    };
  }

  private static void validateDiagnosedObservation(LanguageFeatureClassification classification, List<SourceSpan> origins,
      Optional<AnalysisDiagnostic> diagnostic) {
    var capabilityDiagnostic = diagnostic
        .orElseThrow(() -> new IllegalArgumentException("Diagnosed language features require their capability diagnostic"));
    if (!capabilityDiagnostic.code().equals(classification.diagnosticCode().orElseThrow())) {
      throw new IllegalArgumentException("Diagnosed language feature must carry its exact catalog diagnostic");
    }
    if (capabilityDiagnostic.stage() != DiagnosticStage.FRONTEND || !capabilityDiagnostic.isError()) {
      throw new IllegalArgumentException("Language feature capability diagnostics must be frontend errors");
    }
    var expectedOutcome = expectedOutcomeKind(classification);
    if (capabilityDiagnostic.outcomeKind() != expectedOutcome) {
      throw new IllegalArgumentException("Diagnosed language feature has an inconsistent outcome kind");
    }
    if (!capabilityDiagnostic.origins().equals(origins)) {
      throw new IllegalArgumentException("Language feature observation and diagnostic origins must match");
    }
  }

  /**
   * Returns the stage outcome associated with the feature classification.
   *
   * @param classification
   *          classification
   * @return the resulting analysis outcome kind
   */
  public static AnalysisOutcomeKind expectedOutcomeKind(LanguageFeatureClassification classification) {
    var feature = classification.feature();
    var erroneousCompilerState = (feature.category() == LanguageFeatureCategory.COMPILER_TREE_KIND
        && "ERRONEOUS".equals(feature.identifier()))
        || (feature.category() == LanguageFeatureCategory.COMPILER_TYPE_KIND && "ERROR".equals(feature.identifier()));
    return erroneousCompilerState ? AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE : AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE;
  }
}
