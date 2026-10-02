package org.aquilagraph.repository.neo4j;

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
record Neo4jRelationship(String key, String type, String sourceKey, String targetKey, Map<String, Object> properties) {
  /** Validates and defensively copies relationship data. */
  Neo4jRelationship {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(sourceKey, "sourceKey");
    Objects.requireNonNull(targetKey, "targetKey");
    properties = Neo4jPropertyValues.copyOf(properties);
  }
}
