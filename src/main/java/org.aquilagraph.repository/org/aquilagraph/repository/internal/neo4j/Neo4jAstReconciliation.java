package org.aquilagraph.repository.internal.neo4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Classifies detached rows and tracks the remaining projection for one write attempt. */
final class Neo4jAstReconciliation {
  private static final String ENTITY_LABEL = "AstEntity";
  private final Map<String, Neo4jNode> unmatchedNodes = new LinkedHashMap<>();
  private final Map<String, Neo4jRelationship> unmatchedRelationships = new LinkedHashMap<>();
  private final Map<String, String> nodeAddresses = new HashMap<>();

  Neo4jAstReconciliation(Neo4jAstProjection projection) {
    projection.nodes().forEach(node -> unmatchedNodes.put(node.key(), node));
    projection.relationships().forEach(relationship -> unmatchedRelationships.put(relationship.key(), relationship));
  }

  NodeDecision observeNode(StoredNode stored) {
    var expected = unmatchedNodes.remove(stored.key());
    if (expected == null) {
      return new RemoveNode(stored.address());
    }
    nodeAddresses.put(expected.key(), stored.address());
    var labels = stored.labels();
    if (labels.size() == 2 && labels.contains(expected.label()) && stored.properties().equals(expected.properties())) {
      return new RetainNode(stored.address());
    }
    var extraLabels = labels.stream().filter(label -> !ENTITY_LABEL.equals(label) && !expected.label().equals(label)).toList();
    return new ReplaceNode(stored.address(), expected, extraLabels);
  }

  List<Neo4jNode> missingNodes() {
    return List.copyOf(unmatchedNodes.values());
  }

  void recordInsertedNode(String key, String address) {
    nodeAddresses.put(key, address);
  }

  RelationshipDecision observeRelationship(StoredRelationship stored) {
    var expected = unmatchedRelationships.get(stored.key());
    if (expected == null || !matches(stored, expected)) {
      return RelationshipDecision.REMOVE;
    }
    unmatchedRelationships.remove(expected.key());
    return RelationshipDecision.RETAIN;
  }

  private boolean matches(StoredRelationship stored, Neo4jRelationship expected) {
    return stored.type().equals(expected.type()) && stored.properties().equals(expected.properties())
        && stored.sourceAddress().equals(nodeAddresses.get(expected.sourceKey()))
        && stored.targetAddress().equals(nodeAddresses.get(expected.targetKey()));
  }

  List<Neo4jRelationship> missingRelationships() {
    return List.copyOf(unmatchedRelationships.values());
  }

  String nodeAddress(String key) {
    return Objects.requireNonNull(nodeAddresses.get(key));
  }

  // The query projects absent and non-string keys as null. These rows still have physical addresses.
  record StoredNode(String address, @Nullable String key, List<String> labels, Map<String, Object> properties) {
    StoredNode {
      labels = List.copyOf(labels);
      properties = Map.copyOf(properties);
    }
  }

  record StoredRelationship(String address, @Nullable String key, String type, String sourceAddress, String targetAddress,
      Map<String, Object> properties) {
    StoredRelationship {
      properties = Map.copyOf(properties);
    }
  }

  sealed interface NodeDecision permits RetainNode, RemoveNode, ReplaceNode {
  }

  record RetainNode(String address) implements NodeDecision {
  }

  record RemoveNode(String address) implements NodeDecision {
  }

  record ReplaceNode(String address, Neo4jNode expected, List<String> extraLabels) implements NodeDecision {
  }

  enum RelationshipDecision {
    RETAIN, REMOVE
  }
}
