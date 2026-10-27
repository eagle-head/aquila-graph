package org.aquilagraph.repository.internal.neo4j;



import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.ValueKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jEnumContextNeo4jIT {
  @TempDir
  Path sourceRoot;

  @Test
  void givenActualEnumConstructorAnalysis_whenPublishedToNeo4j_thenStoredContextAndInvocationOperandsRetainOwnershipTypeAndOrder()
      throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var suffix = UUID.randomUUID().toString().replace("-", "");
    var delegated = "Delegated" + suffix;
    var source = SourceFile.fromContent(Path.of("Enums.java"), "enum Empty" + suffix + " { ; } enum " + delegated
        + " { ; " + delegated + "() { this(7); } " + delegated + "(int value) {} }");
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(InputSnapshot.fromSources(sourceRoot, List.of(source)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var run = new SourceAnalysisFixture().analyze(input);
    assertEquals(AnalysisCompletion.COMPLETE, run.completion(), () -> run.diagnostics().toString());
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      try {
        // Act
        try (var repository = new Neo4jAnalysisRunRepository(settings)) {
          repository.save(run);
        }

        // Assert
        assertEquals(6, session.run("MATCH (value:FlowValue {runId: $runId, kind: 'IMPLICIT_PARAMETER'}) RETURN count(value) AS count",
            Values.parameters("runId", run.identity().key())).single().get("count").asInt());
        assertEquals(1, session.run("MATCH (parameter:JavaParameter {runId: $runId}) RETURN count(parameter) AS count",
            Values.parameters("runId", run.identity().key())).single().get("count").asInt());
        for (var analysis : run.representation().cfg().orElseThrow().methods()) {
          var graph = analysis.graph().orElseThrow();
          for (var value : graph.values().stream().filter(candidate -> candidate.kind() == ValueKind.IMPLICIT_PARAMETER).toList()) {
            var stored = session.run("MATCH (graph:ControlFlowGraph {runId: $runId, key: $graphId})-[ownership:HAS_VALUE]->"
                + "(value:FlowValue {runId: $runId, key: $valueId}) RETURN value, ownership.position AS position",
                Values.parameters("runId", run.identity().key(), "graphId", graph.graphId(), "valueId", value.valueId())).single();
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
                Values.parameters("runId", run.identity().key(), "valueId", value.valueId())).single().get("count").asInt());
          }
          var invocation = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
          var operands = session.run("MATCH (operation:OperationNode {runId: $runId, key: $operationId})-[use:USES_VALUE]->"
              + "(value:FlowValue {runId: $runId}) RETURN use.position AS position, value.key AS key ORDER BY position",
              Values.parameters("runId", run.identity().key(), "operationId", invocation.nodeId())).list();
          assertEquals(invocation.operands().stream().map(operand -> operand.value().valueId()).toList(),
              operands.stream().map(record -> record.get("key").asString()).toList());
          assertEquals(invocation.operands().stream().map(operand -> operand.position()).toList(),
              operands.stream().map(record -> record.get("position").asInt()).toList());
        }
      } finally {
        session.run("MATCH (node:AnalysisEntity {runId: $runId}) DETACH DELETE node", Values.parameters("runId", run.identity().key())).consume();
      }
    }
  }
}
