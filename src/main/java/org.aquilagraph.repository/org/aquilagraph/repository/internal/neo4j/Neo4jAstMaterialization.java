package org.aquilagraph.repository.internal.neo4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.RelationshipDecision;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.RemoveNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.ReplaceNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.RetainNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.StoredNode;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstReconciliation.StoredRelationship;
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

  private Neo4jAstMaterialization(TransactionContext transaction, Neo4jAstProjection projection) {
    this.transaction = transaction;
    this.projection = projection;
    scope = projection.identity().astResultKey();
  }

  static void replace(TransactionContext transaction, Neo4jAstProjection projection) {
    new Neo4jAstMaterialization(transaction, projection).replace();
  }

  private void replace() {
    transaction.run("MERGE (subject:AstEntity {astResultKey: $scope, key: $scope}) SET subject.key = subject.key",
        Values.parameters("scope", scope)).consume();
    var reconciliation = new Neo4jAstReconciliation(projection);
    reconcileNodes(reconciliation);
    reconcileRelationships(reconciliation);
  }

  private void reconcileNodes(Neo4jAstReconciliation reconciliation) {
    var obsolete = new ArrayList<String>();
    var changed = new ArrayList<ReplaceNode>();
    try (var records = transaction
        .run("MATCH (entity:AstEntity {astResultKey: $scope}) "
            + "RETURN elementId(entity) AS address, labels(entity) AS labels, properties(entity) AS properties, "
            + "CASE WHEN entity.key IS :: STRING NOT NULL THEN entity.key ELSE null END AS key", Values.parameters("scope", scope))
        .stream()) {
      records.forEach(record -> {
        var stored = new StoredNode(record.get(ADDRESS_FIELD).asString(), record.get("key").asString(null),
            record.get("labels").asList(value -> value.asString()), record.get(PROPERTIES_FIELD).asMap());
        switch (reconciliation.observeNode(stored)) {
          case RetainNode _ -> {
            // The address is already available for relationship reconciliation.
          }
          case RemoveNode removal -> obsolete.add(removal.address());
          case ReplaceNode replacement -> changed.add(replacement);
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
    groups(reconciliation.missingNodes(), Neo4jNode::label).forEach((label, nodes) -> batches(nodes, batch -> {
      try (var records = transaction.run(
          "UNWIND $nodes AS expected CREATE (entity:AstEntity:`" + label + "`) SET entity = expected "
              + "RETURN entity.key AS key, elementId(entity) AS address",
          Values.parameters("nodes", batch.stream().map(Neo4jNode::properties).toList())).stream()) {
        records.forEach(record -> reconciliation.recordInsertedNode(record.get("key").asString(), record.get(ADDRESS_FIELD).asString()));
      }
    }));
  }

  private void reconcileRelationships(Neo4jAstReconciliation reconciliation) {
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
        var stored = new StoredRelationship(record.get(ADDRESS_FIELD).asString(), record.get("key").asString(null),
            record.get("type").asString(), record.get("source").asString(), record.get("target").asString(),
            record.get(PROPERTIES_FIELD).asMap());
        if (reconciliation.observeRelationship(stored) == RelationshipDecision.REMOVE) {
          obsolete.add(stored.address());
        }
      });
    }
    batches(obsolete,
        batch -> transaction
            .run("MATCH ()-[relation]->() WHERE elementId(relation) IN $addresses DELETE relation", Values.parameters("addresses", batch))
            .consume());
    groups(reconciliation.missingRelationships(), Neo4jRelationship::type).forEach((type, relationships) -> batches(relationships,
        batch -> transaction
            .run(
                "UNWIND $relationships AS item MATCH (source), (target) "
                    + "WHERE elementId(source) = item.source AND elementId(target) = item.target " + "CREATE (source)-[relation:`" + type
                    + "`]->(target) SET relation = item.properties",
                Values.parameters("relationships",
                    batch.stream()
                        .map(relationship -> Map.of("source", reconciliation.nodeAddress(relationship.sourceKey()), "target",
                            reconciliation.nodeAddress(relationship.targetKey()), PROPERTIES_FIELD, relationship.properties()))
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

}
