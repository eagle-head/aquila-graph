package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.ValueKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

class Neo4jEnumContextNeo4jIT {
  @TempDir
  Path sourceRoot;

  @Test
  void givenActualEnumConstructorAnalysis_whenPublishedToNeo4j_thenStoredContextAndInvocationOperandsRetainOwnershipTypeAndOrder()
      throws IOException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var suffix = UUID.randomUUID().toString().replace("-", "");
    var delegated = "Delegated" + suffix;
    var source = SourceFile.fromContent(Path.of("Enums.java"), "enum Empty" + suffix + " { ; } enum " + delegated
        + " { ; " + delegated + "() { this(7); } " + delegated + "(int value) {} }");
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(InputSnapshot.fromSources(sourceRoot, List.of(source)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = new AnalysisEngine().analyze(input);
    assertEquals(AnalysisCompletion.COMPLETED, run.completion(), () -> run.diagnostics().toString());
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      try {
        // Act
        try (var repository = new Neo4jAnalysisRunRepository(settings)) {
          repository.save(run);
        }

        // Assert
        assertEquals(6, session.run("MATCH (value:FlowValue {runId: $runId, kind: 'IMPLICIT_PARAMETER'}) RETURN count(value) AS count",
            Values.parameters("runId", run.runId())).single().get("count").asInt());
        assertEquals(1, session.run("MATCH (parameter:JavaParameter {runId: $runId}) RETURN count(parameter) AS count",
            Values.parameters("runId", run.runId())).single().get("count").asInt());
        for (var analysis : run.representation().orElseThrow().methodAnalyses()) {
          var graph = analysis.controlFlowGraph();
          for (var value : graph.values().stream().filter(candidate -> candidate.kind() == ValueKind.IMPLICIT_PARAMETER).toList()) {
            var stored = session.run("MATCH (graph:ControlFlowGraph {runId: $runId, key: $graphId})-[ownership:HAS_VALUE]->"
                + "(value:FlowValue {runId: $runId, key: $valueId}) RETURN value, ownership.position AS position",
                Values.parameters("runId", run.runId(), "graphId", graph.graphId(), "valueId", value.valueId())).single();
            var node = stored.get("value");
            assertEquals("IMPLICIT_PARAMETER", node.get("kind").asString());
            assertEquals(graph.graphId(), node.get("graphId").asString());
            assertEquals(value.displayName().orElseThrow(), node.get("displayName").asString());
            assertEquals(value.staticType().orElseThrow().erasedName(), node.get("staticTypeErasure").asString());
            assertEquals(value.staticType().orElseThrow().category().name(), node.get("staticTypeCategory").asString());
            assertTrue(node.get("literal").isNull());
            assertEquals(graph.values().indexOf(value), stored.get("position").asInt());
            assertEquals(0, session.run("MATCH (value:FlowValue {runId: $runId, key: $valueId}) "
                + "OPTIONAL MATCH (producer)-[:DEFINES_VALUE]->(value) RETURN count(producer) AS count",
                Values.parameters("runId", run.runId(), "valueId", value.valueId())).single().get("count").asInt());
          }
          var invocation = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
          var operands = session.run("MATCH (operation:OperationNode {runId: $runId, key: $operationId})-[use:USES_VALUE]->"
              + "(value:FlowValue {runId: $runId}) RETURN use.position AS position, value.key AS key ORDER BY position",
              Values.parameters("runId", run.runId(), "operationId", invocation.nodeId())).list();
          assertEquals(invocation.operands().stream().map(operand -> operand.value().valueId()).toList(),
              operands.stream().map(record -> record.get("key").asString()).toList());
          assertEquals(invocation.operands().stream().map(operand -> operand.position()).toList(),
              operands.stream().map(record -> record.get("position").asInt()).toList());
        }
      } finally {
        session.run("MATCH (node:AnalysisEntity {runId: $runId}) DETACH DELETE node", Values.parameters("runId", run.runId())).consume();
      }
    }
  }
}
