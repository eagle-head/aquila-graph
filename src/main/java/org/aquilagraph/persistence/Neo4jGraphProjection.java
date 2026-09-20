package org.aquilagraph.persistence;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable graph publication payload for one analysis run.
 *
 * @param runId
 *          analysis run identifier
 * @param nodes
 *          projected nodes
 * @param relationships
 *          projected relationships
 */
public record Neo4jGraphProjection(String runId, List<ProjectedNode> nodes, List<ProjectedRelationship> relationships) {
  /** Validates projection ownership and defensively copies elements. */
  public Neo4jGraphProjection {
    Objects.requireNonNull(runId, "runId");
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
    relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships"));
    var nodeKeys = new HashSet<String>();
    for (var node : nodes) {
      if (!nodeKeys.add(node.key())) {
        throw new IllegalArgumentException("Projected node keys must be unique within a run");
      }
    }
    var relationshipKeys = new HashSet<String>();
    for (var relationship : relationships) {
      if (!relationshipKeys.add(relationship.key()) || !nodeKeys.contains(relationship.sourceKey())
          || !nodeKeys.contains(relationship.targetKey())) {
        throw new IllegalArgumentException("Projected relationships must be unique and connect projected nodes");
      }
    }
  }
}
