package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.AnalysisDiagnostic;

/** Source-context occurrence manifest paired with its retained feature observations. */
record ObservedLanguageFeatures(List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations,
    List<AnalysisDiagnostic> diagnostics) {
  ObservedLanguageFeatures {
    occurrences = List.copyOf(Objects.requireNonNull(occurrences, "occurrences"));
    observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
    diagnostics = TranslationDiagnosticEvidence.copyOf(diagnostics);
  }
}
