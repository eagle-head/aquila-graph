package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;

record TranslationResult<T>(T value, List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> featureOccurrences,
    List<LanguageFeatureObservation> featureObservations) {
  TranslationResult {
    Objects.requireNonNull(value, "value");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
    featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
  }
}
