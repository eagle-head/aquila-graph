package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;

record TranslationResult<T>(T value, List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> featureOccurrences,
    List<LanguageFeatureObservation> featureObservations) {
  TranslationResult {
    Objects.requireNonNull(value, "value");
    diagnostics = TranslationDiagnosticEvidence.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
    featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
  }

  List<DiagnosticEmission> diagnosticEmissions() {
    return TranslationDiagnosticEvidence.events(diagnostics);
  }
}
