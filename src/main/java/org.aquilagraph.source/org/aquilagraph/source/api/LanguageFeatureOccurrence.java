package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * One source-context occurrence required in the language-feature outcome inventory.
 *
 * @param sourceFileKey
 *          source file containing the occurrence
 * @param classification
 *          exact classification from the active coverage contract
 * @param origins
 *          available source spans for this occurrence
 */
public record LanguageFeatureOccurrence(String sourceFileKey, LanguageFeatureClassification classification, List<SourceSpan> origins) {
  /** Validates source ownership and defensively copies occurrence evidence. */
  public LanguageFeatureOccurrence {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    if (sourceFileKey.isBlank()) {
      throw new IllegalArgumentException("Language feature occurrences require a source-file identity");
    }
    Objects.requireNonNull(classification, "classification");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    if (origins.stream().anyMatch(origin -> !origin.sourceFileKey().equals(sourceFileKey))) {
      throw new IllegalArgumentException("Language feature occurrence origins must belong to the source file");
    }
  }

  /**
   * Projects a copied feature observation into its immutable occurrence value.
   *
   * @param observation
   *          observation
   * @return the resulting language feature occurrence
   */
  public static LanguageFeatureOccurrence fromObservation(LanguageFeatureObservation observation) {
    Objects.requireNonNull(observation, "observation");
    return new LanguageFeatureOccurrence(observation.sourceFileKey(), observation.classification(), observation.origins());
  }
}
