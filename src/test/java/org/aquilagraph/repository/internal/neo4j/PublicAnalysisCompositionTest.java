package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.CliPublicationReporting;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisDerivationException;
import org.aquilagraph.application.AnalysisOperation;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisRequestIdentity.AvailableSource;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.UnavailableSource;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.MethodCoverage;
import org.aquilagraph.application.MethodCoverageState;
import org.aquilagraph.application.PublicationEligibility.Impediment;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationOutcome;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.cli.AnalyzeCommandOutcome;
import org.aquilagraph.cli.ChannelEmission;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandExitPolicy;
import org.aquilagraph.cli.CommandInvocation;
import org.aquilagraph.cli.CommandRunner;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.repository.internal.neo4j.AstRepositoryDriverStub.Statement;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.retention.consumer.PublicAstObservation;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

class PublicAnalysisCompositionTest {
  private static final String BRANCHING = """
      interface Sample {
        static int choose(boolean positive) {
          if (positive) { return 7; }
          return 11;
        }
        static int fallback() { return 13; }
      }
      """;
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final AstProfile PROFILE = AstProfile.JAVA_SOURCE_MODULE_SERVICES;
  private static final CfgProfile CFG = CfgProfile.JAVA_BOUNDED;
  private static final AstRetentionRepositoryFactory NO_AST_STORAGE = context -> {
    throw new AssertionError("Unexpected AST repository acquisition: " + context);
  };
  private static final AnalysisRunRepositoryFactory NO_RUN_STORAGE = context -> {
    throw new AssertionError("Unexpected completed-run repository acquisition: " + context);
  };
  private static final Pattern AST_NODE_INSERT = Pattern.compile(
      "UNWIND \\$nodes AS expected CREATE \\(entity:AstEntity:`([^`]+)`\\) SET entity = expected "
          + "RETURN entity.key AS key, elementId\\(entity\\) AS address");
  private static final Pattern AST_RELATIONSHIP_INSERT = Pattern.compile(
      "UNWIND \\$relationships AS item MATCH \\(source\\), \\(target\\) "
          + "WHERE elementId\\(source\\) = item.source AND elementId\\(target\\) = item.target "
          + "CREATE \\(source\\)-\\[relation:`([^`]+)`\\]->\\(target\\) SET relation = item.properties");
  private static final String AST_ROOT_UPDATE = "UNWIND $nodes AS item MATCH (entity) WHERE elementId(entity) = item.address "
      + "SET entity:`AstResult`, entity = item.properties";
  private static final String AST_RESULT_LOOKUP =
      "MATCH (result:AstEntity:AstResult {astResultKey: $scope}) RETURN properties(result) AS properties LIMIT 2";
  private static final String AST_ENTITY_SCOPE = "MATCH (entity:AstEntity {astResultKey: $scope}) RETURN ";
  private static final String AST_RELATIONSHIP_SCOPE = "MATCH (source)-[relation]->(target) "
      + "WHERE (source:AstEntity AND source.astResultKey = $scope) OR relation.astResultKey = $scope "
      + "OR (target:AstEntity AND target.astResultKey = $scope) RETURN ";
  private static final String AST_NODE_LOOKUP =
      AST_ENTITY_SCOPE + "entity.key AS key, labels(entity) AS labels, properties(entity) AS properties";
  private static final String AST_RELATIONSHIP_LOOKUP = AST_RELATIONSHIP_SCOPE
      + "relation.key AS key, type(relation) AS type, source.key AS sourceKey, target.key AS targetKey, "
      + "labels(source) AS sourceLabels, source.astResultKey AS sourceAstResultKey, "
      + "labels(target) AS targetLabels, target.astResultKey AS targetAstResultKey, properties(relation) AS properties";
  private static final String RUN_NODE_CREATE = "CREATE (entity:AnalysisEntity:`";
  private static final String RUN_RELATIONSHIP_SCOPE = "MATCH (source:AnalysisEntity {runId: $runId, key: $sourceKey}), "
      + "(target:AnalysisEntity {runId: $runId, key: $targetKey}) CREATE (source)-[relation:";

  @TempDir Path directory;

  @Test
  void givenBranchingSource_whenRetainedAndQueried_thenConsumerTraversesTheActualWrittenValuesAndDerivesTheSameGraph() {
    // Arrange
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var source = source(BRANCHING);
    var selection = CfgSelection.allBodies(CFG);
    var write = new AstRepositoryDriverStub();
    var read = new AstRepositoryDriverStub();

    // Act
    var run = computation.analyze(new AnalysisComputationRequest(source, Optional.of(selection)));
    var outcome = run.representation().ast();
    var original = outcome.result().orElseThrow();
    var identity = original.identity();
    write.lockedAstSubject = identity.astResultKey();
    var publication = AnalysisCapabilities.astRetention(astFactory(write)::openRetention)
        .retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
    read.stored = writtenAst(identity, write);
    var lookup = AnalysisCapabilities.astQuery(astFactory(read)::openQuery).findAst(identity);
    var retainedOutcome = lookup.outcome().orElseThrow();
    var retained = retainedOutcome.result().orElseThrow();
    var method = methodKey(outcome, "choose");
    var requested = new CfgRequest(identity, List.of(method), CFG);
    var derived = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(retainedOutcome, requested);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertEquals(AnalysisOperation.PRODUCTION_WITH_CFG, run.identity().operation());
    assertEquals(new AvailableSource(identity), run.identity().source());
    var bound = assertInstanceOf(ProductionSelection.class, run.identity().selection());
    assertSame(selection, bound.selection());
    assertEquals(Optional.of(identity), bound.boundIdentity());
    assertEquals(List.of(method, methodKey(outcome, "fallback")).stream().sorted().toList(), bound.methodKeys());
    assertPublication(publication, PublicationIntent.SAVE_COMPLETE_AST, Optional.of(new Ast(identity)), PublicationStatus.SAVED,
        CommitKnowledge.KNOWN_COMMIT);
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return",
        "write.acknowledged", "session.close:1", "driver.close"), write.events);
    assertEquals(Map.of("scope", identity.astResultKey()), write.statements.getFirst().parameters().asMap());
    assertEquals("MERGE (subject:AstEntity {astResultKey: $scope, key: $scope}) SET subject.key = subject.key",
        write.statements.getFirst().query());
    assertTrue(write.queries.stream().noneMatch(query -> query.contains(":AnalysisEntity")));
    var reconciliation = write.statements.stream().filter(statement -> statement.query().contains("RETURN elementId(entity) AS address")
        || statement.query().contains("RETURN elementId(relation) AS address")).toList();
    assertAstStatements(identity, reconciliation,
        AST_ENTITY_SCOPE + "elementId(entity) AS address, labels(entity) AS labels, properties(entity) AS properties, "
            + "CASE WHEN entity.key IS :: STRING NOT NULL THEN entity.key ELSE null END AS key",
        AST_RELATIONSHIP_SCOPE + "elementId(relation) AS address, type(relation) AS type, properties(relation) AS properties, "
            + "elementId(source) AS source, elementId(target) AS target, "
            + "CASE WHEN relation.key IS :: STRING NOT NULL THEN relation.key ELSE null END AS key");
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(identity, lookup.requestedIdentity());
    assertEquals(identity, retained.identity());
    assertEquals("java-source/22", retained.identity().sourceModelVersion());
    assertEquals(original.qualification(), retained.qualification());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    assertEquals(PublicAstObservation.describe(original), PublicAstObservation.describe(retained));
    var body = retained.body(method).orElseThrow();
    assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.IF));
    assertEquals(AnalysisCompletion.COMPLETE, derived.completion());
    assertEquals(AnalysisOperation.DERIVE_EXISTING, derived.identity().operation());
    assertEquals(new AvailableSource(identity), derived.identity().source());
    assertSame(requested, assertInstanceOf(ExistingSelection.class, derived.identity().selection()).request());
    assertSame(retainedOutcome, derived.representation().ast());
    var selected = derived.representation().cfg().orElseThrow();
    assertSame(requested, selected.request());
    assertEquals(List.of(method), selected.methods().stream().map(result -> result.methodKey()).toList());
    var graph = selected.methods().getFirst().graph().orElseThrow();
    var productionGraph = run.representation().cfg().orElseThrow().methods().stream()
        .filter(result -> result.methodKey().equals(method)).findFirst().orElseThrow().graph().orElseThrow();
    assertEquals(productionGraph.identity(), graph.identity());
    assertEquals(identity, graph.identity().astIdentity());
    assertEquals(retained.bodyRef(method).orElseThrow(), graph.body());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), read.events);
    assertAstStatements(identity, read.statements, AST_RESULT_LOOKUP, AST_NODE_LOOKUP, AST_RELATIONSHIP_LOOKUP);
  }

  @Test
  void givenBranchingSource_whenCliPublishesAndAstIsQueriedIndependently_thenBothConsumersPreserveExactGraphIdentity()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Sample.java"), BRANCHING);
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var cliSource = new AstProductionRequest(directory, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED);
    var request = new AnalysisComputationRequest(cliSource, Optional.of(CfgSelection.allBodies(CFG)));
    var noStorage = AnalysisCapabilities.execution(computation, NO_AST_STORAGE, NO_RUN_STORAGE);
    var driver = AstRepositoryDriverStub.completedRuns();
    var runner = CliPublicationReporting.runner(NO_AST_STORAGE, runRepository(driver));
    var astWrite = new AstRepositoryDriverStub();
    var astRead = new AstRepositoryDriverStub();
    var output = new StringWriter();
    var error = new StringWriter();

    // Act
    var unsaved = noStorage.execute(new AnalysisRequest(request, PublicationIntent.DO_NOT_SAVE));
    var invocation = runPersistingCommand(runner, output, error);
    var saved = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
    var ast = saved.run().representation().ast();
    var identity = ast.result().orElseThrow().identity();
    astWrite.lockedAstSubject = identity.astResultKey();
    var retained = AnalysisCapabilities.astRetention(astFactory(astWrite)::openRetention)
        .retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
    astRead.stored = writtenAst(identity, astWrite);
    var lookup = AnalysisCapabilities.astQuery(astFactory(astRead)::openQuery).findAst(identity);
    var retainedAst = lookup.outcome().orElseThrow();
    var method = methodKey(ast, "choose");
    var rederived = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard())
        .deriveExisting(retainedAst, new CfgRequest(identity, List.of(method), CFG));

    // Assert
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, invocation.exitCode());
    assertEquals(ChannelEmission.Status.COMPLETED, invocation.emission().output());
    assertEquals("", error.toString());
    assertEquals(AnalysisCompletion.COMPLETE, saved.run().completion());
    assertTrue(saved.run().publicationEligibility().eligible());
    assertTrue(saved.run().qualification().reasons().isEmpty());
    assertEquals(unsaved.run().identity(), saved.run().identity());
    assertEquals(unsaved.run().diagnostics().values(), saved.run().diagnostics().values());
    var runKey = saved.run().identity().key();
    var astKey = identity.astResultKey();
    assertNotEquals(astKey, runKey);
    assertPublication(unsaved.publication(), PublicationIntent.DO_NOT_SAVE,
        Optional.of(new Ast(identity)), PublicationStatus.NOT_REQUESTED,
        CommitKnowledge.NOT_STARTED);
    assertPublication(saved.publication(), PublicationIntent.SAVE_COMPLETED, Optional.of(new CompletedRun(runKey)), PublicationStatus.SAVED,
        CommitKnowledge.KNOWN_COMMIT);
    assertCompletedRunWrites(new Neo4jAnalysisRunMapper().map(saved.run()), driver.statements);
    assertTrue(driver.queries.stream().noneMatch(query -> query.contains(":AstEntity")));
    var graphs = saved.run().representation().cfg().orElseThrow().methods().stream().map(outcome -> outcome.graph().orElseThrow()).toList();
    var expectedGraphs = graphs.stream().map(graph -> graph.graphId()).sorted().toList();
    assertEquals(2, expectedGraphs.size());
    var writtenGraphs = driver.statements.stream().filter(statement -> statement.query()
        .startsWith(RUN_NODE_CREATE + "ControlFlowGraph`)")).map(statement -> statement.parameters().get("properties")).toList();
    assertEquals(expectedGraphs, writtenGraphs.stream().map(row -> row.get("key").asString()).sorted().toList());
    for (var row : writtenGraphs) {
      var graph = graphs.stream().filter(candidate -> candidate.graphId().equals(row.get("key").asString())).findFirst().orElseThrow();
      var graphIdentity = graph.identity();
      assertEquals(Map.of("key", graph.graphId(), "runId", runKey, "astResultKey", graphIdentity.astIdentity().astResultKey(),
          "methodKey", graph.methodKey(), "cfgProfileVersion", graphIdentity.cfgProfileVersion(), "graphModelVersion",
          graphIdentity.graphModelVersion(), "canonicalContentDigest", graphIdentity.canonicalContentDigest(),
          "sourceOccurrenceKey", graph.body().sourceOccurrenceKey().orElseThrow()),
          row.asMap());
      assertEquals(astKey, graphIdentity.astIdentity().astResultKey());
    }
    var methodKeysByAnalysis = driver.statements.stream()
        .filter(statement -> statement.query().startsWith(RUN_NODE_CREATE + "MethodAnalysis`)"))
        .map(statement -> statement.parameters().get("properties"))
        .collect(Collectors.toMap(row -> row.get("key").asString(), row -> row.get("methodKey").asString()));
    var graphLinks = driver.statements.stream().filter(statement -> statement.query().startsWith(RUN_RELATIONSHIP_SCOPE + "`HAS_CFG`]"))
        .collect(Collectors.toMap(statement -> statement.parameters().get("targetKey").asString(),
            statement -> methodKeysByAnalysis.get(statement.parameters().get("sourceKey").asString())));
    assertEquals(graphs.stream().collect(Collectors.toMap(graph -> graph.graphId(), graph -> graph.methodKey())), graphLinks);
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return",
        "write.acknowledged", "session.close:1", "driver.close"), driver.events);
    assertPublication(retained, PublicationIntent.SAVE_COMPLETE_AST, Optional.of(new Ast(identity)), PublicationStatus.SAVED,
        CommitKnowledge.KNOWN_COMMIT);
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(identity, lookup.requestedIdentity());
    assertEquals(identity, retainedAst.result().orElseThrow().identity());
    assertEquals("driver.close", astWrite.events.getLast());
    assertEquals("driver.close", astRead.events.getLast());
    assertEquals(AnalysisCompletion.COMPLETE, rederived.completion());
    var chosen = graphs.stream().filter(graph -> graph.methodKey().equals(method)).findFirst().orElseThrow();
    assertEquals(chosen.identity(), rederived.representation().cfg().orElseThrow().methods().getFirst().graph().orElseThrow().identity());
    assertTrue(output.toString().lines().toList().containsAll(List.of(
        "Completion: COMPLETE", "Method CFG: \"" + method + "\" AVAILABLE",
        "Repository: SAVED", "Publication subject: COMPLETED_RUN \"" + runKey + "\"",
        "Commit knowledge: KNOWN_COMMIT")), output.toString());
  }

  @Test
  void givenCompleteSourceWithRefusedCfg_whenCliRequestsPublication_thenRefusalKeepsItsQualificationAndAcquiresNothing()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Sample.java"),
        "interface Sample { static int run(int value) { (value) = 1; return value; } }");
    var runner = CliPublicationReporting.runner(NO_AST_STORAGE, NO_RUN_STORAGE);
    var output = new StringWriter();
    var error = new StringWriter();

    // Act
    var invocation = runPersistingCommand(runner, output, error);

    // Assert
    var observed = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
    var run = observed.run();
    var ast = run.representation().ast();
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertFalse(run.isPublishable());
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE, Impediment.ERROR_DIAGNOSTICS),
        run.publicationEligibility().impediments());
    var reason = assertInstanceOf(RequestQualificationReason.Method.class, run.qualification().reasons().getFirst());
    assertSame(run.representation().cfg().orElseThrow().methods().getFirst(), reason.outcome());
    assertEquals(List.of(MethodCoverageState.UNSUPPORTED), run.methodCoverages().stream().map(MethodCoverage::state).toList());
    assertPublication(observed.publication(), PublicationIntent.SAVE_COMPLETED, Optional.of(new CompletedRun(run.identity().key())),
        PublicationStatus.NOT_ELIGIBLE, CommitKnowledge.NOT_STARTED);
    assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, invocation.exitCode());
    assertTrue(output.toString().lines().toList().containsAll(List.of(
        "Completion: PARTIAL", "Method CFG: \"" + methodKey(ast, "run") + "\" UNSUPPORTED",
        "Repository: NOT_ELIGIBLE", "Publication impediments: RUN_NOT_COMPLETE, CFG_NOT_COMPLETE, ERROR_DIAGNOSTICS")),
        output.toString());
    assertTrue(error.toString().lines().anyMatch("  Severity: ERROR"::equals), error.toString());
  }

  @Test
  void givenUnmatchedAdapterWriteAndCleanupFailures_whenCliPublishes_thenTheAbruptExitReportsExactOrderedEvidence()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Sample.java"), BRANCHING);
    var driver = AstRepositoryDriverStub.completedRuns();
    var primary = new IllegalStateException("Unmatched \"write\"\nobservation");
    var sessionCleanup = new IllegalStateException("Session cleanup observation");
    var driverCleanup = new IllegalStateException("Driver cleanup observation");
    driver.writeFailure = primary;
    driver.failingClose = 1;
    driver.closeFailure = sessionCleanup;
    driver.driverCloseFailure = driverCleanup;
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var execution = AnalysisCapabilities.execution(computation, NO_AST_STORAGE, runRepository(driver));
    var original = new AtomicReference<AnalysisPublicationException>();
    AnalysisUseCase observedExecution = request -> {
      try {
        return execution.execute(request);
      } catch (AnalysisPublicationException failure) {
        original.set(failure);
        throw failure;
      }
    };
    var output = new StringWriter();
    var error = new StringWriter();

    // Act
    var failure = assertThrows(AnalysisPublicationException.class,
        () -> runPersistingCommand(CliPublicationReporting.runner(observedExecution), output, error));

    // Assert
    assertSame(original.get(), failure);
    assertSame(primary, failure.getCause());
    var run = assertInstanceOf(Coordinated.class, failure.subject()).run();
    assertTrue(run.publicationEligibility().eligible());
    var context = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    assertEquals(context, failure.context());
    assertEquals(run.identity().key(), driver.statements.getFirst().parameters().get("runId").asString());
    var carrier = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.failure());
    assertEquals(Neo4jRepositoryPhase.REPLACEMENT, carrier.phase());
    assertEquals(Optional.of(CommitKnowledge.UNKNOWN), carrier.knowledge());
    var cleanup = List.of(carrier.getSuppressed());
    assertEquals(2, cleanup.size());
    assertSame(sessionCleanup, assertInstanceOf(Neo4jRepositoryOperationException.class, cleanup.getFirst()).effectivePrimary());
    assertSame(driverCleanup, assertInstanceOf(Neo4jRepositoryOperationException.class, cleanup.getLast()).effectivePrimary());
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return",
        "session.close:1", "driver.close"), driver.events);
    assertEquals("", output.toString());
    var diagnostic = error.toString();
    assertTrue(diagnostic.startsWith("Publication aborted: "), diagnostic);
    assertTrue(diagnostic.contains("  Message: \"Unmatched \\\"write\\\"\\nobservation\""), diagnostic);
    assertTrue(diagnostic.contains("  Operation subject: COMPLETED_RUN \"" + run.identity().key() + "\""), diagnostic);
    assertTrue(diagnostic.lines().anyMatch("  Commit knowledge: UNKNOWN"::equals), diagnostic);
    assertEquals(List.of("  Adapter phase: \"REPLACEMENT\"", "  Adapter phase: \"PUBLICATION_SESSION_CLOSE\"",
        "  Adapter phase: \"REPOSITORY_CLOSE\""), diagnostic.lines().filter(line -> line.startsWith("  Adapter phase:")).toList());
  }

  @Test
  void givenAstOnlyAndUnavailableSource_whenCoordinated_thenQualificationAndIneligibilityStayIndependentOfStorage() {
    // Arrange
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), (source, request) -> {
      throw new AssertionError("Unselected or unavailable source must not invoke CFG derivation");
    });
    var execution = AnalysisCapabilities.execution(computation, NO_AST_STORAGE, NO_RUN_STORAGE);
    var emptySource = new AstProductionRequest(InputSnapshot.fromSources(directory, List.of()), CONFIGURATION, PROFILE);
    var selection = new CfgSelection(List.of("missing"), CFG);

    // Act
    var astOnly = execution.execute(new AnalysisRequest(new AnalysisComputationRequest(source(BRANCHING), Optional.empty()),
        PublicationIntent.SAVE_COMPLETED));
    var unavailable = execution.execute(new AnalysisRequest(new AnalysisComputationRequest(emptySource, Optional.of(selection)),
        PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.run().completion());
    assertEquals(AnalysisOperation.AST_ONLY, astOnly.run().identity().operation());
    assertSame(NotRequested.INSTANCE, astOnly.run().identity().selection());
    assertTrue(astOnly.run().representation().cfg().isEmpty());
    assertPublication(astOnly.publication(), PublicationIntent.SAVE_COMPLETED, Optional.of(new CompletedRun(astOnly.run().identity().key())),
        PublicationStatus.NOT_ELIGIBLE, CommitKnowledge.NOT_STARTED);
    assertEquals(AnalysisCompletion.REJECTED, unavailable.run().completion());
    var ast = unavailable.run().representation().ast();
    assertTrue(ast.result().isEmpty());
    assertTrue(unavailable.run().representation().cfg().isEmpty());
    assertEquals(new UnavailableSource(PROFILE.version(), ast.sourceSubjectDigest()), unavailable.run().identity().source());
    assertEquals(new ProductionSelection(Optional.empty(), selection), unavailable.run().identity().selection());
    assertEquals(List.of(new RequestQualificationReason.Source(ast)), unavailable.run().qualification().reasons());
    assertEquals(ast.diagnostics(), unavailable.run().diagnostics().values());
    assertPublication(unavailable.publication(), PublicationIntent.SAVE_COMPLETE_AST, Optional.empty(), PublicationStatus.NOT_ELIGIBLE,
        CommitKnowledge.NOT_STARTED);
  }

  @Test
  void givenNativeCfgRefusal_whenCompleteAstIsPublished_thenTheSourceIsSavedWithoutRewritingTheRun() {
    // Arrange
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var source = source("""
        interface Sample {
          static int refused() {
            java.util.function.IntSupplier next = () -> 1;
            return next.getAsInt();
          }
        }
        """);
    var driver = new AstRepositoryDriverStub();
    var execution = AnalysisCapabilities.execution(computation, astFactory(driver)::openRetention, NO_RUN_STORAGE);
    var request = new AnalysisComputationRequest(source, Optional.of(CfgSelection.allBodies(CFG)));

    // Act
    var saved = execution.execute(new AnalysisRequest(request, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    var run = saved.run();
    var ast = run.representation().ast();
    var original = ast.result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    var cfg = run.representation().cfg().orElseThrow();
    assertEquals(List.of(methodKey(ast, "refused")), cfg.request().methodKeys());
    var refusal = cfg.methods().getFirst();
    assertEquals(MethodCfgState.UNSUPPORTED, refusal.state());
    assertTrue(refusal.graph().isEmpty());
    var reason = assertInstanceOf(RequestQualificationReason.Method.class, run.qualification().reasons().getFirst());
    assertSame(refusal, reason.outcome());
    var blocker = refusal.blockers().getFirst();
    assertEquals(SyntaxKind.LAMBDA_EXPRESSION, original.node(blocker.occurrence().orElseThrow()).orElseThrow().syntaxKind());
    assertSame(blocker.diagnostic(), cfg.diagnostics().getFirst());
    assertSame(blocker.diagnostic(), run.diagnostics().values().getFirst());
    assertEquals(List.of(run.diagnostics().occurrences().getFirst()), run.diagnostics().relatedTo(reason));
    assertPublication(saved.publication(), PublicationIntent.SAVE_COMPLETE_AST, Optional.of(new Ast(original.identity())),
        PublicationStatus.SAVED, CommitKnowledge.KNOWN_COMMIT);
    assertEquals(Map.of("scope", original.identity().astResultKey()), driver.statements.getFirst().parameters().asMap());
    assertEquals("driver.close", driver.events.getLast());
  }

  @Test
  void givenRecoverableCfgFailure_whenCoordinatingCompleteAstRetention_thenAdapterWritesSourceAndRunKeepsFailedCoverage() {
    // Arrange
    var cause = new IllegalStateException("Controlled CFG construction failure");
    var driver = new AstRepositoryDriverStub();
    var reader = new AstRepositoryDriverStub();
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), (ast, selection) -> {
      driver.lockedAstSubject = ast.identity().astResultKey();
      return RecoverableCfgFailures.atMethod(selection.methodKeys().getFirst(), cause).derive(ast, selection);
    });
    var execution = AnalysisCapabilities.execution(computation, astFactory(driver)::openRetention, NO_RUN_STORAGE);
    var request = new AnalysisComputationRequest(source("interface Sample { static int value() { return 1; } }"),
        Optional.of(CfgSelection.allBodies(CFG)));

    // Act
    var saved = execution.execute(new AnalysisRequest(request, PublicationIntent.SAVE_COMPLETE_AST));
    var run = saved.run();
    var coverage = run.methodCoverages();
    var ast = run.representation().ast();
    var result = ast.result().orElseThrow();
    reader.stored = writtenAst(result.identity(), driver);
    var lookup = AnalysisCapabilities.astQuery(astFactory(reader)::openQuery).findAst(result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    assertEquals(List.of(MethodCoverageState.FAILED), coverage.stream().map(MethodCoverage::state).toList());
    var method = result.program().methods().getFirst();
    var failed = run.representation().cfg().orElseThrow().methods().getFirst();
    assertSame(method, coverage.getFirst().declaration());
    assertEquals(coverage.getFirst(), run.methodCoverage(method.methodKey()).orElseThrow());
    assertSame(failed, coverage.getFirst().cfg().orElseThrow());
    assertSame(cause, failed.failure().orElseThrow());
    var reason = assertInstanceOf(RequestQualificationReason.Method.class, run.qualification().reasons().getFirst());
    assertSame(failed, reason.outcome());
    assertSame(failed.diagnostics().getFirst(), run.diagnostics().values().getFirst());
    assertEquals(List.of(run.diagnostics().occurrences().getFirst()), run.diagnostics().relatedTo(reason));
    assertPublication(saved.publication(), PublicationIntent.SAVE_COMPLETE_AST, Optional.of(new Ast(result.identity())),
        PublicationStatus.SAVED, CommitKnowledge.KNOWN_COMMIT);
    assertEquals(Map.of("scope", result.identity().astResultKey()), driver.statements.getFirst().parameters().asMap());
    assertTrue(driver.events.contains("write.acknowledged"));
    assertEquals("driver.close", driver.events.getLast());
    assertTrue(driver.queries.stream().noneMatch(query -> query.contains(":AnalysisEntity")));
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(result.identity(), lookup.requestedIdentity());
    var retained = lookup.outcome().orElseThrow().result().orElseThrow();
    assertEquals(result.identity(), retained.identity());
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(retained));
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), reader.events);
  }

  @Test
  void givenDeclaredCfgConstructionConditions_whenDerivingEstablishedSource_thenReturnedFailureAndAbruptProgressKeepNativeOwners() {
    // Arrange
    var ast = JavaFrontends.standard().produce(source(BRANCHING));
    var result = ast.result().orElseThrow();
    var first = methodKey(ast, "choose");
    var second = methodKey(ast, "fallback");
    var request = new CfgRequest(result.identity(), List.of(first, second), CFG);
    var expectedProgress = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(first), CFG)).methods();
    var cause = new IllegalStateException("Controlled CFG construction failure");
    var carrier = new CfgDerivationException(request, expectedProgress, cause);
    var recoverable = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(second, cause));
    var abrupt = AnalysisCapabilities.existingSourceDerivation((source, cfgRequest) -> {
      assertSame(result, source);
      assertSame(request, cfgRequest);
      throw carrier;
    });

    // Act
    var run = recoverable.deriveExisting(ast, request);
    var interrupted = assertThrows(AnalysisDerivationException.class, () -> abrupt.deriveExisting(ast, request));

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    assertSame(ast, run.representation().ast());
    assertSame(request, assertInstanceOf(ExistingSelection.class, run.identity().selection()).request());
    var cfg = run.representation().cfg().orElseThrow();
    assertSame(request, cfg.request());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.FAILED), cfg.methods().stream().map(method -> method.state()).toList());
    var progress = cfg.methods().getFirst().graph().orElseThrow();
    assertEquals(expectedProgress.getFirst().graph().orElseThrow().identity(), progress.identity());
    assertEquals(result.bodyRef(first).orElseThrow(), progress.body());
    var failed = cfg.methods().getLast();
    assertEquals(result.bodyRef(second), failed.body());
    assertTrue(failed.graph().isEmpty());
    assertSame(cause, failed.failure().orElseThrow());
    var reason = assertInstanceOf(RequestQualificationReason.Method.class, run.qualification().reasons().getFirst());
    assertSame(failed, reason.outcome());
    assertEquals(List.of("control-flow.construction-failed"), run.diagnostics().values().stream().map(diagnostic -> diagnostic.code()).toList());
    assertSame(cfg.diagnostics().getFirst(), run.diagnostics().values().getFirst());
    assertEquals(List.of(run.diagnostics().occurrences().getFirst()), run.diagnostics().relatedTo(reason));
    assertSame(ast, interrupted.ast());
    assertSame(carrier, interrupted.derivationFailure());
    assertSame(request, interrupted.derivationFailure().request());
    assertSame(cause, interrupted.derivationFailure().getCause());
    assertEquals(expectedProgress, interrupted.derivationFailure().completedMethods());
  }

  @Test
  void givenAbruptDerivationWithCompleteAst_whenCoordinatingAstRetention_thenExactFailureEscapesBeforePublication() {
    // Arrange
    var produced = new AtomicReference<AstOutcome>();
    var carrier = new AtomicReference<CfgDerivationException>();
    var cause = new IllegalStateException("Controlled abrupt CFG construction failure");
    var computation = AnalysisCapabilities.computation(input -> {
      var ast = JavaFrontends.standard().produce(input);
      produced.set(ast);
      return ast;
    }, (ast, selection) -> {
      var prefix = new CfgRequest(ast.identity(), List.of(selection.methodKeys().getFirst()), selection.profile());
      var progress = CfgDerivers.standard().derive(ast, prefix).methods();
      var failure = new CfgDerivationException(selection, progress, cause);
      carrier.set(failure);
      throw failure;
    });
    var execution = AnalysisCapabilities.execution(computation, NO_AST_STORAGE, NO_RUN_STORAGE);
    var request = new AnalysisRequest(new AnalysisComputationRequest(source(BRANCHING), Optional.of(CfgSelection.allBodies(CFG))),
        PublicationIntent.SAVE_COMPLETE_AST);

    // Act
    var interrupted = assertThrows(AnalysisDerivationException.class, () -> execution.execute(request));

    // Assert
    var ast = produced.get();
    assertSame(ast, interrupted.ast());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertSame(carrier.get(), interrupted.derivationFailure());
    assertSame(cause, interrupted.derivationFailure().getCause());
    var result = ast.result().orElseThrow();
    var first = methodKey(ast, "choose");
    assertEquals(new CfgRequest(result.identity(), List.of(first, methodKey(ast, "fallback")), CFG), carrier.get().request());
    var progress = carrier.get().completedMethods();
    assertEquals(1, progress.size());
    assertEquals(first, progress.getFirst().methodKey());
    assertEquals(MethodCfgState.AVAILABLE, progress.getFirst().state());
    assertEquals(result.bodyRef(first).orElseThrow(), progress.getFirst().graph().orElseThrow().body());
  }

  @Test
  void givenUnacknowledgedWriteAndUnmatchedPostCommitClosure_whenPublishing_thenBothExitsKeepTheirOwnComputedRunAndKnowledge() {
    // Arrange
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var observed = new AtomicReference<AnalysisRun>();
    var request = new AnalysisRequest(new AnalysisComputationRequest(source(BRANCHING), Optional.of(CfgSelection.allBodies(CFG))),
        PublicationIntent.SAVE_COMPLETED);
    var unacknowledged = AstRepositoryDriverStub.completedRuns();
    var writeFailure = new Neo4jException("Controlled managed-write acknowledgement failure");
    unacknowledged.writeFailure = writeFailure;
    var committed = AstRepositoryDriverStub.completedRuns();
    var closeFailure = new IllegalStateException("Controlled failure after managed-write acknowledgement");
    committed.driverCloseFailure = closeFailure;
    var normal = AnalysisCapabilities.execution(computation, NO_AST_STORAGE, runRepository(unacknowledged));
    var abrupt = AnalysisCapabilities.execution(input -> {
      var run = computation.analyze(input);
      observed.set(run);
      return run;
    }, NO_AST_STORAGE, runRepository(committed));

    // Act
    var returned = normal.execute(request);
    var interrupted = assertThrows(AnalysisPublicationException.class, () -> abrupt.execute(request));

    // Assert
    var context = RepositoryOperationContext.saveCompletedRun(returned.run().identity().key());
    var publication = returned.publication();
    assertEquals(PublicationIntent.SAVE_COMPLETED, publication.intent());
    assertEquals(PublicationStatus.FAILED, publication.status());
    assertEquals(Optional.of(context.subject()), publication.subjectIdentity());
    assertEquals(CommitKnowledge.UNKNOWN, publication.knowledge());
    var replacement = assertInstanceOf(Neo4jRepositoryOperationException.class, publication.failure().orElseThrow().carrier());
    assertEquals(context, replacement.context());
    assertEquals(Neo4jRepositoryPhase.REPLACEMENT, replacement.phase());
    assertSame(writeFailure, assertInstanceOf(RepositoryException.class, replacement.effectivePrimary()).getCause());
    assertEquals(AnalysisCompletion.COMPLETE, returned.run().completion());
    assertTrue(unacknowledged.events.contains("callback.return"));
    assertFalse(unacknowledged.events.contains("write.acknowledged"));
    assertEquals("driver.close", unacknowledged.events.getLast());
    assertEquals(context, interrupted.context());
    assertSame(observed.get(), assertInstanceOf(Coordinated.class, interrupted.subject()).run());
    assertEquals(returned.run().identity(), observed.get().identity());
    assertEquals(AnalysisCompletion.COMPLETE, observed.get().completion());
    var close = assertInstanceOf(Neo4jRepositoryOperationException.class, interrupted.failure());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, close.phase());
    assertEquals(Optional.of(CommitKnowledge.KNOWN_COMMIT), close.knowledge());
    assertSame(closeFailure, interrupted.getCause());
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return",
        "write.acknowledged", "session.close:1", "driver.close"), committed.events);
  }

  @Test
  void givenAbsentAndUnsupportedRows_whenQueried_thenOnlyTheirExactNormalLookupStatesAreReturned() {
    // Arrange
    var identity = JavaFrontends.standard().produce(source(BRANCHING)).result().orElseThrow().identity();
    var absent = new AstRepositoryDriverStub();
    var incompatible = new AstRepositoryDriverStub();
    incompatible.version = "java-source/unsupported";

    // Act
    var missing = AnalysisCapabilities.astQuery(astFactory(absent)::openQuery).findAst(identity);
    var unsupported = AnalysisCapabilities.astQuery(astFactory(incompatible)::openQuery).findAst(identity);

    // Assert
    assertEquals(new AstLookup(identity, AstLookupStatus.NOT_FOUND, Optional.empty(), Optional.empty()), missing);
    assertEquals(new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.of("java-source/unsupported")), unsupported);
    assertAstStatements(identity, absent.statements, AST_RESULT_LOOKUP, AST_ENTITY_SCOPE + "count(entity) AS count");
    assertAstStatements(identity, incompatible.statements, AST_RESULT_LOOKUP);
    for (var driver : List.of(absent, incompatible)) {
      assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    }
  }

  @Test
  void givenMalformedForeignAndUnclosedRows_whenQueried_thenNoneCanBecomeANormalLookup() {
    // Arrange
    var frontend = JavaFrontends.standard();
    var ast = frontend.produce(source(BRANCHING));
    var identity = ast.result().orElseThrow().identity();
    var codec = new Neo4jAstResultCodec();
    var projection = codec.map(ast);
    var malformed = new AstRepositoryDriverStub();
    malformed.stored = projection;
    malformed.malformedNodeRecords = true;
    var foreign = new AstRepositoryDriverStub();
    foreign.stored = codec.map(frontend.produce(source(BRANCHING.replace("13", "17"))));
    var unclosed = new AstRepositoryDriverStub();
    unclosed.stored = projection;
    unclosed.failingClose = 0;
    var closeFailure = new Neo4jException("Controlled read-session close failure");
    unclosed.closeFailure = closeFailure;

    // Act
    var invalidRows = assertThrows(Neo4jRepositoryOperationException.class,
        () -> AnalysisCapabilities.astQuery(astFactory(malformed)::openQuery).findAst(identity));
    var invalidIdentity = assertThrows(Neo4jRepositoryOperationException.class,
        () -> AnalysisCapabilities.astQuery(astFactory(foreign)::openQuery).findAst(identity));
    var failedClose = assertThrows(Neo4jRepositoryOperationException.class,
        () -> AnalysisCapabilities.astQuery(astFactory(unclosed)::openQuery).findAst(identity));

    // Assert
    assertNotEquals(identity, foreign.stored.identity());
    for (var invalid : List.of(invalidRows, invalidIdentity)) {
      assertEquals(Neo4jRepositoryPhase.DECODE, invalid.phase());
      assertInstanceOf(IllegalArgumentException.class, invalid.effectivePrimary());
    }
    assertEquals(Neo4jRepositoryPhase.READ_SESSION_CLOSE, failedClose.phase());
    assertSame(closeFailure, assertInstanceOf(RepositoryException.class, failedClose.effectivePrimary()).getCause());
    for (var failure : List.of(invalidRows, invalidIdentity, failedClose)) {
      assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
      assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
    }
    for (var driver : List.of(malformed, foreign, unclosed)) {
      assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    }
  }

  @TestFactory
  Stream<DynamicTest> givenOriginalHistoricalFixtures_whenQueried_thenThePublicConsumerStillObservesTheirOriginalValues() {
    // Arrange
    var formats = List.of(2, 19, 21);

    // Act
    var observations = formats.stream().map(format -> DynamicTest.dynamicTest(
        "givenOriginalSourceFormat" + format + "_whenQueried_thenThePublicConsumerObservesItsOriginalValues",
        () -> assertHistoricalLookup(format)));

    // Assert
    return observations;
  }

  private static void assertHistoricalLookup(int format) throws IOException {
    // Arrange
    var original = HistoricalAstFixtures.read(format);
    var identity = original.identity();
    var driver = new AstRepositoryDriverStub();
    driver.stored = new Neo4jAstResultCodec().map(AstOutcome.available(original));

    // Act
    var found = AnalysisCapabilities.astQuery(astFactory(driver)::openQuery).findAst(identity);
    var retained = found.outcome().orElseThrow().result().orElseThrow();

    // Assert
    assertEquals(AstLookupStatus.FOUND, found.status());
    assertEquals(identity, found.requestedIdentity());
    assertEquals(identity, retained.identity());
    assertEquals("java-source/" + format, retained.identity().sourceModelVersion());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    assertEquals(PublicAstObservation.describe(original), PublicAstObservation.describe(retained));
    assertAstStatements(identity, driver.statements, AST_RESULT_LOOKUP, AST_NODE_LOOKUP, AST_RELATIONSHIP_LOOKUP);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
  }

  private AstProductionRequest source(String content) {
    return new AstProductionRequest(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), content))), CONFIGURATION, PROFILE);
  }

  private CommandInvocation runPersistingCommand(CommandRunner runner, StringWriter output, StringWriter error) {
    return runner.run(new String[] {directory.toString(), "--persist"},
        CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));
  }

  private static Neo4jAstResultRepositoryFactory astFactory(AstRepositoryDriverStub driver) {
    return new Neo4jAstResultRepositoryFactory(PublicAnalysisCompositionTest::settings, (uri, auth) -> driver.driver());
  }

  private static AnalysisRunRepositoryFactory runRepository(AstRepositoryDriverStub driver) {
    return Neo4jAnalysisRunRepository.factory(PublicAnalysisCompositionTest::settings, (uri, auth) -> driver.driver());
  }

  private static Neo4jConnectionSettings settings() {
    return new Neo4jConnectionSettings(URI.create("bolt://localhost:7687"), "neo4j", "test", "neo4j");
  }

  private static String methodKey(AstOutcome outcome, String name) {
    return outcome.result().orElseThrow().program().methods().stream().filter(method -> method.name().equals(name))
        .findFirst().orElseThrow().methodKey();
  }

  private static void assertPublication(PublicationOutcome outcome, PublicationIntent intent,
      Optional<PublicationSubjectIdentity> subject, PublicationStatus status, CommitKnowledge knowledge) {
    assertEquals(new PublicationOutcome(intent, subject, status, knowledge, Optional.empty()), outcome);
  }

  private static void assertAstStatements(AstIdentity identity, List<Statement> statements, String... queries) {
    assertEquals(queries.length, statements.size());
    for (var index = 0; index < queries.length; index++) {
      var statement = statements.get(index);
      assertEquals(queries[index], statement.query());
      assertEquals(Map.of("scope", identity.astResultKey()), statement.parameters().asMap());
    }
  }

  private static void assertCompletedRunWrites(Neo4jGraphProjection projection, List<Statement> statements) {
    assertEquals(1 + projection.nodes().size() + projection.relationships().size(), statements.size());
    var writes = statements.iterator();
    var replacement = writes.next();
    assertEquals("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity", replacement.query());
    assertEquals(Map.of("runId", projection.runId()), replacement.parameters().asMap());
    for (var node : projection.nodes()) {
      var write = writes.next();
      assertEquals(RUN_NODE_CREATE + node.label() + "`) SET entity = $properties", write.query());
      assertEquals(Values.parameters("properties", node.properties()).asMap(), write.parameters().asMap(), node.key());
    }
    for (var relationship : projection.relationships()) {
      var write = writes.next();
      assertEquals(RUN_RELATIONSHIP_SCOPE + "`" + relationship.type() + "`]->(target) SET relation = $properties", write.query());
      assertEquals(Values.parameters("runId", projection.runId(), "sourceKey", relationship.sourceKey(),
          "targetKey", relationship.targetKey(), "properties", relationship.properties()).asMap(), write.parameters().asMap(),
          relationship.key());
    }
  }

  // Replay the root update and insert batches on the controlled read boundary.
  private static Neo4jAstProjection writtenAst(AstIdentity identity, AstRepositoryDriverStub write) {
    var rootUpdates = write.statements.stream().filter(statement -> AST_ROOT_UPDATE.equals(statement.query())).toList();
    assertEquals(1, rootUpdates.size(), "Expected the locked subject to receive its AST label and payload");
    var updates = rootUpdates.getFirst().parameters().get("nodes").asList(value -> value);
    assertEquals(1, updates.size());
    var update = updates.getFirst();
    var rootKey = identity.astResultKey();
    var properties = update.get("properties").asMap();
    assertEquals(Map.of("address", "node/" + rootKey, "properties", properties), update.asMap());
    assertEquals(rootKey, properties.get("key"));
    var root = new Neo4jNode(rootKey, "AstResult", properties);

    var nodeBatches = write.statements.stream().filter(statement -> AST_NODE_INSERT.matcher(statement.query()).matches()).toList();
    var relationshipBatches = write.statements.stream().filter(statement -> AST_RELATIONSHIP_INSERT.matcher(statement.query()).matches()).toList();
    // A first save adds one lock, two reconciliation reads, and one root update to the insert batches.
    assertEquals(4 + nodeBatches.size() + relationshipBatches.size(), write.statements.size());
    var insertedNodes = nodeBatches.stream().flatMap(statement -> AST_NODE_INSERT.matcher(statement.query()).results()
        .flatMap(match -> statement.parameters().get("nodes").asList(value -> value.asMap()).stream()
            .map(row -> new Neo4jNode((String) row.get("key"), match.group(1), row))));
    var nodes = Stream.concat(Stream.of(root), insertedNodes).toList();
    var relationships = relationshipBatches.stream()
        .flatMap(statement -> AST_RELATIONSHIP_INSERT.matcher(statement.query()).results()
            .flatMap(match -> statement.parameters().get("relationships").asList(value -> value).stream().map(row -> {
              var values = row.get("properties").asMap();
              return new Neo4jRelationship((String) values.get("key"), match.group(1), nodeKey(row.get("source").asString()),
                  nodeKey(row.get("target").asString()), values);
            }))).toList();
    return new Neo4jAstProjection(identity, nodes, relationships);
  }

  private static String nodeKey(String address) {
    assertTrue(address.startsWith("node/"), address);
    return address.substring("node/".length());
  }
}
