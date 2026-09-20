package org.aquilagraph.persistence;

import java.util.Map;
import java.util.Objects;

/**
 * Neo4j relationship type, endpoints, stable key, and immutable properties.
 *
 * @param key
 *          stable relationship key
 * @param type
 *          allowlisted relationship type
 * @param sourceKey
 *          projected source-node key
 * @param targetKey
 *          projected target-node key
 * @param properties
 *          relationship properties
 */
public record ProjectedRelationship(String key, String type, String sourceKey, String targetKey, Map<String, Object> properties) {
  /** Validates and defensively copies relationship data. */
  public ProjectedRelationship {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(sourceKey, "sourceKey");
    Objects.requireNonNull(targetKey, "targetKey");
    properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
  }
}
