package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Content-addressed version of the complete captured software-analysis target.
 *
 * @param sourceSignature
 *          signature of the immutable source snapshot
 * @param environmentSignature
 *          signature of the compiler and dependency environment
 */
public record SoftwareVersion(String sourceSignature, String environmentSignature) {
  /** Signature algorithm used by captured software versions. */
  public static final String SIGNATURE_ALGORITHM = "SHA-256";

  /** Validates the component signatures. */
  public SoftwareVersion {
    sourceSignature = requireText(sourceSignature, "sourceSignature");
    environmentSignature = requireText(environmentSignature, "environmentSignature");
  }

  /**
   * Creates a version from the captured source tree and its complete analysis environment.
   *
   * @param sourceSnapshot
   *          immutable source snapshot
   * @param environmentSnapshot
   *          compiler and dependency snapshot
   * @return content-addressed software version
   * @throws IllegalArgumentException
   *           if the source snapshot does not use SHA-256
   */
  public static SoftwareVersion from(InputSnapshot sourceSnapshot, AnalysisEnvironmentSnapshot environmentSnapshot) {
    Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    if (!SIGNATURE_ALGORITHM.equals(sourceSnapshot.digestAlgorithm())) {
      throw new IllegalArgumentException("Software-version components must use " + SIGNATURE_ALGORITHM);
    }
    return new SoftwareVersion(sourceSnapshot.snapshotDigest(), environmentSnapshot.environmentDigest());
  }

  /**
   * Returns the algorithm used by {@link #signature()}.
   *
   * @return {@value #SIGNATURE_ALGORITHM}
   */
  public String signatureAlgorithm() {
    return SIGNATURE_ALGORITHM;
  }

  /**
   * Returns the stable signature of all captured target inputs.
   *
   * @return namespaced software-version signature
   */
  public String signature() {
    return StableId.sha256("software-version", sourceSignature, environmentSignature);
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
    return value;
  }
}
