package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.port.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
  void givenDriverCreationFailure_whenRepositoryOpens_thenNoDriverCleanupIsInvented() {
    // Arrange
    var primary = new IllegalStateException("Driver was not returned");
    var settings = settings();

    // Act
    var failure = assertThrows(IllegalStateException.class,
        () -> new Neo4jAnalysisRunRepository(settings, ignored -> { throw primary; }, ignored -> SessionConfig.defaultConfig()));

    // Assert
    assertSame(primary, failure);
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
    var failure = assertThrows(IllegalStateException.class,
        () -> new Neo4jAnalysisRunRepository(settings(), ignored -> backend.driver(), ignored -> { throw primary; }));

    // Assert
    assertSame(primary, failure);
    assertEquals(List.of("driver.close"), backend.effects);
    assertEquals(1, failure.getSuppressed().length);
    assertTrue(failure.getSuppressed()[0] instanceof RepositoryException);
    assertSame(cleanup, failure.getSuppressed()[0].getCause());
    assertFalse(backend.publicationStarted);
  }

  @Test
  void givenNeo4jSetupPrimaryAndUnmatchedCleanupFailure_whenRepositoryOpens_thenTranslationOwnsTheSuppressedCleanup() {
    // Arrange
    var backend = new DriverDouble();
    var primary = new Neo4jException("Session configuration failed");
    var cleanup = new IllegalStateException("Driver cleanup failed");
    backend.unmatchedDriverCloseFailure = cleanup;

    // Act
    var failure = assertThrows(RepositoryException.class,
        () -> new Neo4jAnalysisRunRepository(settings(), ignored -> backend.driver(), ignored -> { throw primary; }));

    // Assert
    assertEquals("Neo4j driver initialization failed", failure.description());
    assertSame(primary, failure.getCause());
    assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
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
        () -> new Neo4jAnalysisRunRepository(settings(), ignored -> backend.driver(), ignored -> { throw primary; }));

    // Assert
    assertSame(primary, failure);
    assertEquals(List.of("driver.close"), backend.effects);
    assertFalse(backend.publicationStarted);
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
    var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Neo4j repository schema definition mismatch", failure.description());
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
      var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
      repository.close();

      // Assert
      assertEquals("Neo4j repository schema definition mismatch", failure.description());
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
    var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.getCause());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
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
      var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
      repository.close();

      // Assert
      assertSame(primary, failure.getCause());
      assertFalse(backend.publicationStarted);
      assertEquals(failedSession, backend.sessions);
      assertEquals(failedSession == 1 ? List.of("driver.close") : List.of("schema.close", "driver.close"), backend.effects);
    }
  }

  @Test
  void givenSchemaMismatchAndFailedClose_whenRunIsSaved_thenMismatchOwnsTheRawCleanupSuppression() throws IOException {
    // Arrange
    var backend = new DriverDouble();
    backend.definitions = List.of();
    var cleanup = new Neo4jException("Schema close failed");
    backend.schemaCloseFailure = cleanup;
    var repository = repository(backend);
    var run = completedRun();

    // Act
    var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Neo4j repository schema definition mismatch", failure.description());
    assertTrue(failure.getCause() instanceof IllegalStateException);
    assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
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
    var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Neo4j repository schema definition mismatch", failure.description());
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
      var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
      repository.close();

      // Assert
      assertSame(primary, failure.getCause());
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
    var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.getCause());
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
    var failure = assertThrows(RepositoryException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure.getCause());
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
    var failure = assertThrows(IllegalStateException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure);
    assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
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
    var failure = assertThrows(IllegalArgumentException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertEquals("Only completed analysis runs may be stored", failure.getMessage());
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
    var repository = new Neo4jAnalysisRunRepository(backend.driver(), SessionConfig.defaultConfig(), ignored -> { throw primary; });
    var run = completedRun();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> repository.save(run));
    repository.close();

    // Assert
    assertSame(primary, failure);
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
    var failure = assertThrows(RepositoryException.class, repository::close);

    // Assert
    assertEquals("Neo4j driver shutdown failed", failure.description());
    assertSame(primary, failure.getCause());
    assertEquals(List.of("driver.close"), backend.effects);
  }

  private static Neo4jAnalysisRunRepository repository(DriverDouble backend) {
    return new Neo4jAnalysisRunRepository(backend.driver(), SessionConfig.defaultConfig(), new Neo4jAnalysisRunMapper()::map);
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
    private Neo4jException metadataFailure;
    private Neo4jException schemaCloseFailure;
    private Neo4jException driverCloseFailure;
    private RuntimeException unmatchedDriverCloseFailure;
    private Neo4jException schemaStatementFailure;
    private Neo4jException publicationCloseFailure;
    private RuntimeException publicationFailure;
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
          yield session(sessions == 1);
        }
        case "close" -> {
          effects.add("driver.close");
          if (driverCloseFailure != null) {
            throw driverCloseFailure;
          }
          if (unmatchedDriverCloseFailure != null) {
            throw unmatchedDriverCloseFailure;
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
