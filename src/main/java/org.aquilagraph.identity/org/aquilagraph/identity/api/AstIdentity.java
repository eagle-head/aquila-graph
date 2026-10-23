package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Qualified identity of one admitted source result.
 *
 * @param astResultKey
 *          derived result key
 * @param softwareSignature
 *          captured software signature
 * @param sourceModelVersion
 *          retained source-value format
 * @param astProfileVersion
 *          source qualification profile
 * @param semanticProductionRevision
 *          copied-evidence production revision
 * @param canonicalContentDigest
 *          digest of full canonical source content
 */
public record AstIdentity(String astResultKey, String softwareSignature, String sourceModelVersion, String astProfileVersion,
    String semanticProductionRevision, String canonicalContentDigest) {
  /** Checks the complete qualification tuple and its derived key. */
  public AstIdentity {
    requireText(astResultKey, "astResultKey");
    requireText(softwareSignature, "softwareSignature");
    requireText(sourceModelVersion, "sourceModelVersion");
    requireText(astProfileVersion, "astProfileVersion");
    requireText(semanticProductionRevision, "semanticProductionRevision");
    requireText(canonicalContentDigest, "canonicalContentDigest");
    if (!astResultKey
        .equals(key(softwareSignature, sourceModelVersion, astProfileVersion, semanticProductionRevision, canonicalContentDigest))) {
      throw new IllegalArgumentException("AST result key does not match its complete qualification tuple");
    }
  }

  /**
   * Derives a qualified identity without accepting a supplied result key.
   *
   * @param softwareSignature
   *          captured software signature
   * @param sourceModelVersion
   *          source-value format
   * @param astProfileVersion
   *          source profile
   * @param semanticProductionRevision
   *          evidence revision
   * @param canonicalContentDigest
   *          full canonical source digest
   * @return qualified result identity
   */
  public static AstIdentity fromContent(String softwareSignature, String sourceModelVersion, String astProfileVersion,
      String semanticProductionRevision, String canonicalContentDigest) {
    return new AstIdentity(
        key(softwareSignature, sourceModelVersion, astProfileVersion, semanticProductionRevision, canonicalContentDigest),
        softwareSignature, sourceModelVersion, astProfileVersion, semanticProductionRevision, canonicalContentDigest);
  }

  private static String key(String software, String format, String profile, String revision, String digest) {
    return StableId.sha256("ast-result", software, format, profile, revision, digest);
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
