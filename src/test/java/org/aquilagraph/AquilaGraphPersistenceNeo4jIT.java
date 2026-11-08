package org.aquilagraph;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.repository.internal.neo4j.BoltCommitAcknowledgementProxy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class AquilaGraphPersistenceNeo4jIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenCommittedPublication_whenNativeOutputPipeIsClosed_thenStoredEffectSurvivesWithoutAnotherPublication() throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var root = Files.createDirectories(temporaryDirectory.resolve("committed-source"));
    var className = "ReportedArithmetic" + UUID.randomUUID().toString().replace("-", "");
    Files.writeString(root.resolve(className + ".java"),
        "class " + className + " { int value(int input) { return input + 13; } }");
    var expected = PackagedAnalysisSummary.expected(root, PublicationStatus.SAVED);
    try (var proxy = new BoltCommitAcknowledgementProxy(new InetSocketAddress(settings.uri().getHost(), settings.uri().getPort()));
        var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      var environment = new HashMap<String, String>(Neo4jIntegration.environment());
      environment.put("AQUILA_GRAPH_NEO4J_URI", proxy.uri().toString());
      try {
        // Act
        var result = new PackagedAnalyzerProcess(temporaryDirectory.resolve("process")).run(
            PackagedAnalyzerProcess.ClosedReader.OUTPUT, List.of(), environment, root, "--persist");

        // Assert
        assertTrue(result.completed(), result.error());
        assertEquals(4, result.exitCode(), result.error());
        assertEquals("", result.error());
        proxy.requireHealthyRelay();
        assertEquals(1, proxy.observation().beginMessages());
        assertEquals(1, proxy.observation().commitMessages());
        assertEquals(1, proxy.observation().forwardedCommitResponses());
        assertEquals(0, proxy.observation().withheldCommitSuccesses());
        assertEquals(List.of(Map.of("completion", "COMPLETE", "type", className, "literal", "13")), session.run("""
            MATCH (run:AnalysisRun {runId: $runId})-[:HAS_PROGRAM]->(program:Program)-[:DECLARES_TYPE]->(type:JavaType)
            MATCH (program)-[:HAS_METHOD_ANALYSIS]->(:MethodAnalysis)-[:HAS_CFG]->(graph:ControlFlowGraph)
            MATCH (graph)-[:HAS_NODE]->(:OperationNode {kind: 'BINARY', detail: 'PLUS'})
                  -[:USES_VALUE {position: 1}]->(literal:FlowValue {literal: '13'})
            RETURN run.completion AS completion, type.qualifiedName AS type, literal.literal AS literal
            """, Values.parameters("runId", expected.runId())).list(record -> record.asMap()));
      } finally {
        session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity",
            Values.parameters("runId", expected.runId())).consume();
      }
    }
  }

  @Test
  void givenCommunityDatabase_whenPackagedProcessesPersistDistinctPrograms_thenCommittedSemanticContentCanBeRead()
      throws IOException, InterruptedException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var runIds = new ArrayList<String>();
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      driver.verifyConnectivity();
      try {
        for (var literal : List.of("7", "9")) {
          var className = "StoredArithmetic" + UUID.randomUUID().toString().replace("-", "");
          var caseDirectory = Files.createDirectories(temporaryDirectory.resolve("literal-" + literal));
          var sourceRoot = Files.createDirectories(caseDirectory.resolve("sources"));
          Files.writeString(sourceRoot.resolve(className + ".java"),
              "class " + className + " { int value(int input) { return input + " + literal + "; } }");
          var expectedOutput = PackagedAnalysisSummary.expected(sourceRoot, PublicationStatus.SAVED);

          // Act
          var result = new PackagedAnalyzerProcess(caseDirectory).run(List.of(), Neo4jIntegration.environment(), sourceRoot, "--release", "25", "--persist");
          var runId = result.output().lines().filter(line -> line.startsWith("Run: \"")).findFirst().orElseThrow()
              .transform(line -> line.substring("Run: \"".length(), line.length() - 1));
          runIds.add(runId);

          // Assert
          assertTrue(result.completed(), result.error());
          assertEquals(0, result.exitCode(), result.error());
          assertEquals("", result.error());
          assertEquals(expectedOutput.output(), result.output());
          assertEquals(List.of(Map.of("completion", "COMPLETE", "type", className, "method", "value", "parameter", "input",
              "literal", literal, "literalType", "INT", "resultType", "INT")), session.run("""
              MATCH (run:AnalysisRun {runId: $runId})-[:HAS_PROGRAM]->(program:Program)
                    -[:DECLARES_TYPE]->(type:JavaType {qualifiedName: $className})
                    -[:DECLARES_METHOD]->(method:JavaMethod {name: 'value', hasBody: true})
              MATCH (method)-[:DECLARES_PARAMETER]->(parameter:JavaParameter {name: 'input'})
              MATCH (program)-[:HAS_METHOD_ANALYSIS]->(analysis:MethodAnalysis)-[:ANALYZES_METHOD]->(method)
              MATCH (analysis)-[:HAS_CFG]->(graph:ControlFlowGraph)-[:HAS_NODE]->(operation:OperationNode {kind: 'BINARY', detail: 'PLUS'})
              MATCH (operation)-[:USES_VALUE {position: 1}]->(literal:FlowValue {literal: $literal})
              MATCH (operation)-[:DEFINES_VALUE]->(sum:FlowValue)
              MATCH (graph)-[:HAS_VALUE]->(literal)
              MATCH (operation)-[:NORMAL_FLOW]->(:OperationNode {kind: 'RETURN'})
              RETURN run.completion AS completion, type.qualifiedName AS type, method.name AS method,
                     parameter.name AS parameter, literal.literal AS literal,
                     literal.staticTypeCategory AS literalType, sum.staticTypeCategory AS resultType
              """, Values.parameters("runId", runId, "className", className, "literal", literal)).list(record -> record.asMap()));
        }
        assertNotEquals(runIds.getFirst(), runIds.getLast());
        assertEquals(0, session.run("""
            MATCH (left:AnalysisEntity)-[relation]->(right:AnalysisEntity)
            WHERE left.runId IN $runIds AND left.runId <> right.runId
            RETURN count(relation) AS count
            """, Values.parameters("runIds", runIds)).single().get("count").asLong());
      } finally {
        for (var runId : runIds) {
          session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity", Values.parameters("runId", runId)).consume();
        }
      }
    }
  }
}
