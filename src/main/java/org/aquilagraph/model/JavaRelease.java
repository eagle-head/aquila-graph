package org.aquilagraph.model;

import java.util.Arrays;
import java.util.stream.Collectors;

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
    return Arrays.stream(values()).filter(release -> release.featureNumber == feature).findFirst().orElseThrow(
        () -> new IllegalArgumentException("Unsupported Java release: " + feature + ". Supported releases: " + supportedFeatures()));
  }

  private static String supportedFeatures() {
    return Arrays.stream(values()).map(release -> Integer.toString(release.featureNumber)).collect(Collectors.joining(", "));
  }
}
