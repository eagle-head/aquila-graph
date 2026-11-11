package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.CommitKnowledge;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.PublicationOutcome;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.cli.CommandExitPolicy;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandRunner;
import org.aquilagraph.cli.CommandInvocation;
import org.aquilagraph.cli.ChannelEmission;
import org.aquilagraph.cli.AnalyzeCommandOutcome;
import org.aquilagraph.cli.CommandPresentationException;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.ResultSummary;

class Neo4jAnalysisRunRepositoryTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenUnacknowledgedDriverDoubleWrite_whenOutputIndependentCommandExecutes_thenPublicOutcomeRetainsExactRepositoryObservation()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "interface Example { static void run() {} }");
    var runDriver = new DriverDouble();
    var astDriver = new AstRepositoryDriverStub();
    var signal = new Neo4jException("Managed write observation");
    runDriver.acknowledgementFailure = signal;
    var repositories = publications(runDriver, astDriver);
    var application = AnalysisCapabilities.execution(AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard()),
        repositories.astRepositories(), repositories.runRepositories());
    var command = new AnalyzeCommand(application);

    // Act
    var outcome = command.execute(temporaryDirectory.toString(), "--persist");

    // Assert
    var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, outcome).execution();
    var publication = execution.publication();
    assertEquals(PublicationStatus.FAILED, publication.status());
    assertEquals(CommitKnowledge.UNKNOWN, publication.knowledge());
    assertEquals(RepositoryOperationContext.saveCompletedRun(execution.run().identity().key()), publication.failure().orElseThrow().carrier().context());
    assertSame(signal, publication.failure().orElseThrow().effectivePrimary().getCause());
    assertEquals(1, runDriver.attempts.size());
    assertEquals(List.of("schema.close", "publication.close", "driver.close"), runDriver.effects);
  }

  @Test
  void givenAcknowledgedDriverDoubleWriteAndProjectionFault_whenCommandRuns_thenCrossPackageContextRetainsKnownCommitAndOriginalFailure()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "interface Example { static void run() {} }");
    var runDriver = new DriverDouble();
    var astDriver = new AstRepositoryDriverStub();
    runDriver.driverCloseFailure = new Neo4jException("Driver closure observation");
    var repositories = publications(runDriver, astDriver);
    var application = AnalysisCapabilities.execution(AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard()),
        repositories.astRepositories(), repositories.runRepositories());
    var original = new AtomicReference<AnalysisExecution>();
    var fault = new AnalysisInputException("Projection observation", new IOException("detail"));
    var command = new AnalyzeCommand(request -> {
      var execution = application.execute(request);
      original.set(execution);
      return execution;
    }, JavaRelease.JAVA_25, carrier -> { throw fault; });
    var output = new ByteArrayOutputStream();
    var error = new ByteArrayOutputStream();

    // Act
    try (var stdout = new PrintStream(output, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(error, true, StandardCharsets.UTF_8)) {
      var failure = assertThrows(CommandPresentationException.class,
          () -> new CommandRunner(command).run(new String[] {temporaryDirectory.toString(), "--persist"}, CommandDestination.printStream(stdout), CommandDestination.printStream(stderr)).exitCode());

      // Assert
      assertSame(fault, failure.getCause());
      assertEquals(CommandPresentationException.Phase.PROJECTION, failure.phase());
      assertFalse(failure.reportEmissionStarted());
      assertEquals(Optional.empty(), failure.channel());
      assertEquals(Optional.empty(), failure.progress());
      var retained = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, failure.outcome());
      assertSame(original.get(), retained.execution());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, retained.execution().publication().knowledge());
      assertEquals(PublicationStatus.FAILED, retained.execution().publication().status());
      assertEquals(1, runDriver.attempts.size());
      assertEquals(0, output.size());
      assertEquals(0, error.size());
    }
  }

  @Test
  void givenDriverCreationFailure_whenRepositoryOpens_thenNoDriverCleanupIsInvented() {
    // Arrange
    var primary = new IllegalStateException("Driver was not returned");
    var settings = settings();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> Neo4jAnalysisRunRepository.factory(() -> settings, (uri, auth) -> { throw primary; }).open(acquisition()));

    // Assert
    assertSame(primary, failure.effectivePrimary());
    assertEquals(0, failure.getSuppressed().length);
  }

  @Test
  void givenSetupAndCleanupFailuresAfterDriverReturn_whenRepositoryOpens_thenPrimaryAndSuppressedCleanupArePreserved() {
    // Arrange
    var backend = new DriverDouble();
    var primary = new IllegalStateException("Session configuration failed");
    var cleanup = new Neo4jException("Driver cleanup failed");
    backend.driverCloseFailure = cleanup;

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> Neo4jAnalysisRunRepository.factory(Neo4jAnalysisRunRepositoryTest::settings, ignored -> backend.driver(), ignored -> { throw primary; }).open(acquisition()));

    // Assert
    assertSame(primary, failure.effectivePrimary());
    assertEquals(List.of("driver.close"), backend.effects);
    assertEquals(1, failure.getSuppressed().length);
    var close = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]);
    assertInstanceOf(RepositoryException.class, close.effectivePrimary());
    assertSame(cleanup, close.effectivePrimary().getCause());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, close.phase());
    assertEquals(acquisition(), close.context());
    assertEquals(CommitKnowledge.NOT_STARTED, close.knowledge().orElseThrow());
    assertFalse(backend.publicationStarted);
  }

  @Test
  void givenNeo4jSetupPrimaryAndUnmatchedCleanupFailure_whenRepositoryOpens_thenTranslationOwnsTheSuppressedCleanup() {
    // Arrange
    var backend = new DriverDouble();
    var primary = new Neo4jException("Session configuration failed");
    var cleanup = new IllegalStateException("Driver cleanup failed");
    backend.driverCloseFailure = cleanup;

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> Neo4jAnalysisRunRepository.factory(Neo4jAnalysisRunRepositoryTest::settings, ignored -> backend.driver(), ignored -> { throw primary; }).open(acquisition()));

    // Assert
    assertEquals("Neo4j driver initialization failed", assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).description());
    assertSame(primary, failure.effectivePrimary().getCause());
    assertSame(cleanup, assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]).effectivePrimary());
    assertEquals(0, primary.getSuppressed().length);
    assertEquals(List.of("driver.close"), backend.effects);
  }

  @Test
  void givenSetupErrorAfterDriverReturn_whenRepositoryOpens_thenGuardClosesTheDriverAndPreservesTheError() {
    // Arrange
    var backend = new DriverDouble();
    var primary = new AssertionError("Session configuration failed");

    // Act
    var failure = assertThrows(AssertionError.class,
        () -> Neo4jAnalysisRunRepository.factory(Neo4jAnalysisRunRepositoryTest::settings, ignored -> backend.driver(), ignored -> { throw primary; }).open(acquisition()));

    // Assert
    assertSame(primary, failure);
    assertEquals(List.of("driver.close"), backend.effects);
    assertFalse(backend.publicationStarted);
  }

  @Test
  void givenSameFatalErrorInPartialSetupAndCleanup_whenRepositoryOpens_thenDriverClosesOnceAndErrorIdentitySurvives() {
    // Arrange
    var primary = new AssertionError("Shared setup and cleanup failure");
    var closes = new AtomicInteger();
    var driver = proxy(Driver.class, (method, arguments) -> {
      assertEquals("close", method);
      closes.incrementAndGet();
      throw primary;
    });
    var factory = Neo4jAnalysisRunRepository.factory(Neo4jAnalysisRunRepositoryTest::settings, ignored -> driver, ignored -> {
      throw primary;
    });

    // Act
    var failure = assertThrows(AssertionError.class, () -> factory.open(acquisition()));

    // Assert
    assertSame(primary, failure);
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(1, closes.get());
  }

  @Test
  void givenWrongNamedConstraint_whenRunIsSaved_thenSchemaClosesAndPublicationIsSkipped() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    backend.definitions = List.of(definition("analysis_entity_identity", "AnalysisEntity", List.of("key", "runId")),
        definition("analysis_run_identity", "AnalysisRun", List.of("runId")));
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Neo4j repository schema definition mismatch", assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).description());
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("schema.close", "driver.close"), backend.effects);
  }

  @Test
  void givenMissingOrIncompatibleNamedDefinitions_whenRunIsSaved_thenEveryMismatchBlocksPublication() throws IOException {
    // Arrange
    var run = completedRun();
    var entity = definition("analysis_entity_identity", "AnalysisEntity", List.of("runId", "key"));
    var incompatible = List.of(List.of(entity), List.of(entity, entity),
        List.of(entity, definition("other_run_identity", "AnalysisRun", List.of("runId"))),
        List.of(entity, definition("analysis_run_identity", "UNIQUENESS", "NODE", "AnalysisRun", List.of("runId"))),
        List.of(entity, definition("analysis_run_identity", "NODE_PROPERTY_UNIQUENESS", "RELATIONSHIP", "AnalysisRun", List.of("runId"))),
        List.of(entity, definition("analysis_run_identity", "DifferentRun", List.of("runId"))),
        List.of(entity, definition("analysis_run_identity", "AnalysisRun", List.of("key"))));
    for (var definitions : incompatible) {
      var backend = new DriverDouble();
      backend.definitions = definitions;
      var repository = repository(backend);

      // Act
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
      repository.close();

      // Assert
      assertEquals("Neo4j repository schema definition mismatch", assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).description());
      assertFalse(backend.publicationStarted);
      assertEquals(List.of("schema.close", "driver.close"), backend.effects);
    }
  }

  @Test
  void givenUnavailableSchemaMetadataAndFailedClose_whenRunIsSaved_thenTranslatedPrimaryRetainsCleanup() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    var primary = new Neo4jException("Metadata access unavailable");
    var cleanup = new Neo4jException("Schema close failed");
    backend.metadataFailure = primary;
    backend.schemaCloseFailure = cleanup;
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.effectivePrimary().getCause());
    assertSame(cleanup, assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]).effectivePrimary().getCause());
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("schema.close", "driver.close"), backend.effects);
  }

  @Test
  void givenSchemaOrPublicationSessionAcquisitionFailure_whenRunIsSaved_thenOnlyAcquiredSessionsAreClosed() throws IOException {
    // Arrange
    var run = completedRun();
    for (var failedSession : List.of(1, 2)) {
      var backend = new DriverDouble();
      var primary = new Neo4jException("Session acquisition failed");
      backend.failedSession = failedSession;
      backend.sessionFailure = primary;
      var repository = repository(backend);

      // Act
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
      repository.close();

      // Assert
      assertSame(primary, failure.effectivePrimary().getCause());
      assertFalse(backend.publicationStarted);
      assertEquals(failedSession, backend.sessions);
      assertEquals(failedSession == 1 ? List.of("driver.close") : List.of("schema.close", "driver.close"), backend.effects);
    }
  }

  @Test
  void givenSchemaMismatchAndFailedClose_whenRunIsSaved_thenMismatchRetainsTheTypedCleanupObservation() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    backend.definitions = List.of();
    var cleanup = new Neo4jException("Schema close failed");
    backend.schemaCloseFailure = cleanup;
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Neo4j repository schema definition mismatch", assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).description());
    assertInstanceOf(IllegalStateException.class, failure.effectivePrimary().getCause());
    assertSame(cleanup, assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]).effectivePrimary().getCause());
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("schema.close", "driver.close"), backend.effects);
  }

  @Test
  void givenCorrectAndIncompatibleDefinitionsWithOneName_whenRunIsSaved_thenDuplicateNameBlocksPublication() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    backend.definitions = List.of(definition("analysis_entity_identity", "AnalysisEntity", List.of("runId", "key")),
        definition("analysis_entity_identity", "AnotherEntity", List.of("runId", "key")),
        definition("analysis_run_identity", "AnalysisRun", List.of("runId")));
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Neo4j repository schema definition mismatch", assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).description());
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("schema.close", "driver.close"), backend.effects);
  }

  @Test
  void givenFirstOrSecondSchemaStatementFailure_whenRunIsSaved_thenLaterPublicationIsSkippedAndPrimaryIsRetained() throws IOException {
    // Arrange
    var run = completedRun();
    for (var failedStatement : List.of(1, 2)) {
      var backend = new DriverDouble();
      backend.failedSchemaStatement = failedStatement;
      var primary = new Neo4jException("Constraint statement failed");
      backend.schemaStatementFailure = primary;
      var repository = repository(backend);

      // Act
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
      repository.close();

      // Assert
      assertSame(primary, failure.effectivePrimary().getCause());
      assertEquals(failedStatement, backend.schemaStatements);
      assertFalse(backend.publicationStarted);
      assertEquals(List.of("schema.close", "driver.close"), backend.effects);
    }
  }

  @Test
  void givenCorrectDefinitionsAndFailedSchemaClose_whenRunIsSaved_thenPublicationIsSkipped() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    var primary = new Neo4jException("Schema session close failed");
    backend.schemaCloseFailure = primary;
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.effectivePrimary().getCause());
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("schema.close", "driver.close"), backend.effects);
  }

  @Test
  void givenNormalWriteReturnAndFailedPublicationClose_whenRunIsSaved_thenCleanupFailurePreventsNormalSaveReturn() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    var primary = new Neo4jException("Publication session close failed");
    backend.publicationCloseFailure = primary;
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.effectivePrimary().getCause());
    assertTrue(backend.publicationStarted);
    assertEquals(1, backend.attempts.size());
    assertEquals(List.of("schema.close", "publication.close", "driver.close"), backend.effects);
  }

  @Test
  void givenUnmatchedWritePrimaryAndPublicationCleanupFailure_whenRunIsSaved_thenActualPrimaryFamilyAndSuppressionRemain() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    var primary = new IllegalStateException("Publication failed");
    var cleanup = new Neo4jException("Publication close failed");
    backend.publicationFailure = primary;
    backend.publicationCloseFailure = cleanup;
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.effectivePrimary());
    assertSame(cleanup, assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]).effectivePrimary().getCause());
    assertEquals(List.of("schema.close", "publication.close", "driver.close"), backend.effects);
  }

  @Test
  void givenCorrectConstraintsAndReplayedCallback_whenRunIsSaved_thenEachAttemptUsesTheSameCompleteProjection() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    backend.replay = true;
    var repository = repository(backend);
    var run = completedRun();

    // Act
    repository.save(run);
    repository.close();

    // Assert
    assertTrue(backend.publicationStarted);
    assertEquals(2, backend.attempts.size());
    assertEquals(backend.attempts.getFirst(), backend.attempts.getLast());
    assertTrue(backend.attempts.getFirst().getFirst().statement().startsWith("MATCH (entity:AnalysisEntity"));
    assertEquals(Map.of("runId", run.identity().key()), backend.attempts.getFirst().getFirst().parameters());
    assertEquals(new Neo4jAnalysisRunMapper().map(run).nodes().size() + new Neo4jAnalysisRunMapper().map(run).relationships().size() + 1,
        backend.attempts.getFirst().size());
    assertEquals(List.of("schema.close", "publication.close", "driver.close"), backend.effects);
  }

  @Test
  void givenNoncompletedRun_whenSaveIsRequested_thenMappingRefusesBeforeSchemaOrPublication() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    var repository = repository(backend);
    var run = RepositoryTestRuns.analyze(temporaryDirectory, "Example.java", "");

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Only completed analysis runs may be stored", failure.effectivePrimary().getMessage());
    assertEquals(0, backend.sessions);
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("driver.close"), backend.effects);
  }

  @Test
  void givenNullRun_whenSaveIsRequested_thenAdmissionFailsBeforeSessions() {
    // Arrange
    var backend = new DriverDouble();
    var repository = repository(backend);

    // Act
    var failure = assertThrows(NullPointerException.class, () -> repository.save(null));
    repository.close();

    // Assert
    assertEquals("run", failure.getMessage());
    assertEquals(0, backend.sessions);
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("driver.close"), backend.effects);
  }

  @Test
  void givenProjectionValidationFailure_whenRunIsSaved_thenOriginalFamilyPropagatesBeforeSessions() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    var primary = new IllegalArgumentException("Projection is inconsistent");
    var repository = new Neo4jAnalysisRunRepository(backend.driver(), SessionConfig.defaultConfig(), acquisition(), ignored -> { throw primary; });
    var run = completedRun();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.effectivePrimary());
    assertEquals(0, backend.sessions);
    assertFalse(backend.publicationStarted);
    assertEquals(List.of("driver.close"), backend.effects);
  }

  @Test
  void givenDriverCloseFailure_whenRepositoryCloses_thenNamedShutdownTranslationPreservesTheCause() {
    // Arrange
    var backend = new DriverDouble();
    var primary = new Neo4jException("Driver close failed");
    backend.driverCloseFailure = primary;
    var repository = repository(backend);

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, repository::close);

    // Assert
    assertEquals("Neo4j driver shutdown failed", assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).description());
    assertSame(primary, failure.effectivePrimary().getCause());
    assertEquals(acquisition(), failure.context());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, failure.phase());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
    assertEquals(List.of("driver.close"), backend.effects);
  }

  @Test
  void givenForeignOperationKinds_whenOpeningEachFactory_thenRejectionPrecedesSettingsResolution() throws IOException {
    // Arrange
    var run = completedRun();
    var identity = run.representation().ast().result().orElseThrow().identity();
    var saveAst = RepositoryOperationContext.saveAst(identity);
    var queryAst = RepositoryOperationContext.queryAst(identity);
    var saveRun = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var settingsResolutions = new AtomicInteger();
    Supplier<Neo4jConnectionSettings> settings = () -> {
      settingsResolutions.incrementAndGet();
      throw new AssertionError("An inadmissible operation cannot resolve settings");
    };
    var retentionFactory = Neo4jRepositories.astRetention(settings);
    var queryFactory = Neo4jRepositories.astQuery(settings);
    var runFactory = Neo4jRepositories.completedRuns(settings);

    // Act
    assertThrows(IllegalArgumentException.class, () -> retentionFactory.open(queryAst));
    assertThrows(IllegalArgumentException.class, () -> retentionFactory.open(saveRun));
    assertThrows(IllegalArgumentException.class, () -> queryFactory.open(saveAst));
    assertThrows(IllegalArgumentException.class, () -> queryFactory.open(saveRun));
    assertThrows(IllegalArgumentException.class, () -> runFactory.open(saveAst));
    assertThrows(IllegalArgumentException.class, () -> runFactory.open(queryAst));

    // Assert
    assertEquals(0, settingsResolutions.get());
  }

  @Test
  void givenDeclaredSetupFailureAtEachAcquisitionStage_whenPublishingBothSubjects_thenClassificationAndPartialOwnershipArePreserved()
      throws IOException {
    // Arrange
    var run = completedRun();
    var astContext = RepositoryOperationContext.saveAst(run.representation().ast().result().orElseThrow().identity());
    var runContext = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var configuration = new IllegalArgumentException("Invalid connection input");
    var driverFailure = new Neo4jException("Driver initialization failed");
    for (var stage : AcquisitionStage.values()) {
      for (var signal : List.of(configuration, driverFailure)) {
        var closed = new ArrayList<String>();
        var services = failingAcquisition(stage, Optional.of(signal), closed);

        // Act
        var outcomes = Map.of(astContext, services.execute(run, PublicationIntent.SAVE_COMPLETE_AST), runContext, services.execute(run, PublicationIntent.SAVE_COMPLETED));

        // Assert
        for (var entry : outcomes.entrySet()) {
          var outcome = entry.getValue();
          assertEquals(PublicationStatus.FAILED, outcome.status());
          assertEquals(CommitKnowledge.NOT_STARTED, outcome.knowledge());
          assertEquals(entry.getKey().subject(), outcome.subjectIdentity().orElseThrow());
          var evidence = assertInstanceOf(Neo4jRepositoryOperationException.class, outcome.failure().orElseThrow().carrier());
          assertEquals(entry.getKey(), evidence.context());
          assertEquals(Neo4jRepositoryPhase.ACQUISITION, evidence.phase());
          assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), evidence.knowledge());
          var declared = assertInstanceOf(RepositoryException.class, evidence.effectivePrimary());
          assertSame(signal, declared.getCause());
          assertEquals(signal == configuration ? "Invalid Neo4j repository configuration" : "Neo4j driver initialization failed",
              declared.description());
        }
        assertEquals(stage.expectedClosures(), closed);
      }
    }
  }

  @Test
  void givenUnmatchedOrNullSetupAtEachAcquisitionStage_whenPublishingBothSubjects_thenAbruptEvidenceAndPartialOwnershipArePreserved()
      throws IOException {
    // Arrange
    var run = completedRun();
    var astContext = RepositoryOperationContext.saveAst(run.representation().ast().result().orElseThrow().identity());
    var runContext = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var unmatched = new IllegalStateException("Unexpected acquisition exit");
    for (var stage : AcquisitionStage.values()) {
      for (var signal : List.<Optional<RuntimeException>>of(Optional.of(unmatched), Optional.empty())) {
        var closed = new ArrayList<String>();
        var services = failingAcquisition(stage, signal, closed);

        // Act
        var astFailure = assertThrows(AnalysisPublicationException.class, () -> services.execute(run, PublicationIntent.SAVE_COMPLETE_AST));
        var runFailure = assertThrows(AnalysisPublicationException.class, () -> services.execute(run, PublicationIntent.SAVE_COMPLETED));

        // Assert
        for (var entry : Map.of(astContext, astFailure, runContext, runFailure).entrySet()) {
          var failure = entry.getValue();
          assertSame(run, assertInstanceOf(Coordinated.class, failure.subject()).run());
          assertEquals(entry.getKey(), failure.context());
          var evidence = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.failure());
          assertEquals(entry.getKey(), evidence.context());
          assertEquals(Neo4jRepositoryPhase.ACQUISITION, evidence.phase());
          assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), evidence.knowledge());
          assertSame(evidence.effectivePrimary(), failure.getCause());
          if (signal.isPresent()) {
            assertSame(unmatched, failure.getCause());
          } else {
            assertInstanceOf(NullPointerException.class, failure.getCause());
          }
          assertEquals(0, evidence.getSuppressed().length);
        }
        assertEquals(stage.expectedClosures(), closed);
      }
    }
  }

  private static PublicationServices failingAcquisition(AcquisitionStage stage, Optional<RuntimeException> signal, List<String> closed) {
    Supplier<Neo4jConnectionSettings> settings = () -> stage == AcquisitionStage.SETTINGS ? failAcquisition(signal) : settings();
    if (stage == AcquisitionStage.SESSION_CONFIGURATION) {
      Function<Neo4jConnectionSettings, Driver> drivers = ignored -> proxy(Driver.class, (method, arguments) -> {
        assertEquals("close", method);
        closed.add(method);
        return null;
      });
      Function<Neo4jConnectionSettings, SessionConfig> configurations = ignored -> failAcquisition(signal);
      return new PublicationServices(new Neo4jAstResultRepositoryFactory(settings, drivers, configurations)::openRetention,
          Neo4jAnalysisRunRepository.factory(settings, drivers, configurations));
    }
    BiFunction<URI, AuthToken, Driver> drivers = (uri, auth) -> {
      assertEquals(AcquisitionStage.DRIVER, stage);
      return failAcquisition(signal);
    };
    return new PublicationServices(new Neo4jAstResultRepositoryFactory(settings, drivers)::openRetention,
        Neo4jAnalysisRunRepository.factory(settings, drivers));
  }

  private static <T> T failAcquisition(Optional<RuntimeException> signal) {
    if (signal.isPresent()) {
      throw signal.orElseThrow();
    }
    return null;
  }

  private record PublicationServices(AstRetentionRepositoryFactory astRepositories, AnalysisRunRepositoryFactory runRepositories) {
    PublicationOutcome execute(AnalysisRun run, PublicationIntent intent) {
      return AnalysisExecutionFixture.executeComputed(run, intent, astRepositories, runRepositories).publication();
    }
  }

  private enum AcquisitionStage {
    SETTINGS, DRIVER, SESSION_CONFIGURATION;

    List<String> expectedClosures() {
      return this == SESSION_CONFIGURATION ? List.of("close", "close") : List.of();
    }
  }

  @Test
  void givenMappingRejectsBothProjections_whenPublishing_thenAbruptFailuresRetainTheComputedRunBeforeAnySession() throws IOException {
    // Arrange
    var run = completedRun();
    var astContext = RepositoryOperationContext.saveAst(run.representation().ast().result().orElseThrow().identity());
    var runContext = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var primary = new IllegalArgumentException("Projection mapping rejected the value");
    var runDriver = new DriverDouble();
    var astDriver = new AstRepositoryDriverStub();
    AstRetentionRepositoryFactory astRepositories = context -> new Neo4jAstResultRepository(astDriver.driver(),
        SessionConfig.defaultConfig(), context, new Neo4jAstResultCodec(), ignored -> { throw primary; });
    AnalysisRunRepositoryFactory completed = context -> new Neo4jAnalysisRunRepository(runDriver.driver(),
        SessionConfig.defaultConfig(), context, ignored -> { throw primary; });

    // Act
    var astFailure = assertThrows(AnalysisPublicationException.class, () -> AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, astRepositories,
        AnalysisExecutionFixture::unexpectedRunRepository).publication());
    var runFailure = assertThrows(AnalysisPublicationException.class, () -> AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETED,
        AnalysisExecutionFixture::unexpectedAstRepository, completed).publication());

    // Assert
    assertAbruptPublication(astContext, astFailure, run, Neo4jRepositoryPhase.MAPPING, CommitKnowledge.NOT_STARTED, primary);
    assertAbruptPublication(runContext, runFailure, run, Neo4jRepositoryPhase.MAPPING, CommitKnowledge.NOT_STARTED, primary);
    assertEquals(List.of("driver.close"), astDriver.events);
    assertEquals(List.of("driver.close"), runDriver.effects);
    assertEquals(0, runDriver.sessions);
    assertTrue(astDriver.queries.isEmpty());
  }

  @Test
  void givenProjectionForAnotherCompletedRun_whenPublishing_thenMappingRejectsItBeforeOpeningStorage() throws IOException {
    // Arrange
    var run = completedRun();
    var foreign = RepositoryTestRuns.analyze(temporaryDirectory, "Other.java", "interface Other {}");
    var projection = new Neo4jAnalysisRunMapper().map(foreign);
    var requested = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var backend = new DriverDouble();
    AnalysisRunRepositoryFactory service = context -> new Neo4jAnalysisRunRepository(backend.driver(),
        SessionConfig.defaultConfig(), context, ignored -> projection);

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETED,
        AnalysisExecutionFixture::unexpectedAstRepository, service).publication());

    // Assert
    var primary = assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertAbruptPublication(requested, failure, run, Neo4jRepositoryPhase.MAPPING, CommitKnowledge.NOT_STARTED, primary);
    assertEquals(0, backend.sessions);
    assertEquals(List.of("driver.close"), backend.effects);
  }

  @Test
  void givenSchemaOrSchemaClosureFails_whenPublishingBothSubjects_thenDeclaredFailuresPrecedeReplacement() throws IOException {
    // Arrange
    var run = completedRun();
    var astContext = RepositoryOperationContext.saveAst(run.representation().ast().result().orElseThrow().identity());
    var runContext = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var primary = new Neo4jException("Schema operation failed");
    for (var phase : List.of(Neo4jRepositoryPhase.SCHEMA, Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE)) {
      var runDriver = new DriverDouble();
      var astDriver = new AstRepositoryDriverStub();
      if (phase == Neo4jRepositoryPhase.SCHEMA) {
        runDriver.metadataFailure = primary;
        astDriver.schemaFailure = primary;
      } else {
        runDriver.schemaCloseFailure = primary;
        astDriver.failingClose = 0;
        astDriver.closeFailure = primary;
      }
      var services = publications(runDriver, astDriver);

      // Act
      var outcomes = Map.of(astContext, services.execute(run, PublicationIntent.SAVE_COMPLETE_AST), runContext, services.execute(run, PublicationIntent.SAVE_COMPLETED));

      // Assert
      for (var entry : outcomes.entrySet()) {
        var outcome = entry.getValue();
        assertEquals(PublicationStatus.FAILED, outcome.status());
        assertEquals(entry.getKey().subject(), outcome.subjectIdentity().orElseThrow());
        assertEquals(CommitKnowledge.NOT_STARTED, outcome.knowledge());
        var evidence = assertInstanceOf(Neo4jRepositoryOperationException.class, outcome.failure().orElseThrow().carrier());
        assertEquals(entry.getKey(), evidence.context());
        assertEquals(phase, evidence.phase());
        assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), evidence.knowledge());
        assertSame(primary, assertInstanceOf(RepositoryException.class, evidence.effectivePrimary()).getCause());
      }
      assertFalse(runDriver.publicationStarted);
      assertFalse(astDriver.events.contains("write.begin"));
      assertEquals(List.of("schema.close", "driver.close"), runDriver.effects);
      assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), astDriver.events);
    }
  }

  @Test
  void givenDeclaredOrUnmatchedDriverPrimaries_whenPublishingBothSubjects_thenEachPhasePreservesClassificationAndKnowledge()
      throws IOException {
    var run = completedRun();
    var astContext = RepositoryOperationContext.saveAst(run.representation().ast().result().orElseThrow().identity());
    var runContext = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var phases = Map.of(Neo4jRepositoryPhase.SCHEMA, CommitKnowledge.NOT_STARTED,
        Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE, CommitKnowledge.NOT_STARTED,
        Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN,
        Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE, CommitKnowledge.KNOWN_COMMIT,
        Neo4jRepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.KNOWN_COMMIT);
    var unmatched = new IllegalArgumentException("Unexpected driver operation exit");
    var declared = new RepositoryException("Declared repository failure", new IllegalStateException("Original cause"));

    for (var entry : phases.entrySet()) {
      var phase = entry.getKey();
      var knowledge = entry.getValue();
      for (var primary : List.<RuntimeException>of(unmatched, declared)) {
        // Arrange
        var runDriver = new DriverDouble();
        var astDriver = new AstRepositoryDriverStub();
        failAt(phase, primary, runDriver, astDriver);
        var services = publications(runDriver, astDriver);

        if (primary == declared) {
          // Act
          var astResult = services.execute(run, PublicationIntent.SAVE_COMPLETE_AST);
          var runResult = services.execute(run, PublicationIntent.SAVE_COMPLETED);

          // Assert
          assertDeclaredPublication(astContext, astResult, phase, knowledge, declared);
          assertDeclaredPublication(runContext, runResult, phase, knowledge, declared);
        } else {
          // Act
          var astFailure = assertThrows(AnalysisPublicationException.class, () -> services.execute(run, PublicationIntent.SAVE_COMPLETE_AST));
          var runFailure = assertThrows(AnalysisPublicationException.class, () -> services.execute(run, PublicationIntent.SAVE_COMPLETED));
          // Assert
          assertAbruptPublication(astContext, astFailure, run, phase, knowledge, primary);
          assertAbruptPublication(runContext, runFailure, run, phase, knowledge, primary);
        }

        var attempted = knowledge != CommitKnowledge.NOT_STARTED;
        assertEquals(attempted, astDriver.events.contains("write.begin"));
        assertEquals(attempted, runDriver.publicationStarted);
        assertEquals(attempted ? 1 : 0, runDriver.attempts.size());
        assertEquals(knowledge == CommitKnowledge.KNOWN_COMMIT, astDriver.events.contains("write.acknowledged"));
        assertEquals(attempted ? List.of("session.close:0", "session.close:1", "driver.close") : List.of("session.close:0", "driver.close"),
            astDriver.events.stream().filter(event -> event.contains(".close")).toList());
        assertEquals(attempted ? List.of("schema.close", "publication.close", "driver.close") : List.of("schema.close", "driver.close"),
            runDriver.effects);
      }
    }
  }

  private static void failAt(Neo4jRepositoryPhase phase, RuntimeException primary, DriverDouble runDriver,
      AstRepositoryDriverStub astDriver) {
    switch (phase) {
      case SCHEMA -> {
        runDriver.metadataFailure = primary;
        astDriver.schemaFailure = primary;
      }
      case SCHEMA_SESSION_CLOSE -> {
        runDriver.schemaCloseFailure = primary;
        astDriver.failingClose = 0;
        astDriver.closeFailure = primary;
      }
      case REPLACEMENT -> {
        runDriver.acknowledgementFailure = primary;
        astDriver.writeFailure = primary;
      }
      case PUBLICATION_SESSION_CLOSE -> {
        runDriver.publicationCloseFailure = primary;
        astDriver.failingClose = 1;
        astDriver.closeFailure = primary;
      }
      case REPOSITORY_CLOSE -> {
        runDriver.driverCloseFailure = primary;
        astDriver.driverCloseFailure = primary;
      }
      default -> throw new AssertionError("No driver failure scenario for " + phase);
    }
  }

  private static void assertDeclaredPublication(RepositoryOperationContext requested, PublicationOutcome result,
      Neo4jRepositoryPhase phase, CommitKnowledge knowledge, RepositoryException primary) {
    assertEquals(PublicationStatus.FAILED, result.status());
    assertEquals(Optional.of(requested.subject()), result.subjectIdentity());
    assertEquals(knowledge, result.knowledge());
    var evidence = assertInstanceOf(Neo4jRepositoryOperationException.class, result.failure().orElseThrow().carrier());
    assertEquals(requested, evidence.context());
    assertEquals(phase, evidence.phase());
    assertEquals(Optional.of(knowledge), evidence.knowledge());
    assertSame(primary, evidence.effectivePrimary());
    assertEquals(0, evidence.getSuppressed().length);
  }

  private static void assertAbruptPublication(RepositoryOperationContext requested, AnalysisPublicationException failure, AnalysisRun run,
      Neo4jRepositoryPhase phase, CommitKnowledge knowledge, RuntimeException primary) {
    assertSame(run, assertInstanceOf(Coordinated.class, failure.subject()).run());
    assertEquals(requested, failure.context());
    assertSame(primary, failure.getCause());
    var evidence = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.failure());
    assertEquals(requested, evidence.context());
    assertEquals(phase, evidence.phase());
    assertEquals(Optional.of(knowledge), evidence.knowledge());
    assertSame(primary, evidence.effectivePrimary());
    assertEquals(0, evidence.getSuppressed().length);
  }

  private static PublicationServices publications(DriverDouble runDriver, AstRepositoryDriverStub astDriver) {
    return new PublicationServices(
        new Neo4jAstResultRepositoryFactory(Neo4jAnalysisRunRepositoryTest::settings, (uri, auth) -> astDriver.driver())::openRetention,
        Neo4jAnalysisRunRepository.factory(Neo4jAnalysisRunRepositoryTest::settings, (uri, auth) -> runDriver.driver()));
  }

  @Test
  void givenLostManagedWriteAcknowledgement_whenBothSubjectsPublish_thenExactFailureIsReturnedAndCliPrintsItsCompletedAnalysis()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "interface Example { static void run() {} }");
    var runDriver = new DriverDouble();
    var astDriver = new AstRepositoryDriverStub();
    var signal = new Neo4jException("Managed write was not acknowledged");
    runDriver.acknowledgementFailure = signal;
    astDriver.writeFailure = signal;
    var services = publications(runDriver, astDriver);
    var application = AnalysisCapabilities.execution(AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard()), services.astRepositories(), services.runRepositories());
    var observed = new AtomicReference<AnalysisExecution>();
    var command = new AnalyzeCommand(request -> {
      var execution = application.execute(request);
      observed.set(execution);
      return execution;
    });
    var output = new ByteArrayOutputStream();
    var error = new ByteArrayOutputStream();
    final CommandInvocation invocation;

    // Act
    try (var stdout = new PrintStream(output, false, StandardCharsets.UTF_8);
        var stderr = new PrintStream(error, false, StandardCharsets.UTF_8)) {
      invocation = new CommandRunner(command).run(new String[] {temporaryDirectory.toString(), "--persist"}, CommandDestination.printStream(stdout), CommandDestination.printStream(stderr));
    }
    var execution = observed.get();
    var run = execution.run();
    var ast = run.representation().ast();
    var astPublication = services.execute(run, PublicationIntent.SAVE_COMPLETE_AST);

    // Assert
    var astContext = RepositoryOperationContext.saveAst(ast.result().orElseThrow().identity());
    var runContext = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var expected = Map.of(astContext, astPublication, runContext, execution.publication());
    for (var entry : expected.entrySet()) {
      var result = entry.getValue();
      assertEquals(PublicationStatus.FAILED, result.status());
      assertEquals(CommitKnowledge.UNKNOWN, result.knowledge());
      assertEquals(entry.getKey().subject(), result.subjectIdentity().orElseThrow());
      var evidence = assertInstanceOf(Neo4jRepositoryOperationException.class, result.failure().orElseThrow().carrier());
      assertEquals(entry.getKey(), evidence.context());
      assertEquals(Neo4jRepositoryPhase.REPLACEMENT, evidence.phase());
      assertSame(signal, evidence.effectivePrimary().getCause());
    }
    assertEquals(1, runDriver.attempts.size());
    assertTrue(astDriver.events.contains("callback.return"));
    assertEquals(List.of("schema.close", "publication.close", "driver.close"), runDriver.effects);
    assertEquals("driver.close", astDriver.events.getLast());
    assertEquals(CommandExitPolicy.EXIT_REPOSITORY_FAILED, invocation.exitCode());
    assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution());
    assertEquals(ChannelEmission.Status.COMPLETED, invocation.emission().output());
    assertEquals(ChannelEmission.Status.COMPLETED, invocation.emission().error());
    var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
    var identity = ast.result().orElseThrow().identity();
    var methodKey = ast.result().orElseThrow().program().methods().getFirst().methodKey();
    assertEquals(List.of("Run: \"" + run.identity().key() + "\"", "Software version: \"" + identity.softwareSignature() + "\"",
        "Completion: COMPLETE", "Java release: 25", "Java source files: 1", "AST: COMPLETE", "AST profile: \"java-source-bounded/1\"",
        "CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/2\"", "CFG selection: ALL_BODIES",
        "CFG binding: \"" + identity.astResultKey() + "\"", "Selected method: \"" + methodKey + "\"", "Java types: 1", "Java methods: 1",
        "Analyzed method bodies: 1", "CFG operations: 0", "CFG nodes: 2", "CFG edges: 1", "Method CFG: \"" + methodKey + "\" AVAILABLE",
        "Repository: FAILED", "Publication intent: SAVE_COMPLETED", "Publication subject: COMPLETED_RUN \"" + run.identity().key() + "\"",
        "Commit knowledge: UNKNOWN"), lines);
    assertEquals(List.of("Repository save failed: \"Neo4j repository save failed\"", "Failure reference: 1",
        "Failure: 1", "  Type: \"org.aquilagraph.application.PublicationFailure\"", "  Message: \"Publication failed\"",
        "  Cause: 2", "  Carrier: 2", "  Effective primary: 3", "  Suppressed: none", "  Operation: not-applicable",
        "Failure: 2", "  Type: \"org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException\"", "  Message: \"Repository operation failed\"",
        "  Cause: 3", "  Carrier: not-applicable", "  Effective primary: 3", "  Suppressed: none", "  Operation: SAVE_COMPLETED_RUN",
        "  Operation subject: COMPLETED_RUN \"" + run.identity().key() + "\"", "  Commit knowledge: UNKNOWN", "  Application boundary: not-applicable",
        "  Adapter: not-applicable", "  Adapter phase: not-applicable",
        "Failure: 3", "  Type: \"org.aquilagraph.application.port.RepositoryException\"", "  Message: \"Neo4j repository save failed\"",
        "  Cause: 4", "  Carrier: not-applicable", "  Effective primary: not-applicable", "  Suppressed: none", "  Operation: not-applicable",
        "Failure: 4", "  Type: \"org.neo4j.driver.exceptions.Neo4jException\"", "  Message: \"Managed write was not acknowledged\"",
        "  Cause: unavailable", "  Carrier: not-applicable", "  Effective primary: not-applicable", "  Suppressed: none", "  Operation: not-applicable"),
        error.toString(StandardCharsets.UTF_8).lines().toList());
  }

  @Test
  void givenAcknowledgedWrites_whenPublishingBothSubjects_thenEachClosePhaseRetainsCommitAndNormalClosureReturnsSaved() throws IOException {
    var run = completedRun();
    var ast = run.representation().ast();
    var astContext = RepositoryOperationContext.saveAst(ast.result().orElseThrow().identity());
    var runContext = RepositoryOperationContext.saveCompletedRun(run.identity().key());

    for (var failedPhase : List.of(Optional.<Neo4jRepositoryPhase>empty(),
        Optional.of(Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE), Optional.of(Neo4jRepositoryPhase.REPOSITORY_CLOSE))) {
      // Arrange
      var runDriver = new DriverDouble();
      var astDriver = new AstRepositoryDriverStub();
      var failure = new Neo4jException("Close failed after managed write acknowledgement");
      if (failedPhase.equals(Optional.of(Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE))) {
        runDriver.publicationCloseFailure = failure;
        astDriver.failingClose = 1;
        astDriver.closeFailure = failure;
      }
      if (failedPhase.equals(Optional.of(Neo4jRepositoryPhase.REPOSITORY_CLOSE))) {
        runDriver.driverCloseFailure = failure;
        astDriver.driverCloseFailure = failure;
      }
      var services = publications(runDriver, astDriver);

      // Act
      var decisions = Map.of(astContext, services.execute(run, PublicationIntent.SAVE_COMPLETE_AST), runContext, services.execute(run, PublicationIntent.SAVE_COMPLETED));

      // Assert
      for (var entry : decisions.entrySet()) {
        var result = entry.getValue();
        assertSame(run, new AnalysisExecution(run, result).run());
        assertSame(ast, run.representation().ast());
        assertEquals(entry.getKey().subject(), result.subjectIdentity().orElseThrow());
        assertEquals(CommitKnowledge.KNOWN_COMMIT, result.knowledge());
        if (failedPhase.isEmpty()) {
          assertEquals(PublicationStatus.SAVED, result.status());
          assertTrue(result.failure().isEmpty());
        } else {
          assertEquals(PublicationStatus.FAILED, result.status());
          var evidence = assertInstanceOf(Neo4jRepositoryOperationException.class, result.failure().orElseThrow().carrier());
          assertEquals(entry.getKey(), evidence.context());
          assertEquals(failedPhase.orElseThrow(), evidence.phase());
          assertEquals(Optional.of(CommitKnowledge.KNOWN_COMMIT), evidence.knowledge());
          var primary = assertInstanceOf(RepositoryException.class, evidence.effectivePrimary());
          assertSame(failure, primary.getCause());
        }
      }
      assertEquals(1, runDriver.attempts.size());
      var replacement = runDriver.attempts.getFirst();
      assertTrue(replacement.getFirst().statement().startsWith("MATCH (entity:AnalysisEntity"));
      assertEquals(Map.of("runId", run.identity().key()), replacement.getFirst().parameters());
      var projection = new Neo4jAnalysisRunMapper().map(run);
      assertEquals(projection.nodes().size() + projection.relationships().size() + 1, replacement.size());
      assertEquals(List.of("schema.close", "publication.close", "driver.close"), runDriver.effects);
      assertTrue(astDriver.events.contains("write.acknowledged"));
      assertEquals(List.of("session.close:0", "session.close:1", "driver.close"),
          astDriver.events.stream().filter(event -> event.contains(".close")).toList());
    }
  }

  @Test
  void givenAcknowledgedFirstRunAndInconclusiveSecondRun_whenRepositoryCloses_thenLastSubjectAndItsKnowledgeAreRetained()
      throws IOException {
    // Arrange
    var backend = new DriverDouble();
    var first = completedRun();
    var second = RepositoryTestRuns.analyze(temporaryDirectory, "Other.java", "interface Other {}");
    var context = RepositoryOperationContext.saveCompletedRun(second.identity().key());
    var closeSignal = new Neo4jException("Driver close failed");
    var writeSignal = new Neo4jException("Managed write was not acknowledged");
    backend.driverCloseFailure = closeSignal;
    var repository = repository(backend);

    // Act
    repository.save(first);
    backend.acknowledgementFailure = writeSignal;
    var write = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.save(second));
    var close = assertThrows(Neo4jRepositoryOperationException.class, repository::close);

    // Assert
    assertEquals(context, write.context());
    assertEquals(context, close.context());
    assertEquals(CommitKnowledge.UNKNOWN, write.knowledge().orElseThrow());
    assertEquals(CommitKnowledge.UNKNOWN, close.knowledge().orElseThrow());
    assertSame(writeSignal, write.effectivePrimary().getCause());
    assertSame(closeSignal, close.effectivePrimary().getCause());
    assertEquals(Map.of("runId", first.identity().key()), backend.attempts.getFirst().getFirst().parameters());
    assertEquals(Map.of("runId", second.identity().key()), backend.attempts.getLast().getFirst().parameters());
  }

  private static RepositoryOperationContext acquisition() {
    return RepositoryOperationContext.saveCompletedRun("analysis-request");
  }

  private static Neo4jAnalysisRunRepository repository(DriverDouble backend) {
    return new Neo4jAnalysisRunRepository(backend.driver(), SessionConfig.defaultConfig(), acquisition(), new Neo4jAnalysisRunMapper()::map);
  }

  private AnalysisRun completedRun() throws IOException {
    return RepositoryTestRuns.analyze(temporaryDirectory, "Example.java", "interface Example {}");
  }

  private static Neo4jConnectionSettings settings() {
    return new Neo4jConnectionSettings(URI.create("neo4j://localhost:7687"), "neo4j", "fixture-password", "neo4j");
  }

  private static Record definition(String name, String label, List<String> properties) {
    return definition(name, "NODE_PROPERTY_UNIQUENESS", "NODE", label, properties);
  }

  private static Record definition(String name, String type, String entityType, String label, List<String> properties) {
    var values = Map.<String, Object>of("name", name, "type", type, "entityType", entityType, "labelsOrTypes",
        List.of(label), "properties", properties);
    return proxy(Record.class, (method, arguments) -> {
      if (method.equals("get")) {
        return Values.value(values.get(arguments[0]));
      }
      throw new UnsupportedOperationException(method);
    });
  }

  private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> invocation) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
        (instance, method, arguments) -> invocation.apply(method.getName(), arguments == null ? new Object[0] : arguments)));
  }

  private static final class DriverDouble {
    private List<Record> definitions = List.of(definition("analysis_entity_identity", "AnalysisEntity", List.of("runId", "key")),
        definition("analysis_run_identity", "AnalysisRun", List.of("runId")));
    private final List<String> effects = new ArrayList<>();
    private final List<List<StatementCall>> attempts = new ArrayList<>();
    private RuntimeException metadataFailure;
    private RuntimeException schemaCloseFailure;
    private RuntimeException driverCloseFailure;
    private Neo4jException schemaStatementFailure;
    private RuntimeException publicationCloseFailure;
    private RuntimeException publicationFailure;
    private RuntimeException acknowledgementFailure;
    private Neo4jException sessionFailure;
    private int sessions;
    private int failedSchemaStatement;
    private int schemaStatements;
    private int failedSession;
    private boolean publicationStarted;
    private boolean replay;

    Driver driver() {
      return proxy(Driver.class, (method, arguments) -> switch (method) {
        case "session" -> {
          sessions++;
          if (sessions == failedSession && sessionFailure != null) {
            throw sessionFailure;
          }
          yield session(sessions % 2 == 1);
        }
        case "close" -> {
          effects.add("driver.close");
          if (driverCloseFailure != null) {
            throw driverCloseFailure;
          }
          yield null;
        }
        default -> throw new UnsupportedOperationException(method);
      });
    }

    private Session session(boolean schema) {
      return proxy(Session.class, (method, arguments) -> switch (method) {
        case "run" -> {
          if (schema && ((String) arguments[0]).startsWith("CREATE CONSTRAINT")) {
            schemaStatements++;
            if (schemaStatements == failedSchemaStatement && schemaStatementFailure != null) {
              throw schemaStatementFailure;
            }
          }
          if (((String) arguments[0]).startsWith("SHOW CONSTRAINTS") && metadataFailure != null) {
            throw metadataFailure;
          }
          yield result();
        }
        case "executeWrite" -> {
          publicationStarted = true;
          if (publicationFailure != null) {
            throw publicationFailure;
          }
          var callback = (TransactionCallback<?>) arguments[0];
          var result = attempt(callback);
          if (replay) {
            result = attempt(callback);
          }
          if (acknowledgementFailure != null) {
            throw acknowledgementFailure;
          }
          yield result;
        }
        case "close" -> {
          effects.add(schema ? "schema.close" : "publication.close");
          if (schema && schemaCloseFailure != null) {
            throw schemaCloseFailure;
          }
          if (!schema && publicationCloseFailure != null) {
            throw publicationCloseFailure;
          }
          yield null;
        }
        default -> throw new UnsupportedOperationException(method);
      });
    }

    private Object attempt(TransactionCallback<?> callback) {
      var statements = new ArrayList<StatementCall>();
      attempts.add(statements);
      var transaction = proxy(TransactionContext.class, (method, arguments) -> {
        if (method.equals("run")) {
          var parameters = arguments[1] instanceof Value value ? value.asObject() : arguments[1];
          statements.add(new StatementCall((String) arguments[0], parameters));
          return result();
        }
        throw new UnsupportedOperationException(method);
      });
      return callback.execute(transaction);
    }

    private Result result() {
      return proxy(Result.class, (method, arguments) -> switch (method) {
        case "list" -> definitions;
        case "consume" -> proxy(ResultSummary.class, (operation, values) -> { throw new UnsupportedOperationException(operation); });
        default -> throw new UnsupportedOperationException(method);
      });
    }
  }

  private record StatementCall(String statement, Object parameters) {
  }
}
