package org.aquilagraph.repository.internal.neo4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;

/** Independent database observation of one publication and its incident relationships. */
record StoredPublication(Map<String, StoredPublication.Node> nodes, Map<String, StoredPublication.Relationship> relationships) {
  enum Namespace {
    AST("AstEntity", "astResultKey"),
    RUN("AnalysisEntity", "runId");

    private final String label;
    private final String scopeProperty;

    Namespace(String label, String scopeProperty) {
      this.label = label;
      this.scopeProperty = scopeProperty;
    }

    String nodesQuery() {
      return "MATCH (n:%s {%s: $scope}) RETURN n.key AS key, labels(n) AS labels, properties(n) AS properties"
          .formatted(label, scopeProperty);
    }

    String relationshipsQuery() {
      return """
          MATCH (source)-[relation]->(target)
          WHERE relation.%2$s = $scope
            OR (source:%1$s AND source.%2$s = $scope)
            OR (target:%1$s AND target.%2$s = $scope)
          RETURN relation.key AS key, type(relation) AS type, properties(relation) AS properties,
            source.key AS sourceKey, labels(source) AS sourceLabels, source.%2$s AS sourceScope,
            target.key AS targetKey, labels(target) AS targetLabels, target.%2$s AS targetScope
          """.formatted(label, scopeProperty);
    }

    StoredPublication expected(String scope, List<Neo4jNode> projectedNodes, List<Neo4jRelationship> projectedRelationships) {
      var nodes = projectedNodes.stream().collect(Collectors.toMap(Neo4jNode::key,
          node -> new Node(Set.of(label, node.label()), Values.value(node.properties()).asMap())));
      var relationships = projectedRelationships.stream().collect(Collectors.toMap(Neo4jRelationship::key,
          relation -> new Relationship(relation.type(), new Endpoint(relation.sourceKey(), nodes.get(relation.sourceKey()).labels(), scope),
              new Endpoint(relation.targetKey(), nodes.get(relation.targetKey()).labels(), scope),
              Values.value(relation.properties()).asMap())));
      return new StoredPublication(nodes, relationships);
    }
  }

  static StoredPublication fromRecords(List<Record> nodeRows, List<Record> relationshipRows) {
    var nodes = nodeRows.stream().collect(Collectors.toMap(row -> row.get("key").asString(),
        row -> new Node(labels(row, "labels"), row.get("properties").asMap())));
    var relationships = relationshipRows.stream().collect(Collectors.toMap(row -> row.get("key").asString(null),
        row -> new Relationship(row.get("type").asString(), endpoint(row, "source"), endpoint(row, "target"),
            row.get("properties").asMap())));
    return new StoredPublication(nodes, relationships);
  }

  private static Endpoint endpoint(Record row, String prefix) {
    return new Endpoint(row.get(prefix + "Key").asString(null), labels(row, prefix + "Labels"),
        row.get(prefix + "Scope").asString(null));
  }

  private static Set<String> labels(Record row, String field) {
    return Set.copyOf(row.get(field).asList(value -> value.asString()));
  }

  record Node(Set<String> labels, Map<String, Object> properties) {}

  record Endpoint(String key, Set<String> labels, String scope) {}

  record Relationship(String type, Endpoint source, Endpoint target, Map<String, Object> properties) {}
}
