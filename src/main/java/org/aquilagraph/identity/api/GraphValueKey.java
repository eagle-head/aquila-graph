package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Qualified local value query key for one exact graph interpretation.
 *
 * @param graphIdentity
 *          exact content-qualified graph
 * @param localKey
 *          local value identity
 */
public record GraphValueKey(GraphIdentity graphIdentity, String localKey) {
  /** Rejects absent graph identity and blank local query keys. */
  public GraphValueKey {
    Objects.requireNonNull(graphIdentity, "graphIdentity");
    if (Objects.requireNonNull(localKey, "localKey").isBlank()) {
      throw new IllegalArgumentException("A qualified graph value key cannot be blank");
    }
  }
}
