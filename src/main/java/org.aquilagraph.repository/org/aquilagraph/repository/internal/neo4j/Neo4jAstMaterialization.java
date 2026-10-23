package org.aquilagraph.repository.internal.neo4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;

/** Reconciles one immutable AST projection within its subject-locked write attempt. */
final class Neo4jAstMaterialization {
  private static final int WRITE_BATCH_SIZE = 512;
  private static final String ADDRESS_FIELD = "address";
  private static final String PROPERTIES_FIELD = "properties";
  private final TransactionContext transaction;
  private final Neo4jAstProjection projection;
  private final String scope;
  private final Map<String, String> nodeAddresses = new HashMap<>();

  private Neo4jAstMaterialization(TransactionContext transaction, Neo4jAstProjection projection) {
    this.transaction = transaction;
    this.projection = projection;
    scope = projection.identity().astResultKey();
  }

  static void replace(TransactionContext transaction, Neo4jAstProjection projection) {
    new Neo4jAstMaterialization(transaction, projection).replace();
  }

  private void replace() {
    // Addresses are collected anew for each attempt and never become persisted identities.
    transaction.run("MERGE (subject:AstEntity {astResultKey: $scope, key: $scope}) SET subject.key = subject.key",
        Values.parameters("scope", scope)).consume();
    reconcileNodes();
    reconcileRelationships();
  }

  private void reconcileNodes() {
    var missing = new LinkedHashMap<String, Neo4jNode>();
    projection.nodes().forEach(node -> missing.put(node.key(), node));
    var obsolete = new ArrayList<String>();
    var changed = new ArrayList<NodeChange>();
    try (var records = transaction
        .run("MATCH (entity:AstEntity {astResultKey: $scope}) "
            + "RETURN elementId(entity) AS address, labels(entity) AS labels, properties(entity) AS properties, "
            + "CASE WHEN entity.key IS :: STRING NOT NULL THEN entity.key ELSE null END AS key", Values.parameters("scope", scope))
        .stream()) {
      records.forEach(record -> {
        var address = record.get(ADDRESS_FIELD).asString();
        var expected = missing.remove(record.get("key").asString(null));
        if (expected == null) {
          obsolete.add(address);
          return;
        }
        nodeAddresses.put(expected.key(), address);
        var labels = record.get("labels").asList(value -> value.asString());
        if (!record.get(PROPERTIES_FIELD).asMap().equals(expected.properties()) || labels.size() != 2
            || !labels.contains(expected.label())) {
          var extra = labels.stream().filter(label -> !"AstEntity".equals(label) && !label.equals(expected.label())).toList();
          changed.add(new NodeChange(address, expected, extra));
        }
      });
    }
    batches(obsolete, batch -> transaction
        .run("MATCH (entity) WHERE elementId(entity) IN $addresses DETACH DELETE entity", Values.parameters("addresses", batch)).consume());
    batches(changed.stream().filter(change -> !change.extraLabels().isEmpty()).toList(),
        batch -> transaction
            .run("UNWIND $nodes AS item MATCH (entity) WHERE elementId(entity) = item.address REMOVE entity:$(item.labels)",
                Values.parameters("nodes",
                    batch.stream().map(change -> Map.of(ADDRESS_FIELD, change.address(), "labels", change.extraLabels())).toList()))
            .consume());
    groups(changed, change -> change.expected().label()).forEach((label, nodes) -> batches(nodes,
        batch -> transaction
            .run(
                "UNWIND $nodes AS item MATCH (entity) WHERE elementId(entity) = item.address " + "SET entity:`" + label
                    + "`, entity = item.properties",
                Values.parameters("nodes", batch.stream()
                    .map(change -> Map.of(ADDRESS_FIELD, change.address(), PROPERTIES_FIELD, change.expected().properties())).toList()))
            .consume()));
    groups(new ArrayList<>(missing.values()), Neo4jNode::label).forEach((label, nodes) -> batches(nodes, batch -> {
      try (var records = transaction.run(
          "UNWIND $nodes AS expected CREATE (entity:AstEntity:`" + label + "`) SET entity = expected "
              + "RETURN entity.key AS key, elementId(entity) AS address",
          Values.parameters("nodes", batch.stream().map(Neo4jNode::properties).toList())).stream()) {
        records.forEach(record -> nodeAddresses.put(record.get("key").asString(), record.get(ADDRESS_FIELD).asString()));
      }
    }));
  }

  private void reconcileRelationships() {
    var expected = new HashMap<String, Neo4jRelationship>();
    projection.relationships().forEach(relationship -> expected.put(relationship.key(), relationship));
    var retained = new HashSet<String>();
    var obsolete = new ArrayList<String>();
    try (var records = transaction
        .run("MATCH (source)-[relation]->(target) "
            + "WHERE (source:AstEntity AND source.astResultKey = $scope) OR relation.astResultKey = $scope "
            + "OR (target:AstEntity AND target.astResultKey = $scope) "
            + "RETURN elementId(relation) AS address, type(relation) AS type, properties(relation) AS properties, "
            + "elementId(source) AS source, elementId(target) AS target, "
            + "CASE WHEN relation.key IS :: STRING NOT NULL THEN relation.key ELSE null END AS key", Values.parameters("scope", scope))
        .stream()) {
      records.forEach(record -> {
        var relationship = expected.get(record.get("key").asString(null));
        if (relationship == null || !record.get("type").asString().equals(relationship.type())
            || !record.get(PROPERTIES_FIELD).asMap().equals(relationship.properties())
            || !record.get("source").asString().equals(nodeAddresses.get(relationship.sourceKey()))
            || !record.get("target").asString().equals(nodeAddresses.get(relationship.targetKey())) || !retained.add(relationship.key())) {
          obsolete.add(record.get(ADDRESS_FIELD).asString());
        }
      });
    }
    batches(obsolete,
        batch -> transaction
            .run("MATCH ()-[relation]->() WHERE elementId(relation) IN $addresses DELETE relation", Values.parameters("addresses", batch))
            .consume());
    var missing = projection.relationships().stream().filter(relationship -> !retained.contains(relationship.key())).toList();
    groups(missing, Neo4jRelationship::type).forEach((type, relationships) -> batches(relationships,
        batch -> transaction
            .run(
                "UNWIND $relationships AS item MATCH (source), (target) "
                    + "WHERE elementId(source) = item.source AND elementId(target) = item.target " + "CREATE (source)-[relation:`" + type
                    + "`]->(target) SET relation = item.properties",
                Values.parameters("relationships",
                    batch.stream()
                        .map(relationship -> Map.of("source", nodeAddresses.get(relationship.sourceKey()), "target",
                            nodeAddresses.get(relationship.targetKey()), PROPERTIES_FIELD, relationship.properties()))
                        .toList()))
            .consume()));
  }

  private static <T> Map<String, List<T>> groups(List<T> values, Function<T, String> classifier) {
    var groups = new LinkedHashMap<String, List<T>>();
    values.forEach(value -> groups.computeIfAbsent(classifier.apply(value), ignored -> new ArrayList<>()).add(value));
    return groups;
  }

  private static <T> void batches(List<T> values, Consumer<List<T>> action) {
    for (var first = 0; first < values.size(); first += Math.min(WRITE_BATCH_SIZE, values.size() - first)) {
      var end = first + Math.min(WRITE_BATCH_SIZE, values.size() - first);
      action.accept(values.subList(first, end));
    }
  }

  private record NodeChange(String address, Neo4jNode expected, List<String> extraLabels) {
  }
}
