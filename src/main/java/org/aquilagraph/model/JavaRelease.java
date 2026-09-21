package org.aquilagraph.model;

import java.util.Objects;

/** Java language releases whose source semantics are supported by Aquila Graph. */
public enum JavaRelease {
  /** Java 25 language release. */
  JAVA_25(25);

  private final int featureNumber;

  JavaRelease(int feature) {
    featureNumber = feature;
  }

  /**
   * Returns the Java feature-release number supplied to the compiler.
   *
   * @return Java feature-release number
   */
  public int feature() {
    return featureNumber;
  }

  /**
   * Resolves a supported Java release from its feature-release number.
   *
   * @param feature
   *          Java feature-release number
   * @return supported release
   * @throws IllegalArgumentException
   *           if Aquila Graph does not support the release
   */
  public static JavaRelease fromFeature(int feature) {
    for (var release : values()) {
      if (release.featureNumber == feature) {
        return release;
      }
    }
    throw new IllegalArgumentException(unsupportedFeatureMessage(feature));
  }

  /**
   * Indicates whether Aquila Graph supports a Java feature number.
   *
   * @param feature
   *          Java feature-release number
   * @return {@code true} when the feature is supported
   */
  public static boolean supportsFeature(int feature) {
    for (var release : values()) {
      if (release.featureNumber == feature) {
        return true;
      }
    }
    return false;
  }

  /**
   * Describes an unsupported Java feature number and the supported alternatives.
   *
   * @param feature
   *          unsupported Java feature-release number
   * @return validation message
   */
  public static String unsupportedFeatureMessage(int feature) {
    return "Unsupported Java release: " + feature + ". Supported releases: " + formatSupportedFeatures();
  }

  private static String formatSupportedFeatures() {
    var supportedFeatures = new StringBuilder();
    for (var release : values()) {
      if (!supportedFeatures.isEmpty()) {
        supportedFeatures.append(", ");
      }
      supportedFeatures.append(release.featureNumber);
    }
    return Objects.requireNonNull(supportedFeatures.toString(), "supported features");
  }
}
