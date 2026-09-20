package org.aquilagraph.repository.neo4j;

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
record Neo4jNode(String key, String label, Map<String, Object> properties) {
  /** Validates and defensively copies node data. */
  Neo4jNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(label, "label");
    properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
  }
}
