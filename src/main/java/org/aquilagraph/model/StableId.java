package org.aquilagraph.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Creates deterministic, namespaced SHA-256 identifiers. */
public final class StableId {
  private StableId() {
  }

  /**
   * Hashes a namespace and ordered components with unambiguous length framing.
   *
   * @param namespace
   *          identifier namespace
   * @param components
   *          ordered identity components
   * @return lowercase hexadecimal SHA-256 identifier
   */
  public static String sha256(String namespace, String... components) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(components, "components");
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      update(digest, namespace);
      for (var component : components) {
        update(digest, Objects.requireNonNull(component, "component"));
      }
      return namespace + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }

  private static void update(MessageDigest digest, String value) {
    var bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update((byte) (bytes.length >>> 24));
    digest.update((byte) (bytes.length >>> 16));
    digest.update((byte) (bytes.length >>> 8));
    digest.update((byte) bytes.length);
    digest.update(bytes);
  }
}
