package org.aquilagraph.persistence;

import java.util.Map;
import java.util.Objects;

/**
 * Neo4j node label, stable key, and immutable property map.
 *
 * @param key
 *          stable node key
 * @param label
 *          allowlisted node label
 * @param properties
 *          node properties
 */
public record ProjectedNode(String key, String label, Map<String, Object> properties) {
  /** Validates and defensively copies node data. */
  public ProjectedNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(label, "label");
    properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
  }
}
