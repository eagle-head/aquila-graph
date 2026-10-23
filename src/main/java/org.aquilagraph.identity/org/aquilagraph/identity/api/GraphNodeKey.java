package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Qualified local node query key for one exact graph interpretation.
 *
 * @param graphIdentity
 *          exact content-qualified graph
 * @param localKey
 *          local node identity
 */
public record GraphNodeKey(GraphIdentity graphIdentity, String localKey) {
  /** Rejects absent graph identity and blank local query keys. */
  public GraphNodeKey {
    Objects.requireNonNull(graphIdentity, "graphIdentity");
    if (Objects.requireNonNull(localKey, "localKey").isBlank()) {
      throw new IllegalArgumentException("A qualified graph node key cannot be blank");
    }
  }
}
