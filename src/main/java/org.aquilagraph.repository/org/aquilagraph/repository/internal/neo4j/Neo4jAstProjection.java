package org.aquilagraph.repository.internal.neo4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.identity.api.AstIdentity;

/** Complete immutable, endpoint-validated replacement for one exact AST result. */
record Neo4jAstProjection(AstIdentity identity, List<Neo4jNode> nodes, List<Neo4jRelationship> relationships) {
  private static final int REQUIRED_ROOT_COUNT = 1;
  private static final String ROOT_LABEL = "AstResult";

  Neo4jAstProjection {
    Objects.requireNonNull(identity, "identity");
    nodes = List.copyOf(nodes);
    relationships = List.copyOf(relationships);
    var keys = new HashSet<String>();
    for (var node : nodes) {
      Neo4jAstSchema.requireLabel(node.label());
      requireScope(identity, node.key(), node.properties());
      if (!keys.add(node.key())) {
        throw new IllegalArgumentException("AST storage node keys must be unique");
      }
    }
    var relationshipKeys = new HashSet<String>();
    for (var relationship : relationships) {
      Neo4jAstSchema.requireRelationship(relationship.type());
      requireScope(identity, relationship.key(), relationship.properties());
      if (!relationshipKeys.add(relationship.key()) || !keys.contains(relationship.sourceKey())
          || !keys.contains(relationship.targetKey())) {
        throw new IllegalArgumentException("AST storage relationships require unique keys and exact owned endpoints");
      }
    }
    var roots = nodes.stream().filter(Neo4jAstProjection::isRoot).toList();
    if (roots.size() != REQUIRED_ROOT_COUNT || !identity.astResultKey().equals(roots.getFirst().key())) {
      throw new IllegalArgumentException("AST storage requires one exact result root");
    }
  }

  Neo4jNode root() {
    return nodes.stream().filter(Neo4jAstProjection::isRoot).findFirst().orElseThrow();
  }

  private static boolean isRoot(Neo4jNode node) {
    return ROOT_LABEL.equals(node.label());
  }

  private static void requireScope(AstIdentity identity, String key, Map<String, Object> properties) {
    if (!identity.astResultKey().equals(properties.get("astResultKey")) || !key.equals(properties.get("key"))) {
      throw new IllegalArgumentException("AST storage properties differ from their exact subject and element identity");
    }
  }
}
