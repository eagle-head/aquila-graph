package org.aquilagraph.repository.internal.neo4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Neo4j storage payload for one analysis run.
 *
 * @param runId
 *          analysis run identifier
 * @param nodes
 *          projected nodes
 * @param relationships
 *          projected relationships
 */
record Neo4jGraphProjection(String runId, List<Neo4jNode> nodes, List<Neo4jRelationship> relationships) {
  /** Validates projection ownership and defensively copies elements. */
  public Neo4jGraphProjection {
    Objects.requireNonNull(runId, "runId");
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
    relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships"));
    var nodeKeys = new HashSet<String>();
    for (var node : nodes) {
      Neo4jSchema.requireNodeLabel(node.label());
      requireIdentity(runId, node.key(), node.properties());
      if (!nodeKeys.add(node.key())) {
        throw new IllegalArgumentException("Projected node keys must be unique within a run");
      }
    }
    var relationshipKeys = new HashSet<String>();
    for (var relationship : relationships) {
      Neo4jSchema.requireRelationshipType(relationship.type());
      requireIdentity(runId, relationship.key(), relationship.properties());
      if (!relationshipKeys.add(relationship.key()) || !nodeKeys.contains(relationship.sourceKey())
          || !nodeKeys.contains(relationship.targetKey())) {
        throw new IllegalArgumentException("Projected relationships must be unique and connect projected nodes");
      }
    }
  }

  private static void requireIdentity(String runId, String key, Map<String, Object> properties) {
    if (!runId.equals(properties.get("runId")) || !key.equals(properties.get("key"))) {
      throw new IllegalArgumentException("Projected properties must match their run and element identities");
    }
  }
}
