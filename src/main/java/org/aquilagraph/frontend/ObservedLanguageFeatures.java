package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Source-context occurrence manifest paired with its retained feature observations. */
record ObservedLanguageFeatures(List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations,
    DiagnosticEmissions emissions) {
  ObservedLanguageFeatures {
    occurrences = List.copyOf(Objects.requireNonNull(occurrences, "occurrences"));
    observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
    Objects.requireNonNull(emissions, "emissions");
  }
  List<AnalysisDiagnostic> diagnostics() {
    return emissions.diagnostics();
  }
}
