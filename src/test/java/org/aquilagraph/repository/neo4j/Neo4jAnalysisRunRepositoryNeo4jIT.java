package org.aquilagraph.repository.neo4j;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.RepositorySavePolicy;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CfgEdge;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.JavaField;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.OperationOperand;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.SoftwareVersion;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jAnalysisRunRepositoryNeo4jIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenNullableExplicitThrow_whenCompilerRunIsPublished_thenStoredCategoriesRetainNonNullBeforeNull() throws IOException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("nullable-throw"));
    Files.writeString(sourceRoot.resolve("Example.java"),
        "class Example { void execute(Exception failure) throws Exception { throw failure; } }");
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = new AnalysisEngine().analyze(input);
    assertEquals(AnalysisCompletion.COMPLETED, run.completion(), () -> run.diagnostics().toString());
    var graph = run.representation().orElseThrow().methodAnalyses().stream()
        .filter(analysis -> analysis.sourceMethod().name().equals("execute")).findFirst().orElseThrow().controlFlowGraph();
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
            Values.parameters("runId", run.runId(), "key", thrown.nodeId())).single().get("categories").asList(value -> value.asString()));
      }
      assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), thrown.possibleExceptions());
    } finally {
      cleanup(settings, List.of(run.runId()));
    }
  }

  @Test
  void givenRunningNeo4j_whenRunsArePersisted_thenStorageIsIdempotentIsolatedAndTransactional()
      throws IOException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var primarySourceRoot = Files.createDirectories(temporaryDirectory.resolve("primary"));
    var primaryRun = completedRun(primarySourceRoot, "PersistentSample" + UUID.randomUUID().toString().replace("-", ""));
    var independentSourceRoot = Files.createDirectories(temporaryDirectory.resolve("independent"));
    var independentRun = completedRun(independentSourceRoot, "Independent" + UUID.randomUUID().toString().replace("-", ""));
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    final StoredProjection replacementState;
    final StoredProjection independentState;
    var replacementWarning = new AnalysisDiagnostic("diagnostic:replacement-warning", "publication.replacement-warning",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "Replacement warning", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var replacementRun = new AnalysisRun(primaryRun.runId(), primaryRun.inputSnapshot(), primaryRun.environmentSnapshot(),
        primaryRun.representation(), List.of(replacementWarning), primaryRun.completion());

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
        assertEquals(expectedPrimaryProjection.nodes().size(), nodeCount(session, primaryRun.runId()));
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
                    Values.parameters("runId", primaryRun.runId()))
                .single()
                .get("types")
                .asList(value -> value.asString());
        assertTrue(flowTypes.contains("EXCEPTIONAL_FLOW"));
        assertTrue(flowTypes.contains("SWITCH_FLOW"));
        assertTrue(
            session
                    .run(
                        "MATCH (:FlowValue {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.runId()))
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
                        Values.parameters("runId", primaryRun.runId()))
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
                        Values.parameters("runId", primaryRun.runId()))
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
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (operation:OperationNode {runId: $runId}) "
                            + "WHERE operation.kind IS NOT NULL RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.runId()))
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
                    Values.parameters("runId", primaryRun.runId()))
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
                    Values.parameters("runId", primaryRun.runId()))
                .single()
                .get("count")
                .asLong());
        assertTrue(
            session
                    .run(
                        "MATCH (method:JavaMethod {runId: $runId}) "
                            + "WHERE method.hasBody = true RETURN count(method) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (operation:OperationNode {runId: $runId})-[:HAS_REFERENCE|HAS_ORIGIN]->() "
                            + "RETURN count(operation) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (:SourceOrigin {runId: $runId})-[:IN_SOURCE]->"
                            + "(:SourceFile {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
      }

      try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        independentState = storedProjection(verificationSession, independentRun.runId());
      }
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(replacementRun);
      }
      try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        replacementState = storedProjection(verificationSession, primaryRun.runId());
        assertEquals(1, verificationSession.run("MATCH (:Diagnostic {runId: $runId, code: 'publication.replacement-warning'}) "
            + "RETURN count(*) AS count", Values.parameters("runId", primaryRun.runId())).single().get("count").asLong());
        assertEquals(independentState, storedProjection(verificationSession, independentRun.runId()));
      }
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(replacementRun);
      }
      try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        assertEquals(replacementState, storedProjection(verificationSession, primaryRun.runId()));
        assertEquals(independentState, storedProjection(verificationSession, independentRun.runId()));
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
        assertEquals(replacementState, storedProjection(verificationSession, primaryRun.runId()));
        assertEquals(independentState, storedProjection(verificationSession, independentRun.runId()));
      }
    } finally {
      cleanup(settings, List.of(primaryRun.runId(), independentRun.runId()));
    }
  }

  @Test
  void givenEqualDiagnosticValuesAtTwoSlots_whenRunIsPublishedToNeo4j_thenOneDiagnosticAndOriginRetainBothPositions()
      throws IOException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var original = completedRun(Files.createDirectories(temporaryDirectory.resolve("diagnostics")),
        "DiagnosticSample" + UUID.randomUUID().toString().replace("-", ""));
    var origin = original.representation().orElseThrow().program().types().getFirst().origins().getFirst();
    var warning = new AnalysisDiagnostic("diagnostic:publication-warning", "publication.warning", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "Publication warning", List.of(origin), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var run = new AnalysisRun(original.runId(), original.inputSnapshot(), original.environmentSnapshot(), original.representation(),
        List.of(warning, warning), original.completion());

    try {
      // Act
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(run);
      }

      // Assert
      try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
        assertEquals(1, session.run("MATCH (d:Diagnostic {runId: $runId}) RETURN count(d) AS count",
            Values.parameters("runId", run.runId())).single().get("count").asLong());
        assertEquals(List.of(0L, 1L), session.run("MATCH (:AnalysisRun {runId: $runId})-[slot:HAS_DIAGNOSTIC]->"
            + "(d:Diagnostic {runId: $runId}) RETURN slot.position AS position ORDER BY position",
            Values.parameters("runId", run.runId())).list(record -> record.get("position").asLong()));
        assertEquals(1, session.run("MATCH (:Diagnostic {runId: $runId})-[origin:HAS_ORIGIN]->"
            + "(:SourceOrigin {runId: $runId}) RETURN count(origin) AS count",
            Values.parameters("runId", run.runId())).single().get("count").asLong());
      }
    } finally {
      cleanup(settings, List.of(run.runId()));
    }
  }

  @Test
  void givenAcknowledgedNeo4jCommit_whenLaterCloseSignalsFailure_thenApplicationRetainsRunAndStoredProjection()
      throws IOException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
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
          establishedProjection = storedProjection(verificationSession, run.runId());
        }
        var delegatedDriver = closeSignalingDriver(actualDriver, phase, signal, events);
        var service = new AnalysisApplicationService((root, configuration) -> run,
            () -> new Neo4jAnalysisRunRepository(delegatedDriver, sessionConfig, ignored -> projection));
        var request = new AnalysisRequest(sourceRoot, run.compilerConfiguration(), RepositorySavePolicy.SAVE_COMPLETED);

        // Act
        var failure = assertThrows(AnalysisRepositoryException.class, () -> service.execute(request));

        // Assert
        assertSame(run, failure.analysisRun());
        assertSame(signal, failure.repositoryFailure().getCause());
        assertEquals(List.of("schema.close", "publication.write.returned", "publication.close", "driver.close"), events);
        try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
            var verificationSession = verificationDriver.session(sessionConfig)) {
          assertEquals(projection.nodes().size(), nodeCount(verificationSession, run.runId()));
          assertEquals(projection.relationships().size(), verificationSession.run(
              "MATCH (:AnalysisEntity {runId: $runId})-[relation]->() WHERE relation.runId = $runId RETURN count(relation) AS count",
              Values.parameters("runId", run.runId())).single().get("count").asLong());
          assertEquals(establishedProjection, storedProjection(verificationSession, run.runId()));
        }
      } finally {
        cleanup(settings, List.of(run.runId()));
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
    var sourceFile = snapshot.sourceFiles().getFirst();
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var environment = new AnalysisEnvironmentSnapshot(configuration, "test-compiler", "test-vendor", "25-test",
        List.of("--release", "25"), List.of(), List.of());
    var typeKey = StableId.sha256("type", typeName);
    var fieldKey = StableId.sha256("field", typeKey, "field");
    var methodKey = StableId.sha256("method", typeKey, "evaluate(int,int)");
    var intType = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
    var field = new JavaField(fieldKey, typeKey, "field", intType, Set.of(), false, true,
        List.of(span(sourceFile, "int field;")));
    var parameters = List.of(
        new JavaParameter(StableId.sha256("parameter", methodKey, "input"), "input", intType, true,
            List.of(span(sourceFile, "int input"))),
        new JavaParameter(StableId.sha256("parameter", methodKey, "divisor"), "divisor", intType, true,
            List.of(span(sourceFile, "int divisor"))));
    var method = new JavaMethod(methodKey, typeKey, "evaluate", "evaluate(int,int)", "int", parameters, Set.of(), false, true, true,
        List.of(span(sourceFile, "int evaluate")));
    var type = new JavaType(typeKey, typeName, JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        List.of(), true, List.of(field), List.of(), List.of(method), List.of(),
        List.of(new SourceSpan(sourceFile.sourceFileKey(), 0, source.length())));
    var graph = controlFlowGraph(sourceFile, methodKey, fieldKey, intType);
    var representation = new ProgramRepresentation(new JavaProgram(List.of(type)),
        List.of(new MethodAnalysis(StableId.sha256("method-analysis", methodKey), method, graph)));
    var runId = StableId.sha256("analysis-run", SoftwareVersion.from(snapshot, environment).signature());
    return new AnalysisRun(runId, snapshot, environment, Optional.of(representation), List.of(), AnalysisCompletion.COMPLETED);
  }

  private static ControlFlowGraph controlFlowGraph(SourceFile sourceFile, String methodKey, String fieldKey,
      JavaTypeIdentity intType) {
    var graphId = StableId.sha256("cfg", methodKey);
    var entry = new EntryNode(StableId.sha256("cfg-node", graphId, "entry"), graphId);
    var exit = new ExitNode(StableId.sha256("cfg-node", graphId, "exit"), graphId);
    var inputValue = new FlowValue(StableId.sha256("cfg-value", graphId, "input"), graphId, ValueKind.PARAMETER,
        Optional.of("input"), Optional.empty(), Optional.of(intType));
    var divisorValue = new FlowValue(StableId.sha256("cfg-value", graphId, "divisor"), graphId, ValueKind.PARAMETER,
        Optional.of("divisor"), Optional.empty(), Optional.of(intType));
    var divisionValue = new FlowValue(StableId.sha256("cfg-value", graphId, "division"), graphId, ValueKind.TEMPORARY,
        Optional.empty(), Optional.empty(), Optional.of(intType));
    var selector = operation(graphId, "selector", OperationKind.SWITCH_SELECTOR, "switch (input)",
        List.of(new OperationOperand(0, inputValue)), Optional.empty(), List.of(), List.of(), span(sourceFile, "switch (input)"));
    var fieldReference = new SemanticReference(ReferenceKind.FIELD, ResolutionStatus.RESOLVED, Optional.of(fieldKey),
        Optional.of("int"), List.of("int"), List.of("int"));
    var fieldRead = operation(graphId, "field-read", OperationKind.READ, "field", List.of(), Optional.empty(),
        List.of(fieldReference), List.of(), span(sourceFile, "case 0 -> field"));
    var division = operation(graphId, "division", OperationKind.BINARY, "input / divisor",
        List.of(new OperationOperand(0, inputValue), new OperationOperand(1, divisorValue)), Optional.of(divisionValue), List.of(),
        List.of("java.lang.ArithmeticException"), span(sourceFile, "input / divisor"));
    var returning = operation(graphId, "return", OperationKind.RETURN, "return switch", List.of(), Optional.empty(), List.of(),
        List.of(), span(sourceFile, "return switch"));
    var edges = List.of(
        edge(graphId, "entry-selector", entry.nodeId(), selector.nodeId(), TransferKind.NORMAL, Optional.empty()),
        edge(graphId, "selector-field", selector.nodeId(), fieldRead.nodeId(), TransferKind.SWITCH_ALTERNATIVE,
            Optional.of("case 0")),
        edge(graphId, "selector-division", selector.nodeId(), division.nodeId(), TransferKind.SWITCH_ALTERNATIVE,
            Optional.of("default")),
        edge(graphId, "field-return", fieldRead.nodeId(), returning.nodeId(), TransferKind.NORMAL, Optional.empty()),
        edge(graphId, "division-return", division.nodeId(), returning.nodeId(), TransferKind.NORMAL, Optional.empty()),
        edge(graphId, "division-exception", division.nodeId(), exit.nodeId(), TransferKind.EXCEPTIONAL,
            Optional.of("java.lang.ArithmeticException")),
        edge(graphId, "return-exit", returning.nodeId(), exit.nodeId(), TransferKind.RETURN, Optional.empty()));
    return new ControlFlowGraph(graphId, methodKey, entry, List.of(selector, fieldRead, division, returning),
        List.of(inputValue, divisorValue, divisionValue), exit, edges);
  }

  private static OperationNode operation(String graphId, String identity, OperationKind kind, String detail,
      List<OperationOperand> operands, Optional<FlowValue> definedValue, List<SemanticReference> references,
      List<String> possibleExceptions, SourceSpan origin) {
    return new OperationNode(StableId.sha256("cfg-node", graphId, identity), graphId, kind, detail, operands, definedValue,
        Optional.empty(), references, possibleExceptions, List.of(origin));
  }

  private static CfgEdge edge(String graphId, String identity, String sourceNodeId, String targetNodeId, TransferKind kind,
      Optional<String> discriminator) {
    return new CfgEdge(StableId.sha256("cfg-edge", graphId, identity), graphId, sourceNodeId, targetNodeId, kind, discriminator);
  }

  private static SourceSpan span(SourceFile sourceFile, String fragment) {
    var start = sourceFile.content().indexOf(fragment);
    if (start < 0) {
      throw new IllegalArgumentException("Missing source fragment: " + fragment);
    }
    return new SourceSpan(sourceFile.sourceFileKey(), start, start + fragment.length());
  }

  private static Neo4jGraphProjection constraintConflictProjection(AnalysisRun run) {
    var valid = new Neo4jAnalysisRunMapper().map(run);
    var nodes = new ArrayList<>(valid.nodes());
    var duplicateRunKey = run.runId() + "-duplicate";
    nodes.add(new Neo4jNode(duplicateRunKey, "AnalysisRun",
        Map.of("runId", run.runId(), "key", duplicateRunKey, "completion", "COMPLETED")));
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
