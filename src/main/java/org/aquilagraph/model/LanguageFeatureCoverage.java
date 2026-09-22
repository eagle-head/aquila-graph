package org.aquilagraph.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, release-versioned language-feature coverage contract. */
public final class LanguageFeatureCoverage {
  private static final Comparator<LanguageFeatureClassification> CLASSIFICATION_ORDER = Comparator
      .comparing((LanguageFeatureClassification classification) -> classification.feature().category())
      .thenComparing(classification -> classification.feature().identifier());

  private final JavaRelease contractRelease;
  private final List<LanguageFeatureClassification> featureClassifications;

  private LanguageFeatureCoverage(JavaRelease release, List<LanguageFeatureClassification> classifications) {
    contractRelease = Objects.requireNonNull(release, "release");
    featureClassifications = validateClassifications(classifications);
  }

  /**
   * Creates a coverage contract for one supported Java release.
   *
   * @param release
   *          typed release defining the contract version
   * @param classifications
   *          explicitly classified features
   * @return immutable coverage contract
   */
  public static LanguageFeatureCoverage forRelease(JavaRelease release, List<LanguageFeatureClassification> classifications) {
    return new LanguageFeatureCoverage(release, classifications);
  }

  /**
   * Returns the typed Java release defining this contract version.
   *
   * @return supported release
   */
  public JavaRelease release() {
    return contractRelease;
  }

  /**
   * Returns the compiler feature number derived from the typed release.
   *
   * @return Java feature number
   */
  public int releaseNumber() {
    return contractRelease.feature();
  }

  /**
   * Returns explicitly classified features in deterministic identity order.
   *
   * @return immutable classifications
   */
  public List<LanguageFeatureClassification> classifications() {
    return List.copyOf(featureClassifications);
  }

  /**
   * Finds a classification only by exact feature identity. No wildcard, default, or unknown fallback is consulted.
   *
   * @param feature
   *          exact feature identity
   * @return matching classification, if declared
   */
  public Optional<LanguageFeatureClassification> classificationOf(LanguageFeature feature) {
    Objects.requireNonNull(feature, "feature");
    return featureClassifications.stream().filter(classification -> classification.feature().equals(feature)).findFirst();
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof LanguageFeatureCoverage coverage && contractRelease == coverage.contractRelease
        && featureClassifications.equals(coverage.featureClassifications));
  }

  @Override
  public int hashCode() {
    return Objects.hash(contractRelease, featureClassifications);
  }

  @Override
  public String toString() {
    return "LanguageFeatureCoverage[release=" + contractRelease + ", classifications=" + featureClassifications + ']';
  }

  private static List<LanguageFeatureClassification> validateClassifications(List<LanguageFeatureClassification> classifications) {
    Objects.requireNonNull(classifications, "classifications");
    var features = new HashSet<LanguageFeature>();
    var validated = new ArrayList<LanguageFeatureClassification>(classifications.size());
    for (var classification : classifications) {
      var requiredClassification = Objects.requireNonNull(classification, "classification");
      if (!features.add(requiredClassification.feature())) {
        var feature = requiredClassification.feature();
        throw new IllegalArgumentException("Duplicate language feature classification: " + feature.category() + ':' + feature.identifier());
      }
      validated.add(requiredClassification);
    }
    validated.sort(CLASSIFICATION_ORDER);
    return List.copyOf(validated);
  }
}
