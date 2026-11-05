package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.AstQuery;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;


import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.repository.internal.neo4j.Neo4jCloseObservation.Phase;
import org.aquilagraph.repository.internal.neo4j.StoredPublication.Namespace;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
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
    assertEquals(AnalysisCompletion.COMPLETE, run.completion(), () -> run.diagnostics().values().toString());
    var graph = run.representation().cfg().orElseThrow().methods().stream()
        .filter(analysis -> run.representation().ast().result().orElseThrow().method(analysis.methodKey()).orElseThrow().name().equals("execute")).findFirst().orElseThrow().graph().orElseThrow();
    var thrown = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.THROW).findFirst().orElseThrow();

    try {
      // Act
      try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(RepositoryOperationContext.saveCompletedRun(run.identity().key()))) {
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
    final StoredPublication replacementState;
    final StoredPublication independentState;
    var replacementWarning = new AnalysisDiagnostic("diagnostic:replacement-warning", "publication.replacement-warning",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "Replacement warning", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var replacementRun = AnalysisRun.create(primaryRun.identity(), primaryRun.representation(), List.of(replacementWarning));

    try {
      // Act
      var expectedPrimaryProjection = new Neo4jAnalysisRunMapper().map(primaryRun);
      try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(RepositoryOperationContext.saveCompletedRun(primaryRun.identity().key()))) {
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
      try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(RepositoryOperationContext.saveCompletedRun(replacementRun.identity().key()))) {
        repository.save(replacementRun);
      }
      try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        replacementState = storedProjection(verificationSession, primaryRun.identity().key());
        assertEquals(1, verificationSession.run("MATCH (:Diagnostic {runId: $runId, code: 'publication.replacement-warning'}) "
            + "RETURN count(*) AS count", Values.parameters("runId", primaryRun.identity().key())).single().get("count").asLong());
        assertEquals(independentState, storedProjection(verificationSession, independentRun.identity().key()));
      }
      try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(RepositoryOperationContext.saveCompletedRun(replacementRun.identity().key()))) {
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
                  failingDriver, sessionConfig, RepositoryOperationContext.saveCompletedRun(replacementRun.identity().key()), ignored -> invalidProjection)) {
        var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> failingRepository.save(replacementRun));
        assertInstanceOf(RepositoryException.class, failure.effectivePrimary());
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
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(warning, warning));

    try {
      // Act
      try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(RepositoryOperationContext.saveCompletedRun(run.identity().key()))) {
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
  void givenDistinctStoredRuns_whenReplacementCommitsBeforeCloseFails_thenKnownCommitAndReplacementSurvive()
      throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var mapper = new Neo4jAnalysisRunMapper();

    for (var phase : Phase.values()) {
      var sourceRoot = Files.createDirectories(temporaryDirectory.resolve(phase.name()));
      var original = completedRun(sourceRoot, "PublishedSample" + UUID.randomUUID().toString().replace("-", ""));
      var independentRoot = Files.createDirectory(sourceRoot.resolve("independent"));
      var independent = completedRun(independentRoot, "Independent" + UUID.randomUUID().toString().replace("-", ""));
      var previous = withPublicationWarning(original, "previous");
      var run = withPublicationWarning(original, "replacement");
      var expectedReplacement = expectedProjection(mapper.map(run));
      var runId = run.identity().key();
      var independentId = independent.identity().key();
      var context = RepositoryOperationContext.saveCompletedRun(runId);
      var events = new ArrayList<String>();
      var signal = new Neo4jException("Close outcome signal");
      try (var actualDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()))) {
        try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(context)) {
          repository.save(previous);
          repository.save(independent);
        }
        final StoredPublication establishedProjection;
        final StoredPublication independentProjection;
        try (var verificationSession = actualDriver.session(sessionConfig)) {
          establishedProjection = storedProjection(verificationSession, runId);
          independentProjection = storedProjection(verificationSession, independentId);
          assertEquals(expectedProjection(mapper.map(previous)), establishedProjection);
          assertEquals(expectedProjection(mapper.map(independent)), independentProjection);
          assertNotEquals(establishedProjection, expectedReplacement);
        }
        var delegatedDriver = Neo4jCloseObservation.signalPublicationClose(actualDriver, phase, signal, events);
        AnalysisRunRepositoryFactory publisher = operation -> new Neo4jAnalysisRunRepository(delegatedDriver, sessionConfig, operation, mapper::map);

        // Act
        var execution = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETED,
            AnalysisExecutionFixture::unexpectedAstRepository, publisher);

        // Assert
        var outcome = execution.publication();
        assertSame(run, execution.run());
        assertEquals(PublicationStatus.FAILED, outcome.status());
        assertEquals(CommitKnowledge.KNOWN_COMMIT, outcome.knowledge());
        assertEquals(new CompletedRun(runId), outcome.subjectIdentity().orElseThrow());
        var failure = outcome.failure().orElseThrow();
        assertEquals(context, failure.carrier().context());
        assertEquals(phase.repositoryPhase(),
            assertInstanceOf(Neo4jRepositoryOperationException.class, failure.carrier()).phase());
        assertSame(signal, failure.effectivePrimary().getCause());
        assertEquals(List.of("schema.close", "publication.write.returned", "publication.close", "driver.close"), events);
        try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
            var verificationSession = verificationDriver.session(sessionConfig)) {
          assertEquals(expectedReplacement, storedProjection(verificationSession, runId));
          assertEquals(independentProjection, storedProjection(verificationSession, independentId));
        }
      } finally {
        cleanup(settings, List.of(runId, independentId));
      }
    }
  }

  @Test
  void givenAstAndRunForTheSameSource_whenEachIsReplaced_thenTheOtherNamespacePreservesItsValues() throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var sourceRoot = Files.createDirectory(temporaryDirectory.resolve("original"));
    var original = completedRun(sourceRoot, "SharedSource");
    var originalAst = original.representation().ast();
    var originalResult = originalAst.result().orElseThrow();
    var identity = originalResult.identity();
    var replacementRoot = Files.createDirectory(temporaryDirectory.resolve("replacement"));
    var replacementAst = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
        InputSnapshot.fromSources(replacementRoot, originalResult.snapshot().sourceFiles()),
        originalAst.requestedConfiguration().orElseThrow(), originalAst.profile()));
    var replacementResult = replacementAst.result().orElseThrow();
    assertEquals(identity, replacementResult.identity());
    assertNotEquals(originalResult.snapshot(), replacementResult.snapshot());

    var previousRun = withPublicationWarning(original, "previous");
    var replacementRun = withPublicationWarning(original, "replacement");
    var runId = previousRun.identity().key();
    var mapper = new Neo4jAnalysisRunMapper();
    var expectedPrevious = expectedProjection(mapper.map(previousRun));
    var expectedReplacement = expectedProjection(mapper.map(replacementRun));
    assertNotEquals(expectedPrevious, expectedReplacement);
    var astRepositories = Neo4jRepositories.astResults(() -> settings);
    var retention = AnalysisCapabilities.astRetention(astRepositories);
    var query = AnalysisCapabilities.astQuery(astRepositories);
    var runRepositories = Neo4jRepositories.completedRuns(() -> settings);
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      try {
        assertEquals(PublicationStatus.SAVED, retention.retainAst(
            new AstPublicationRequest(originalAst, PublicationIntent.SAVE_COMPLETE_AST)).status());
        try (var repository = runRepositories.open(RepositoryOperationContext.saveCompletedRun(runId))) {
          repository.save(previousRun);
        }
        assertRetainedAst(query, originalAst);
        assertEquals(expectedPrevious, storedProjection(session, runId));

        // Act
        var astPublication = retention.retainAst(new AstPublicationRequest(replacementAst, PublicationIntent.SAVE_COMPLETE_AST));
        var runAfterAstReplacement = storedProjection(session, runId);
        assertEquals(PublicationStatus.SAVED, astPublication.status());
        assertRetainedAst(query, replacementAst);
        var runPublication = AnalysisExecutionFixture.executeComputed(replacementRun, PublicationIntent.SAVE_COMPLETED,
            AnalysisExecutionFixture::unexpectedAstRepository, runRepositories).publication();

        // Assert
        assertEquals(expectedPrevious, runAfterAstReplacement);
        assertEquals(PublicationStatus.SAVED, runPublication.status());
        assertEquals(expectedReplacement, storedProjection(session, runId));
        assertRetainedAst(query, replacementAst);
      } finally {
        Neo4jIntegration.discardAstFixture(session, identity.astResultKey());
      }
    } finally {
      cleanup(settings, List.of(runId));
    }
  }

  private static void assertRetainedAst(AstQuery query, AstOutcome expected) {
    var expectedResult = expected.result().orElseThrow();
    var lookup = query.findAst(expectedResult.identity());
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    var actual = lookup.outcome().orElseThrow().result().orElseThrow();
    assertEquals(expectedResult.snapshot(), actual.snapshot());
    assertArrayEquals(AstCodec.encode(expectedResult), AstCodec.encode(actual));
  }

  private static AnalysisRun withPublicationWarning(AnalysisRun run, String state) {
    var warning = new AnalysisDiagnostic("diagnostic:publication-" + state, "publication." + state, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "Publication warning: " + state, List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return AnalysisRun.create(run.identity(), run.representation(), List.of(warning));
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

  private static StoredPublication expectedProjection(Neo4jGraphProjection projection) {
    return Namespace.RUN.expected(projection.runId(), projection.nodes(), projection.relationships());
  }

  private static StoredPublication storedProjection(Session session, String runId) {
    var parameters = Values.parameters("scope", runId);
    return StoredPublication.fromRecords(session.run(Namespace.RUN.nodesQuery(), parameters).list(),
        session.run(Namespace.RUN.relationshipsQuery(), parameters).list());
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
