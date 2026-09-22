package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;

/** Source-context occurrence manifest paired with its retained feature observations. */
record ObservedLanguageFeatures(List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
  ObservedLanguageFeatures {
    occurrences = List.copyOf(Objects.requireNonNull(occurrences, "occurrences"));
    observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
  }
}
