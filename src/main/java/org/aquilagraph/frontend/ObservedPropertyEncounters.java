package org.aquilagraph.frontend;

import java.util.List;
import org.aquilagraph.model.CoveragePropertyValue;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.SourceContext;

/** Actual accessor encounters, independently produced from both expectation stages. */
record ObservedPropertyEncounters(List<Encounter> encounters) {
  ObservedPropertyEncounters {
    encounters = List.copyOf(encounters);
  }

  record Encounter(SourceContext.Address address, LanguageFeatureOccurrence occurrence, LanguageFeatureObservation observation,
      CoveragePropertyValue value) {
  }
}
