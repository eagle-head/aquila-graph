package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Qualified local edge query key for one exact graph interpretation.
 *
 * @param graphIdentity
 *          exact content-qualified graph
 * @param localKey
 *          local edge identity
 */
public record GraphEdgeKey(GraphIdentity graphIdentity, String localKey) {
  /** Rejects absent graph identity and blank local query keys. */
  public GraphEdgeKey {
    Objects.requireNonNull(graphIdentity, "graphIdentity");
    if (Objects.requireNonNull(localKey, "localKey").isBlank()) {
      throw new IllegalArgumentException("A qualified graph edge key cannot be blank");
    }
  }
}
