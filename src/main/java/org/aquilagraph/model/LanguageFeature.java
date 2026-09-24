package org.aquilagraph.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable compiler-neutral identity for one language feature in a coverage contract.
 *
 * @param category
 *          finite compiler surface containing the feature
 * @param identifier
 *          exact identifier within that surface
 */
public record LanguageFeature(LanguageFeatureCategory category, String identifier) {
  /** Validates the feature identity without normalizing it. */
  public LanguageFeature {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(identifier, "identifier");
    if (identifier.isBlank()) {
      throw new IllegalArgumentException("Language feature identifier must not be blank");
    }
    if (!identifier.equals(identifier.strip())) {
      throw new IllegalArgumentException("Language feature identifiers must not have surrounding whitespace: " + identifier);
    }
    if ((category == LanguageFeatureCategory.COMPILER_TREE_KIND || category == LanguageFeatureCategory.COMPILER_TYPE_KIND)
        && !identifier.equals(identifier.toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Compiler kind features must use exact enum-style identifiers: " + identifier);
    }
    var normalizedIdentifier = identifier.toUpperCase(Locale.ROOT);
    if ("*".equals(identifier) || "DEFAULT".equals(normalizedIdentifier) || "UNKNOWN".equals(normalizedIdentifier)) {
      throw new IllegalArgumentException("Language features must use exact identifiers, not catch-all identifiers: " + identifier);
    }
  }
}
