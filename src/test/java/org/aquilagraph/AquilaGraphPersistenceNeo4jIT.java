package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

class AquilaGraphPersistenceNeo4jIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenCommunityDatabase_whenPackagedProcessesPersistDistinctPrograms_thenCommittedSemanticContentCanBeRead()
      throws IOException, InterruptedException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
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

          // Act
          var result = new PackagedAnalyzerProcess(caseDirectory).run(sourceRoot, "--release", "25", "--persist");
          var runId = result.output().lines().filter(line -> line.startsWith("Run: ")).map(line -> line.substring("Run: ".length()))
              .findFirst().orElseThrow(() -> new AssertionError("Missing run identity: " + result.output() + result.error()));
          runIds.add(runId);

          // Assert
          assertTrue(result.completed(), result.error());
          assertEquals(0, result.exitCode(), result.error());
          assertEquals("", result.error());
          assertEquals("Completion: COMPLETED", result.output().lines().toList().get(2));
          assertTrue(result.output().endsWith("Repository: SAVED" + System.lineSeparator()), result.output());
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
