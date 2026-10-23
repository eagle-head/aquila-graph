package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;


import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CompletedRunPublicationService;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

@ExtendWith(Neo4jIntegration.class)
class Neo4jAnalysisRunRepositoryNeo4jIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenNullableExplicitThrow_whenCompilerRunIsPublished_thenStoredCategoriesRetainNonNullBeforeNull() throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("nullable-throw"));
    Files.writeString(sourceRoot.resolve("Example.java"),
        "class Example { void execute(Exception failure) throws Exception { throw failure; } }");
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var run = new SourceAnalysisFixture().analyze(input);
    assertEquals(AnalysisCompletion.COMPLETE, run.completion(), () -> run.diagnostics().toString());
    var graph = run.representation().cfg().orElseThrow().methods().stream()
        .filter(analysis -> run.representation().ast().result().orElseThrow().method(analysis.methodKey()).orElseThrow().name().equals("execute")).findFirst().orElseThrow().graph().orElseThrow();
    var thrown = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.THROW).findFirst().orElseThrow();

    try {
      // Act
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(run);
      }

      // Assert
      try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
        assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), session.run(
            "MATCH (operation:OperationNode {runId: $runId, key: $key}) RETURN operation.possibleExceptions AS categories",
            Values.parameters("runId", run.identity().key(), "key", thrown.nodeId())).single().get("categories").asList(value -> value.asString()));
      }
      assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), thrown.possibleExceptions());
    } finally {
      cleanup(settings, List.of(run.identity().key()));
    }
  }

  @Test
  void givenRunningNeo4j_whenRunsArePersisted_thenStorageIsIdempotentIsolatedAndTransactional()
      throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var primarySourceRoot = Files.createDirectories(temporaryDirectory.resolve("primary"));
    var primaryRun = completedRun(primarySourceRoot, "PersistentSample" + UUID.randomUUID().toString().replace("-", ""));
    var independentSourceRoot = Files.createDirectories(temporaryDirectory.resolve("independent"));
    var independentRun = completedRun(independentSourceRoot, "Independent" + UUID.randomUUID().toString().replace("-", ""));
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    final StoredProjection replacementState;
    final StoredProjection independentState;
    var replacementWarning = new AnalysisDiagnostic("diagnostic:replacement-warning", "publication.replacement-warning",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "Replacement warning", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var replacementRun = new AnalysisRun(primaryRun.identity(), primaryRun.representation(), primaryRun.completion(), List.of(replacementWarning));

    try {
      // Act
      var expectedPrimaryProjection = new Neo4jAnalysisRunMapper().map(primaryRun);
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(primaryRun);
        repository.save(primaryRun);
        repository.save(independentRun);
      }

      // Assert
      try (var driver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session(sessionConfig)) {
        driver.verifyConnectivity();
        assertEquals(expectedPrimaryProjection.nodes().size(), nodeCount(session, primaryRun.identity().key()));
        assertTrue(nodeCount(session, independentRun.identity().key()) > 0);
        assertEquals(
            0,
            session
                .run(
                    "MATCH (left:AnalysisEntity)-[relation]->(right:AnalysisEntity) "
                        + "WHERE left.runId <> right.runId RETURN count(relation) AS count")
                .single()
                .get("count")
                .asLong());
        assertEquals(List.of(
            Map.of("name", "analysis_entity_identity", "type", "NODE_PROPERTY_UNIQUENESS", "entityType", "NODE", "labelsOrTypes",
                List.of("AnalysisEntity"), "properties", List.of("runId", "key")),
            Map.of("name", "analysis_run_identity", "type", "NODE_PROPERTY_UNIQUENESS", "entityType", "NODE", "labelsOrTypes",
                List.of("AnalysisRun"), "properties", List.of("runId"))),
            session.run("SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties WHERE name IN "
                + "['analysis_entity_identity', 'analysis_run_identity'] "
                + "RETURN name, type, entityType, labelsOrTypes, properties ORDER BY name").list(record -> record.asMap()));
        var flowTypes =
            session
                .run(
                    "MATCH (:AnalysisEntity {runId: $runId})-[relation]->() "
                        + "WHERE relation.runId = $runId AND type(relation) ENDS WITH '_FLOW' "
                        + "RETURN collect(DISTINCT type(relation)) AS types",
                    Values.parameters("runId", primaryRun.identity().key()))
                .single()
                .get("types")
                .asList(value -> value.asString());
        assertTrue(flowTypes.contains("EXCEPTIONAL_FLOW"));
        assertTrue(flowTypes.contains("SWITCH_FLOW"));
        assertTrue(
            session
                    .run(
                        "MATCH (:FlowValue {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (value:FlowValue {runId: $runId}) "
                            + "WHERE value.staticTypeKey IS NOT NULL "
                            + "AND value.staticTypeErasure IS NOT NULL "
                            + "AND value.staticTypeCategory IS NOT NULL "
                            + "RETURN count(value) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (parameter:JavaParameter {runId: $runId}) "
                            + "WHERE parameter.staticTypeKey IS NOT NULL "
                            + "AND parameter.staticTypeErasure IS NOT NULL "
                            + "AND parameter.staticTypeCategory IS NOT NULL "
                            + "RETURN count(parameter) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (:JavaType {runId: $runId})-"
                            + "[declaration:DECLARES_FIELD]->(field:JavaField {runId: $runId}) "
                            + "WHERE declaration.runId = $runId "
                            + "AND field.staticTypeKey IS NOT NULL "
                            + "AND field.staticTypeErasure IS NOT NULL "
                            + "AND field.staticTypeCategory IS NOT NULL "
                            + "RETURN count(field) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (operation:OperationNode {runId: $runId}) "
                            + "WHERE operation.kind IS NOT NULL RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertEquals(
            0,
            session
                .run(
                    "MATCH (entity:AnalysisEntity {runId: $runId}) "
                        + "WHERE any(label IN labels(entity) WHERE label IN ['MethodBody', 'SyntaxNode']) "
                        + "RETURN count(entity) AS count",
                    Values.parameters("runId", primaryRun.identity().key()))
                .single()
                .get("count")
                .asLong());
        assertEquals(
            0,
            session
                .run(
                    "MATCH (:AnalysisEntity {runId: $runId})-[relation]->() "
                        + "WHERE type(relation) IN ['HAS_BODY', 'HAS_SYNTAX_ROOT', 'CHILD'] "
                        + "RETURN count(relation) AS count",
                    Values.parameters("runId", primaryRun.identity().key()))
                .single()
                .get("count")
                .asLong());
        assertTrue(
            session
                    .run(
                        "MATCH (method:JavaMethod {runId: $runId}) "
                            + "WHERE method.hasBody = true RETURN count(method) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (operation:OperationNode {runId: $runId})-[:HAS_REFERENCE|HAS_ORIGIN]->() "
                            + "RETURN count(operation) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (:SourceOrigin {runId: $runId})-[:IN_SOURCE]->"
                            + "(:SourceFile {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.identity().key()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
      }

      try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        independentState = storedProjection(verificationSession, independentRun.identity().key());
      }
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(replacementRun);
      }
      try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        replacementState = storedProjection(verificationSession, primaryRun.identity().key());
        assertEquals(1, verificationSession.run("MATCH (:Diagnostic {runId: $runId, code: 'publication.replacement-warning'}) "
            + "RETURN count(*) AS count", Values.parameters("runId", primaryRun.identity().key())).single().get("count").asLong());
        assertEquals(independentState, storedProjection(verificationSession, independentRun.identity().key()));
      }
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(replacementRun);
      }
      try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        assertEquals(replacementState, storedProjection(verificationSession, primaryRun.identity().key()));
        assertEquals(independentState, storedProjection(verificationSession, independentRun.identity().key()));
      }

      var invalidProjection = constraintConflictProjection(replacementRun);
      try (var failingDriver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var failingRepository =
              new Neo4jAnalysisRunRepository(
                  failingDriver, sessionConfig, ignored -> invalidProjection)) {
        assertThrows(RepositoryException.class, () -> failingRepository.save(replacementRun));
      }
      try (var verificationDriver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        assertEquals(replacementState, storedProjection(verificationSession, primaryRun.identity().key()));
        assertEquals(independentState, storedProjection(verificationSession, independentRun.identity().key()));
      }
    } finally {
      cleanup(settings, List.of(primaryRun.identity().key(), independentRun.identity().key()));
    }
  }

  @Test
  void givenEqualDiagnosticValuesAtTwoSlots_whenRunIsPublishedToNeo4j_thenOneDiagnosticAndOriginRetainBothPositions()
      throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var original = completedRun(Files.createDirectories(temporaryDirectory.resolve("diagnostics")),
        "DiagnosticSample" + UUID.randomUUID().toString().replace("-", ""));
    var origin = original.representation().ast().result().orElseThrow().program().types().getFirst().origins().getFirst();
    var warning = new AnalysisDiagnostic("diagnostic:publication-warning", "publication.warning", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "Publication warning", List.of(origin), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var run = new AnalysisRun(original.identity(), original.representation(), original.completion(), List.of(warning, warning));

    try {
      // Act
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(run);
      }

      // Assert
      try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
        assertEquals(1, session.run("MATCH (d:Diagnostic {runId: $runId}) RETURN count(d) AS count",
            Values.parameters("runId", run.identity().key())).single().get("count").asLong());
        assertEquals(Map.of("runId", run.identity().key(), "key", warning.diagnosticKey(), "code", "publication.warning",
            "stage", "FRONTEND", "severity", "WARNING", "message", "Publication warning"),
            session.run("MATCH (d:Diagnostic {runId: $runId}) RETURN properties(d) AS properties",
                Values.parameters("runId", run.identity().key())).single().get("properties").asMap());
        assertEquals(List.of(0L, 1L), session.run("MATCH (:AnalysisRun {runId: $runId})-[slot:HAS_DIAGNOSTIC]->"
            + "(d:Diagnostic {runId: $runId}) RETURN slot.position AS position ORDER BY position",
            Values.parameters("runId", run.identity().key())).list(record -> record.get("position").asLong()));
        assertEquals(1, session.run("MATCH (:Diagnostic {runId: $runId})-[origin:HAS_ORIGIN]->"
            + "(:SourceOrigin {runId: $runId}) RETURN count(origin) AS count",
            Values.parameters("runId", run.identity().key())).single().get("count").asLong());
        var storedOrigin = session.run("MATCH (:Diagnostic {runId: $runId})-[:HAS_ORIGIN]->"
            + "(origin:SourceOrigin {runId: $runId})-[:IN_SOURCE]->(source:SourceFile) "
            + "RETURN origin.startOffset AS start, origin.endOffsetExclusive AS end, origin.position AS position, source.key AS source",
            Values.parameters("runId", run.identity().key())).single();
        assertEquals(origin.startOffset(), storedOrigin.get("start").asLong());
        assertEquals(origin.endOffsetExclusive(), storedOrigin.get("end").asLong());
        assertEquals(0L, storedOrigin.get("position").asLong());
        assertEquals(origin.sourceFileKey(), storedOrigin.get("source").asString());
      }
    } finally {
      cleanup(settings, List.of(run.identity().key()));
    }
  }

  @Test
  void givenAcknowledgedNeo4jCommit_whenLaterCloseSignalsFailure_thenApplicationRetainsRunAndStoredProjection()
      throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var phases = List.of(CloseFailurePhase.PUBLICATION_SESSION, CloseFailurePhase.DRIVER);

    for (var phase : phases) {
      var sourceRoot = Files.createDirectories(temporaryDirectory.resolve(phase.name()));
      var run = completedRun(sourceRoot, "PublishedSample" + UUID.randomUUID().toString().replace("-", ""));
      var projection = new Neo4jAnalysisRunMapper().map(run);
      var events = new ArrayList<String>();
      var signal = new Neo4jException("Close outcome signal");
      try (var actualDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()))) {
        try (var repository = new Neo4jAnalysisRunRepository(settings)) {
          repository.save(run);
        }
        final StoredProjection establishedProjection;
        try (var verificationSession = actualDriver.session(sessionConfig)) {
          establishedProjection = storedProjection(verificationSession, run.identity().key());
        }
        var delegatedDriver = closeSignalingDriver(actualDriver, phase, signal, events);
        var publisher = new CompletedRunPublicationService(
            () -> new Neo4jAnalysisRunRepository(delegatedDriver, sessionConfig, ignored -> projection));

        // Act
        var failure = assertThrows(AnalysisRepositoryException.class, () -> publisher.publish(run));

        // Assert
        assertSame(run, failure.analysisRun());
        assertSame(signal, failure.repositoryFailure().getCause());
        assertEquals(List.of("schema.close", "publication.write.returned", "publication.close", "driver.close"), events);
        try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
            var verificationSession = verificationDriver.session(sessionConfig)) {
          assertEquals(projection.nodes().size(), nodeCount(verificationSession, run.identity().key()));
          assertEquals(projection.relationships().size(), verificationSession.run(
              "MATCH (:AnalysisEntity {runId: $runId})-[relation]->() WHERE relation.runId = $runId RETURN count(relation) AS count",
              Values.parameters("runId", run.identity().key())).single().get("count").asLong());
          assertEquals(establishedProjection, storedProjection(verificationSession, run.identity().key()));
        }
      } finally {
        cleanup(settings, List.of(run.identity().key()));
      }
    }
  }

  private enum CloseFailurePhase {
    PUBLICATION_SESSION, DRIVER
  }

  private static Driver closeSignalingDriver(Driver actual, CloseFailurePhase phase, Neo4jException signal, List<String> events) {
    var sessionCount = new AtomicInteger();
    return (Driver) Proxy.newProxyInstance(Driver.class.getClassLoader(), new Class<?>[] {Driver.class}, (proxy, method, arguments) -> {
      if (method.getName().equals("session")) {
        var session = (Session) invoke(actual, method, arguments);
        var publication = sessionCount.incrementAndGet() == 2;
        return Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[] {Session.class},
            (sessionProxy, sessionMethod, sessionArguments) -> {
              var result = invoke(session, sessionMethod, sessionArguments);
              if (sessionMethod.getName().equals("executeWrite")) {
                events.add("publication.write.returned");
              }
              if (sessionMethod.getName().equals("close")) {
                events.add(publication ? "publication.close" : "schema.close");
                if (publication && phase == CloseFailurePhase.PUBLICATION_SESSION) {
                  throw signal;
                }
              }
              return result;
            });
      }
      var result = invoke(actual, method, arguments);
      if (method.getName().equals("close")) {
        events.add("driver.close");
        if (phase == CloseFailurePhase.DRIVER) {
          throw signal;
        }
      }
      return result;
    });
  }

  private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  private static AnalysisRun completedRun(Path sourceRoot, String typeName) throws IOException {
    var source = """
        class %s {
          int field;
          int evaluate(int input, int divisor) {
            return switch (input) {
              case 0 -> field;
              default -> input / divisor;
            };
          }
        }
        """.formatted(typeName);
    Files.writeString(sourceRoot.resolve(typeName + ".java"), source);
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    return new SourceAnalysisFixture().analyze(new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow());
  }

  private static Neo4jGraphProjection constraintConflictProjection(AnalysisRun run) {
    var valid = new Neo4jAnalysisRunMapper().map(run);
    var nodes = new ArrayList<>(valid.nodes());
    var duplicateRunKey = run.identity().key() + "-duplicate";
    nodes.add(new Neo4jNode(duplicateRunKey, "AnalysisRun",
        Map.of("runId", run.identity().key(), "key", duplicateRunKey, "completion", "COMPLETE")));
    return new Neo4jGraphProjection(valid.runId(), nodes, valid.relationships());
  }

  private static long nodeCount(Session session, String runId) {
    return session
        .run(
            "MATCH (node:AnalysisEntity {runId: $runId}) RETURN count(node) AS count",
            Values.parameters("runId", runId))
        .single()
        .get("count")
        .asLong();
  }

  private record StoredProjection(List<Map<String, Object>> nodes, List<Map<String, Object>> relationships) {
  }

  private static StoredProjection storedProjection(Session session, String runId) {
    var nodes = session.run("MATCH (n:AnalysisEntity {runId: $runId}) "
        + "RETURN labels(n) AS labels, properties(n) AS properties ORDER BY n.key",
        Values.parameters("runId", runId)).list(record -> record.asMap());
    var relationships = session.run("MATCH (source:AnalysisEntity {runId: $runId})-[relation]->(target:AnalysisEntity) "
        + "WHERE relation.runId = $runId RETURN source.key AS sourceKey, target.key AS targetKey, type(relation) AS type, "
        + "properties(relation) AS properties ORDER BY relation.key", Values.parameters("runId", runId)).list(record -> record.asMap());
    return new StoredProjection(nodes, relationships);
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
