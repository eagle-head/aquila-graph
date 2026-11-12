package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.NodeDecision;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.RelationshipDecision;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.RemoveNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.ReplaceNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.RetainNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.StoredNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.StoredRelationship;
import org.junit.jupiter.api.Test;

class Neo4jAstReconciliationTest {
  private static final AstIdentity IDENTITY = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content");
  private static final String SCOPE = IDENTITY.astResultKey();

  @Test
  void givenExactNodesAndRelationship_whenRowsAreCompared_thenExistingAddressesAreRetainedWithoutInsertions() {
    // Arrange
    var decisions = new Neo4jAstReconciliation(new Neo4jAstProjection(IDENTITY,
        List.of(node(SCOPE, "AstResult"), node("existing", "AstValue")), List.of(relationship("child", "existing"))));

    // Act
    var root = decisions.observeNode(storedNode("root-address", SCOPE, "AstResult"));
    var child = decisions.observeNode(new StoredNode("child-address", "existing", List.of("AstValue", "AstEntity"),
        properties("existing", 1L)));
    var link = decisions.observeRelationship(storedRelationship("link-address", "child", "child-address"));

    // Assert
    assertEquals(new RetainNode("root-address"), root);
    assertEquals(new RetainNode("child-address"), child);
    assertEquals(RelationshipDecision.RETAIN, link);
    assertTrue(decisions.missingNodes().isEmpty());
    assertTrue(decisions.missingRelationships().isEmpty());
  }

  @Test
  void givenPopulatedGraphWithStaleRows_whenDecisionsAreApplied_thenExactGraphAndIndependentSubjectRemain() {
    // Arrange
    var decisions = new Neo4jAstReconciliation(new Neo4jAstProjection(IDENTITY,
        List.of(node(SCOPE, "AstResult"), node("changed", "AstValue"), node("relabeled", "AstValue"), node("new", "AstValue")),
        List.of(relationship("retained", "changed"), relationship("replaced", "relabeled"), relationship("created", "new"))));
    var root = storedNode("root-address", SCOPE, "AstResult");
    var changed = new StoredNode("changed-address", "changed", List.of("AstEntity", "AstValue"), properties("changed", 1.0d));
    var relabeled = new StoredNode("relabeled-address", "relabeled", List.of("AstEntity", "AstType", "Extra"),
        Map.of("astResultKey", SCOPE, "key", "relabeled", "value", 1L, "stale", true));
    var obsolete = storedNode("obsolete-address", "obsolete", "AstValue");
    var unkeyed = new StoredNode("unkeyed-address", null, List.of("AstEntity", "AstValue"), Map.of("astResultKey", SCOPE, "key", 7L));
    var independent = new StoredNode("independent-address", "independent", List.of("AstEntity", "AstResult"),
        Map.of("astResultKey", "other-scope", "key", "independent"));
    var retained = storedRelationship("retained-address", "retained", "changed-address");
    var duplicate = storedRelationship("duplicate-address", "retained", "changed-address");
    var wrongType = new StoredRelationship("wrong-type", "retained", "AST_CHILD", "root-address", "changed-address",
        properties("retained", 1L));
    var wrongSource = new StoredRelationship("wrong-source", "retained", "AST_FIELD", "independent-address", "changed-address",
        properties("retained", 1L));
    var wrongTarget = storedRelationship("wrong-target", "retained", "relabeled-address");
    var numericMismatch = new StoredRelationship("wrong-value", "replaced", "AST_FIELD", "root-address", "relabeled-address",
        properties("replaced", 1.0d));
    var incident = new StoredRelationship("incident-address", "incident", "AST_FIELD", "obsolete-address", "root-address",
        properties("incident", 1L));
    var unkeyedLink = new StoredRelationship("unkeyed-link", null, "AST_FIELD", "root-address", "relabeled-address",
        Map.of("astResultKey", SCOPE));
    var scopeMarkedForeign = new StoredRelationship("foreign-link", "foreign", "AST_FIELD", "independent-address", "independent-address",
        properties("foreign", 1L));
    var independentLink = new StoredRelationship("independent-link", "independent", "AST_FIELD", "independent-address", "independent-address",
        Map.of("astResultKey", "other-scope", "key", "independent"));
    var graph = new StoredGraph();
    var selectedNodes = List.of(root, changed, relabeled, obsolete, unkeyed);
    selectedNodes.forEach(graph::putNode);
    graph.putNode(independent);
    List.of(wrongType, wrongSource, wrongTarget, retained, duplicate, numericMismatch, incident, unkeyedLink, scopeMarkedForeign,
        independentLink).forEach(graph::putRelationship);
    var insertAddresses = Map.of("replaced", "replacement-address", "created", "created-address");

    // Act
    selectedNodes.forEach(stored -> graph.apply(decisions.observeNode(stored)));
    decisions.missingNodes().forEach(node -> {
      graph.putNode(new StoredNode("inserted-address", node.key(), List.of("AstEntity", node.label()), node.properties()));
      decisions.recordInsertedNode(node.key(), "inserted-address");
    });
    var relationshipsAfterNodeWork = Set.copyOf(graph.relationships.keySet());
    graph.reconcileRelationships(decisions, Set.of("independent-link"), insertAddresses);

    // Assert
    assertFalse(relationshipsAfterNodeWork.contains("incident-address"));
    assertEquals(Set.of(root, storedNode("changed-address", "changed", "AstValue"),
        storedNode("relabeled-address", "relabeled", "AstValue"), storedNode("inserted-address", "new", "AstValue"), independent),
        Set.copyOf(graph.nodes.values()));
    assertEquals(Set.of(retained, storedRelationship("replacement-address", "replaced", "relabeled-address"),
        storedRelationship("created-address", "created", "inserted-address"), independentLink), Set.copyOf(graph.relationships.values()));
  }

  private static Neo4jNode node(String key, String label) {
    return new Neo4jNode(key, label, properties(key, 1L));
  }

  private static Neo4jRelationship relationship(String key, String target) {
    return new Neo4jRelationship(key, "AST_FIELD", SCOPE, target, properties(key, 1L));
  }

  private static StoredNode storedNode(String address, String key, String label) {
    return new StoredNode(address, key, List.of("AstEntity", label), properties(key, 1L));
  }

  private static StoredRelationship storedRelationship(String address, String key, String target) {
    return new StoredRelationship(address, key, "AST_FIELD", "root-address", target, properties(key, 1L));
  }

  private static Map<String, Object> properties(String key, Object value) {
    return Map.of("astResultKey", SCOPE, "key", key, "value", value);
  }

  private static final class StoredGraph {
    private final Map<String, StoredNode> nodes = new LinkedHashMap<>();
    private final Map<String, StoredRelationship> relationships = new LinkedHashMap<>();

    void putNode(StoredNode node) {
      nodes.put(node.address(), node);
    }

    void putRelationship(StoredRelationship relationship) {
      relationships.put(relationship.address(), relationship);
    }

    void apply(NodeDecision decision) {
      switch (decision) {
        case RetainNode _ -> {
          // Existing node remains unchanged.
        }
        case RemoveNode removal -> {
          var address = removal.address();
          nodes.remove(address);
          relationships.values().removeIf(row -> address.equals(row.sourceAddress()) || address.equals(row.targetAddress()));
        }
        case ReplaceNode replacement -> {
          var labels = new LinkedHashSet<>(nodes.get(replacement.address()).labels());
          labels.removeAll(replacement.extraLabels());
          var expected = replacement.expected();
          labels.add(expected.label());
          putNode(new StoredNode(replacement.address(), expected.key(), List.copyOf(labels), expected.properties()));
        }
      }
    }

    void reconcileRelationships(Neo4jAstReconciliation decisions, Set<String> unrelated, Map<String, String> insertAddresses) {
      for (var row : List.copyOf(relationships.values())) {
        if (!unrelated.contains(row.address()) && decisions.observeRelationship(row) == RelationshipDecision.REMOVE) {
          relationships.remove(row.address());
        }
      }
      for (var expected : decisions.missingRelationships()) {
        putRelationship(new StoredRelationship(insertAddresses.get(expected.key()), expected.key(), expected.type(),
            decisions.nodeAddress(expected.sourceKey()), decisions.nodeAddress(expected.targetKey()), expected.properties()));
      }
    }
  }
}
