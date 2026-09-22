package org.aquilagraph.model;

import java.util.Objects;
import java.util.Optional;

/**
 * One exclusively classified feature in a release-bound coverage contract.
 *
 * @param feature
 *          exact compiler-neutral feature identity
 * @param policy
 *          exclusive coverage policy
 * @param representationBoundary
 *          project-owned boundary retaining a represented fact
 * @param irrelevanceRationale
 *          semantic-scope reason for an irrelevant fact
 * @param sourceLocator
 *          direct authoritative source for an irrelevance decision
 * @param diagnosticCode
 *          stable diagnostic emitted for a diagnosed fact
 */
public record LanguageFeatureClassification(LanguageFeature feature, LanguageFeaturePolicy policy, Optional<String> representationBoundary,
    Optional<String> irrelevanceRationale, Optional<String> sourceLocator, Optional<String> diagnosticCode) {
  /** Validates the exclusive metadata contract for the selected policy. */
  public LanguageFeatureClassification {
    Objects.requireNonNull(feature, "feature");
    Objects.requireNonNull(policy, "policy");
    representationBoundary = validateMetadata(representationBoundary, "representationBoundary");
    irrelevanceRationale = validateMetadata(irrelevanceRationale, "irrelevanceRationale");
    sourceLocator = validateMetadata(sourceLocator, "sourceLocator");
    diagnosticCode = validateMetadata(diagnosticCode, "diagnosticCode");

    switch (policy) {
      case REPRESENTED -> {
        requirePresent(representationBoundary, "REPRESENTED requires a representation boundary");
        requireAbsent(irrelevanceRationale, "REPRESENTED cannot carry an irrelevance rationale");
        requireAbsent(sourceLocator, "REPRESENTED cannot carry an irrelevance source locator");
        requireAbsent(diagnosticCode, "REPRESENTED cannot carry a diagnostic code");
      }
      case IRRELEVANT -> {
        requireAbsent(representationBoundary, "IRRELEVANT cannot carry a representation boundary");
        requirePresent(irrelevanceRationale, "IRRELEVANT requires a semantic-scope rationale");
        requirePresent(sourceLocator, "IRRELEVANT requires a direct source locator");
        requireAbsent(diagnosticCode, "IRRELEVANT cannot carry a diagnostic code");
      }
      case DIAGNOSED -> {
        requireAbsent(representationBoundary, "DIAGNOSED cannot carry a representation boundary");
        requireAbsent(irrelevanceRationale, "DIAGNOSED cannot carry an irrelevance rationale");
        requireAbsent(sourceLocator, "DIAGNOSED cannot carry an irrelevance source locator");
        requirePresent(diagnosticCode, "DIAGNOSED requires a stable diagnostic code");
      }
    }
  }

  /**
   * Classifies a fact retained at a project-owned representation boundary.
   *
   * @param feature
   *          covered feature
   * @param representationBoundary
   *          retaining boundary
   * @return represented classification
   */
  public static LanguageFeatureClassification represented(LanguageFeature feature, String representationBoundary) {
    return new LanguageFeatureClassification(feature, LanguageFeaturePolicy.REPRESENTED, Optional.of(representationBoundary),
        Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Classifies a fact deliberately outside the semantic AST scope.
   *
   * @param feature
   *          covered feature
   * @param rationale
   *          semantic-scope rationale
   * @param sourceLocator
   *          direct authoritative source
   * @return irrelevant classification
   */
  public static LanguageFeatureClassification irrelevant(LanguageFeature feature, String rationale, String sourceLocator) {
    return new LanguageFeatureClassification(feature, LanguageFeaturePolicy.IRRELEVANT, Optional.empty(), Optional.of(rationale),
        Optional.of(sourceLocator), Optional.empty());
  }

  /**
   * Classifies a fact that produces a stable completion-blocking diagnostic.
   *
   * @param feature
   *          covered feature
   * @param diagnosticCode
   *          stable diagnostic code
   * @return diagnosed classification
   */
  public static LanguageFeatureClassification diagnosed(LanguageFeature feature, String diagnosticCode) {
    return new LanguageFeatureClassification(feature, LanguageFeaturePolicy.DIAGNOSED, Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.of(diagnosticCode));
  }

  /**
   * Indicates whether encountering this feature prevents completion.
   *
   * @return {@code true} when this classification is diagnosed
   */
  public boolean blocksCompletion() {
    return policy.blocksCompletion();
  }

  private static Optional<String> validateMetadata(Optional<String> value, String name) {
    var presentValue = Objects.requireNonNull(value, name);
    if (presentValue.isEmpty()) {
      return Optional.empty();
    }
    var content = presentValue.orElseThrow();
    if (content.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank when present");
    }
    if (!content.equals(content.strip())) {
      throw new IllegalArgumentException(name + " must not have surrounding whitespace");
    }
    return presentValue;
  }

  private static void requirePresent(Optional<String> value, String message) {
    if (value.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireAbsent(Optional<String> value, String message) {
    if (value.isPresent()) {
      throw new IllegalArgumentException(message);
    }
  }
}
