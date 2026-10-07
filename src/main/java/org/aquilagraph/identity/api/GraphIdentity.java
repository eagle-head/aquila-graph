package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Content-qualified derivation identity for one exact source body interpretation.
 *
 * @param derivationKey
 *          derived owning graph key
 * @param astIdentity
 *          exact admitted source identity
 * @param cfgProfileVersion
 *          graph interpretation version
 * @param graphModelVersion
 *          retained graph schema version
 * @param methodKey
 *          canonical selected declaration
 * @param canonicalContentDigest
 *          complete graph value digest
 */
public record GraphIdentity(String derivationKey, AstIdentity astIdentity, String cfgProfileVersion, String graphModelVersion,
    String methodKey, String canonicalContentDigest) {
  /** Validates the complete qualification tuple and derived owning key. */
  public GraphIdentity {
    Objects.requireNonNull(astIdentity, "astIdentity");
    requireText(derivationKey, "derivationKey");
    requireText(cfgProfileVersion, "cfgProfileVersion");
    requireText(graphModelVersion, "graphModelVersion");
    requireText(methodKey, "methodKey");
    requireText(canonicalContentDigest, "canonicalContentDigest");
    if (!derivationKey.equals(key(astIdentity, cfgProfileVersion, graphModelVersion, methodKey, canonicalContentDigest))) {
      throw new IllegalArgumentException("Graph identity does not match its complete qualification tuple");
    }
  }

  /**
   * Derives an exact qualified graph identity from complete canonical content.
   *
   * @param astIdentity
   *          exact admitted source identity
   * @param cfgProfileVersion
   *          graph interpretation version
   * @param graphModelVersion
   *          retained graph schema version
   * @param methodKey
   *          canonical selected declaration
   * @param canonicalContentDigest
   *          complete graph value digest
   * @return content-qualified graph identity
   */
  public static GraphIdentity fromContent(AstIdentity astIdentity, String cfgProfileVersion, String graphModelVersion, String methodKey,
      String canonicalContentDigest) {
    return new GraphIdentity(key(astIdentity, cfgProfileVersion, graphModelVersion, methodKey, canonicalContentDigest), astIdentity,
        cfgProfileVersion, graphModelVersion, methodKey, canonicalContentDigest);
  }

  private static String key(AstIdentity astIdentity, String profile, String format, String method, String digest) {
    return StableId.sha256("cfg-derivation", astIdentity.astResultKey(), profile, format, method, digest);
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
