package org.aquilagraph.repository.internal.neo4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.identity.api.StableId;

/** Owns mutable Neo4j projection state for exactly one analysis run. */
final class Neo4jProjectionBuilder {
  private final String runId;
  private final List<Neo4jNode> nodes = new ArrayList<>();
  private final List<Neo4jRelationship> relationships = new ArrayList<>();

  Neo4jProjectionBuilder(String runId) {
    this.runId = Objects.requireNonNull(runId, "runId");
  }

  void node(String label, String key, Object... pairs) {
    node(label, key, properties(pairs));
  }

  void node(String label, String key, Map<String, Object> properties) {
    Neo4jSchema.requireNodeLabel(label);
    var allProperties = new LinkedHashMap<String, Object>();
    allProperties.put("runId", runId);
    allProperties.put("key", key);
    allProperties.putAll(properties);
    nodes.add(new Neo4jNode(key, label, allProperties));
  }

  void relate(String type, String sourceKey, String targetKey, String discriminator, Object... pairs) {
    relate(type, sourceKey, targetKey, discriminator, properties(pairs));
  }

  void relate(String type, String sourceKey, String targetKey, String discriminator, Map<String, Object> properties) {
    Neo4jSchema.requireRelationshipType(type);
    var key = StableId.sha256("relationship", type, sourceKey, targetKey, discriminator);
    var allProperties = new LinkedHashMap<String, Object>();
    allProperties.put("runId", runId);
    allProperties.put("key", key);
    allProperties.putAll(properties);
    relationships.add(new Neo4jRelationship(key, type, sourceKey, targetKey, allProperties));
  }

  Neo4jGraphProjection build() {
    return new Neo4jGraphProjection(runId, nodes, relationships);
  }

  static Map<String, Object> properties(Object... pairs) {
    if (pairs.length % 2 != 0) {
      throw new IllegalArgumentException("Property names and values must be paired");
    }
    var result = new LinkedHashMap<String, Object>();
    for (var index = 0; index < pairs.length; index += 2) {
      result.put((String) pairs[index], Objects.requireNonNull(pairs[index + 1], "property value"));
    }
    return result;
  }
}
