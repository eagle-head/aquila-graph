package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;

record TranslationResult<T>(T value, DiagnosticEmissions emissions, List<LanguageFeatureOccurrence> featureOccurrences,
    List<LanguageFeatureObservation> featureObservations) {
  TranslationResult {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(emissions, "emissions");
    featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
    featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
  }

  List<AnalysisDiagnostic> diagnostics() {
    return emissions.diagnostics();
  }
}
