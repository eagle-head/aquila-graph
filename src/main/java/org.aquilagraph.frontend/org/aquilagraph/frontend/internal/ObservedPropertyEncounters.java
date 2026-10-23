package org.aquilagraph.frontend.internal;

import java.util.List;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.SourceContext;

/** Actual accessor encounters, independently produced from both expectation stages. */
record ObservedPropertyEncounters(List<Encounter> encounters) {
  ObservedPropertyEncounters {
    encounters = List.copyOf(encounters);
  }

  record Encounter(SourceContext.Address address, LanguageFeatureOccurrence occurrence, LanguageFeatureObservation observation,
      CoveragePropertyValue value) {
  }
}
