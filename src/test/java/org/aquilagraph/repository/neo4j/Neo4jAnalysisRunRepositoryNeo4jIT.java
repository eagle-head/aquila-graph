package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.repository.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

class Neo4jAnalysisRunRepositoryNeo4jIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenRunningNeo4j_whenRunsArePersisted_thenStorageIsIdempotentIsolatedAndTransactional()
      throws IOException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var calculatorRun =
        analyze(Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize());
    Files.writeString(
        temporaryDirectory.resolve("Independent.java"),
        "class Independent { int value() { return 42; } }");
    var independentRun = analyze(temporaryDirectory);
    var sessionConfig = SessionConfig.forDatabase(settings.database());

    try {
      // Act
      var expectedCalculatorProjection = new Neo4jAnalysisRunMapper().map(calculatorRun);
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(calculatorRun);
        repository.save(calculatorRun);
        repository.save(independentRun);
      }

      // Assert
      try (var driver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session(sessionConfig)) {
        driver.verifyConnectivity();
        assertEquals(expectedCalculatorProjection.nodes().size(), nodeCount(session, calculatorRun.runId()));
        assertTrue(nodeCount(session, independentRun.runId()) > 0);
        assertEquals(
            0,
            session
                .run(
                    "MATCH (left:AnalysisEntity)-[relation]->(right:AnalysisEntity) "
                        + "WHERE left.runId <> right.runId RETURN count(relation) AS count")
                .single()
                .get("count")
                .asLong());
        assertTrue(
            session
                    .run(
                        "SHOW CONSTRAINTS YIELD name WHERE name IN "
                            + "['analysis_entity_identity', 'analysis_run_identity'] "
                            + "RETURN count(*) AS count")
                    .single()
                    .get("count")
                    .asLong()
                == 2);
        var flowTypes =
            session
                .run(
                    "MATCH (:AnalysisEntity {runId: $runId})-[relation]->() "
                        + "WHERE relation.runId = $runId AND type(relation) ENDS WITH '_FLOW' "
                        + "RETURN collect(DISTINCT type(relation)) AS types",
                    Values.parameters("runId", calculatorRun.runId()))
                .single()
                .get("types")
                .asList(value -> value.asString());
        assertTrue(flowTypes.contains("EXCEPTIONAL_FLOW"));
        assertTrue(flowTypes.contains("SWITCH_FLOW"));
        assertTrue(
            session
                    .run(
                        "MATCH (:FlowValue {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", calculatorRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (operation:OperationNode {runId: $runId}) "
                            + "WHERE operation.kind IS NOT NULL RETURN count(*) AS count",
                        Values.parameters("runId", calculatorRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertEquals(
            0,
            session
                .run(
                    "MATCH (entity:AnalysisEntity {runId: $runId}) "
                        + "WHERE any(label IN labels(entity) WHERE label STARTS WITH 'Normalized') "
                        + "RETURN count(entity) AS count",
                    Values.parameters("runId", calculatorRun.runId()))
                .single()
                .get("count")
                .asLong());
        assertTrue(
            session
                    .run(
                        "MATCH (:SourceOrigin {runId: $runId})-[:IN_SOURCE]->"
                            + "(:SourceFile {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", calculatorRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
      }

      var invalidProjection = invalidProjection(calculatorRun);
      try (var failingDriver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var failingRepository =
              new Neo4jAnalysisRunRepository(
                  failingDriver, sessionConfig, ignored -> invalidProjection)) {
        assertThrows(RepositoryException.class, () -> failingRepository.save(calculatorRun));
      }
      try (var verificationDriver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        assertEquals(expectedCalculatorProjection.nodes().size(), nodeCount(verificationSession, calculatorRun.runId()));
      }
    } finally {
      cleanup(settings, List.of(calculatorRun.runId(), independentRun.runId()));
    }
  }

  private static AnalysisRun analyze(Path sourceRoot) throws IOException {
    return new AnalysisEngine()
        .analyze(
            new JavaFrontend(new JavaSourceLoader())
                .analyze(sourceRoot, CompilerConfiguration.java25()));
  }

  private static Neo4jGraphProjection invalidProjection(AnalysisRun run) {
    var valid = new Neo4jAnalysisRunMapper().map(run);
    var nodes = new ArrayList<>(valid.nodes());
    var first = nodes.getFirst();
    var invalidProperties = new LinkedHashMap<>(first.properties());
    invalidProperties.put("invalidNestedMap", java.util.Map.of("not", "a Neo4j property"));
    nodes.set(0, new Neo4jNode(first.key(), first.label(), invalidProperties));
    return new Neo4jGraphProjection(valid.runId(), nodes, valid.relationships());
  }

  private static long nodeCount(org.neo4j.driver.Session session, String runId) {
    return session
        .run(
            "MATCH (node:AnalysisEntity {runId: $runId}) RETURN count(node) AS count",
            Values.parameters("runId", runId))
        .single()
        .get("count")
        .asLong();
  }

  private static void cleanup(Neo4jConnectionSettings settings, List<String> runIds) {
    try (var driver =
            GraphDatabase.driver(
                settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      session
          .run(
              "MATCH (entity:AnalysisEntity) WHERE entity.runId IN $runIds DETACH DELETE entity",
              Values.parameters("runIds", runIds))
          .consume();
    }
  }
}
