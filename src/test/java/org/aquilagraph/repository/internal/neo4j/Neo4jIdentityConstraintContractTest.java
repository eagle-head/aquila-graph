package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.repository.internal.neo4j.AstRepositoryDriverStub.Statement;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jIdentityConstraintContractTest {
  private static final String INSPECTION = "SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties "
      + "WHERE name IN $names RETURN name, type, entityType, labelsOrTypes, properties";
  private static final List<String> CLOSED_WITHOUT_REPLACEMENT = List.of("session.open:0", "session.close:0", "driver.close");

  @TempDir Path directory;

  @Test
  void givenExactConstraintMetadata_whenSavingBothSubjects_thenExpectedSchemaCompletesBeforeReplacement() {
    // Arrange
    for (var publication : publications()) {
      var driver = publication.exactMetadata();

      // Act
      publication.save().accept(driver);

      // Assert
      var expected = publication.schemaStatements();
      assertEquals(expected, driver.schemaStatements.stream().map(Statement::query).toList());
      assertEquals(expected, driver.completedSchemaStatements);
      assertEquals(Map.of("names", List.of(publication.entity().get("name"), publication.root().get("name"))),
          driver.schemaStatements.getLast().parameters().asObject());
      assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return",
          "write.acknowledged", "session.close:1", "driver.close"), driver.events);
      assertTrue(driver.statements.getFirst().query().contains(publication.replacementScope()));
    }
  }

  @Test
  void givenMissingRepeatedOrIncompatibleMetadata_whenSavingBothSubjects_thenSchemaAdmissionPreventsReplacement() {
    // Arrange
    for (var publication : publications()) {
      var entity = publication.entity();
      var root = publication.root();
      var label = ((List<?>) entity.get("labelsOrTypes")).getFirst();
      var properties = (List<?>) entity.get("properties");
      var mismatches = List.of(
          new Mismatch("missing entity", entity, List.of(root)),
          new Mismatch("missing root", root, List.of(entity)),
          new Mismatch("wrong root name", root, List.of(entity, with(root, "name", "other_identity"))),
          new Mismatch("incompatible root", root, List.of(entity, with(root, "properties", List.of("otherKey")))),
          new Mismatch("repeated exact row", entity, List.of(entity, entity, root)),
          new Mismatch("exact row before incompatible row", entity, List.of(entity, with(entity, "labelsOrTypes", List.of("Other")), root)),
          new Mismatch("wrong uniqueness type", entity, List.of(with(entity, "type", "NODE_PROPERTY_EXISTENCE"), root)),
          new Mismatch("wrong entity kind", entity, List.of(with(entity, "entityType", "RELATIONSHIP"), root)),
          new Mismatch("wrong label", entity, List.of(with(entity, "labelsOrTypes", List.of("Other")), root)),
          new Mismatch("absent label", entity, List.of(with(entity, "labelsOrTypes", List.of()), root)),
          new Mismatch("repeated label", entity, List.of(with(entity, "labelsOrTypes", List.of(label, label)), root)),
          new Mismatch("additional label", entity, List.of(with(entity, "labelsOrTypes", List.of(label, "Other")), root)),
          new Mismatch("reversed properties", entity, List.of(with(entity, "properties", properties.reversed()), root)),
          new Mismatch("missing property", entity, List.of(with(entity, "properties", List.of(properties.getFirst())), root)),
          new Mismatch("additional property", entity,
              List.of(with(entity, "properties", List.of(properties.getFirst(), properties.getLast(), "otherKey")), root)));
      for (var mismatch : mismatches) {
        var driver = new AstRepositoryDriverStub(mismatch.rows().stream().map(AstRepositoryDriverStub::record).toList());

        // Act
        var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> publication.save().accept(driver), mismatch.reason());

        // Assert
        assertSchemaFailure(failure, Neo4jRepositoryPhase.SCHEMA, driver);
        var primary = assertInstanceOf(RepositoryException.class, failure.effectivePrimary(), mismatch.reason());
        assertEquals(publication.mismatchDescription(), primary.description(), mismatch.reason());
        assertEquals(publication.mismatchCausePrefix() + mismatch.expected().get("name"),
            assertInstanceOf(IllegalStateException.class, primary.getCause()).getMessage(), mismatch.reason());
      }
    }
  }

  @Test
  void givenFailedConstraintOrInspectionResult_whenSavingBothSubjects_thenOriginalFailureStopsLaterSchemaAndReplacement() {
    // Arrange
    for (var publication : publications()) {
      var expected = publication.schemaStatements();
      for (var failed : expected) {
        var driver = publication.exactMetadata();
        var primary = new Neo4jException("Schema result failed");
        driver.onSchemaResult = query -> {
          if (query.equals(failed)) {
            throw primary;
          }
        };

        // Act
        var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> publication.save().accept(driver), failed);

        // Assert
        assertSchemaFailure(failure, Neo4jRepositoryPhase.SCHEMA, driver);
        assertSame(primary, assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).getCause());
        assertEquals(expected.subList(0, expected.indexOf(failed)), driver.completedSchemaStatements);
        assertEquals(expected.subList(0, expected.indexOf(failed) + 1),
            driver.schemaStatements.stream().map(Statement::query).toList());
      }
    }
  }

  @Test
  void givenAdmittedSchemaButFailedSessionClose_whenSavingBothSubjects_thenReplacementRemainsUnstarted() {
    // Arrange
    for (var publication : publications()) {
      var driver = publication.exactMetadata();
      var close = new Neo4jException("Schema session close failed");
      driver.failingClose = 0;
      driver.closeFailure = close;

      // Act
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> publication.save().accept(driver));

      // Assert
      assertSchemaFailure(failure, Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE, driver);
      assertSame(close, assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).getCause());
      assertEquals(publication.schemaStatements(), driver.completedSchemaStatements);
    }
  }

  @Test
  void givenIncompatibleAstSchemaAndFailedClosures_whenRetainingAst_thenDeclaredFailurePreservesOrderedCleanup() {
    // Arrange
    var ast = RepositoryTestRuns.analyze(directory, "Example.java", "interface Example {}").representation().ast();
    var context = RepositoryOperationContext.saveAst(ast.result().orElseThrow().identity());
    var driver = new AstRepositoryDriverStub(List.of(
        AstRepositoryDriverStub.record(definition("ast_entity_identity", "AstEntity", List.of("key", "astResultKey"))),
        AstRepositoryDriverStub.record(definition("ast_result_identity", "AstResult", List.of("astResultKey")))));
    var schemaClose = new Neo4jException("Schema session close failed");
    var driverClose = new Neo4jException("Driver close failed");
    driver.failingClose = 0;
    driver.closeFailure = schemaClose;
    driver.driverCloseFailure = driverClose;
    var factory = new Neo4jAstResultRepositoryFactory(Neo4jIdentityConstraintContractTest::settings, (uri, auth) -> driver.driver());
    var retention = AnalysisCapabilities.astRetention(factory::openRetention);

    // Act
    var outcome = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(PublicationStatus.FAILED, outcome.status());
    assertEquals(Optional.of(context.subject()), outcome.subjectIdentity());
    assertEquals(CommitKnowledge.NOT_STARTED, outcome.knowledge());
    var failure = assertInstanceOf(Neo4jRepositoryOperationException.class, outcome.failure().orElseThrow().carrier());
    assertEquals(context, failure.context());
    assertSchemaFailure(failure, Neo4jRepositoryPhase.SCHEMA, driver);
    var primary = assertInstanceOf(RepositoryException.class, failure.effectivePrimary());
    assertEquals("Neo4j AST schema definition mismatch", primary.description());
    assertEquals("Required AST node-property uniqueness constraint is missing or incompatible: ast_entity_identity",
        assertInstanceOf(IllegalStateException.class, primary.getCause()).getMessage());
    var cleanup = failure.getSuppressed();
    assertEquals(2, cleanup.length);
    assertCleanup(cleanup[0], Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE, schemaClose);
    assertCleanup(cleanup[1], Neo4jRepositoryPhase.REPOSITORY_CLOSE, driverClose);
    assertTrue(Arrays.stream(cleanup).map(Neo4jRepositoryOperationException.class::cast)
        .allMatch(observation -> context.equals(observation.context())));
  }

  @Test
  void givenFatalInspectionAndFailedClosures_whenSavingBothSubjects_thenPrimaryIdentityAndCleanupOrderSurvive() {
    // Arrange
    for (var publication : publications()) {
      var driver = publication.exactMetadata();
      var primary = new AssertionError("Schema inspection stopped abruptly");
      var schemaClose = new Neo4jException("Schema session close failed");
      var driverClose = new Neo4jException("Driver close failed");
      driver.onSchemaResult = query -> {
        if (query.equals(INSPECTION)) {
          throw primary;
        }
      };
      driver.failingClose = 0;
      driver.closeFailure = schemaClose;
      driver.driverCloseFailure = driverClose;

      // Act
      var failure = assertThrows(AssertionError.class, () -> publication.save().accept(driver));

      // Assert
      assertSame(primary, failure);
      assertEquals(2, failure.getSuppressed().length);
      assertCleanup(failure.getSuppressed()[0], Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE, schemaClose);
      assertCleanup(failure.getSuppressed()[1], Neo4jRepositoryPhase.REPOSITORY_CLOSE, driverClose);
      assertEquals(CLOSED_WITHOUT_REPLACEMENT, driver.events);
      assertTrue(driver.statements.isEmpty());
    }
  }

  private List<Publication> publications() {
    var run = RepositoryTestRuns.analyze(directory, "Example.java", "interface Example {}");
    var ast = run.representation().ast();
    var identity = ast.result().orElseThrow().identity();
    var completedRun = new Publication(
        definition("analysis_entity_identity", "AnalysisEntity", List.of("runId", "key")),
        definition("analysis_run_identity", "AnalysisRun", List.of("runId")),
        List.of("CREATE CONSTRAINT analysis_entity_identity IF NOT EXISTS FOR (entity:AnalysisEntity) REQUIRE (entity.runId, entity.key) IS UNIQUE",
            "CREATE CONSTRAINT analysis_run_identity IF NOT EXISTS FOR (run:AnalysisRun) REQUIRE run.runId IS UNIQUE", INSPECTION),
        "Neo4j repository schema definition mismatch", "Required node-property uniqueness constraint is missing or incompatible: ",
        "MATCH (entity:AnalysisEntity {runId: $runId})", driver -> {
          var factory = Neo4jAnalysisRunRepository.factory(Neo4jIdentityConstraintContractTest::settings, (uri, auth) -> driver.driver());
          try (var repository = factory.open(RepositoryOperationContext.saveCompletedRun(run.identity().key()))) {
            repository.save(run);
          }
        });
    var retainedAst = new Publication(
        definition("ast_entity_identity", "AstEntity", List.of("astResultKey", "key")),
        definition("ast_result_identity", "AstResult", List.of("astResultKey")),
        List.of("CREATE CONSTRAINT ast_entity_identity IF NOT EXISTS FOR (entity:AstEntity) REQUIRE (entity.astResultKey, entity.key) IS UNIQUE",
            "CREATE CONSTRAINT ast_result_identity IF NOT EXISTS FOR (result:AstResult) REQUIRE result.astResultKey IS UNIQUE", INSPECTION),
        "Neo4j AST schema definition mismatch", "Required AST node-property uniqueness constraint is missing or incompatible: ",
        "AstEntity", driver -> {
          var factory = new Neo4jAstResultRepositoryFactory(Neo4jIdentityConstraintContractTest::settings, (uri, auth) -> driver.driver());
          try (var repository = factory.openRetention(new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, new Ast(identity)))) {
            repository.saveComplete(ast);
          }
        });
    return List.of(completedRun, retainedAst);
  }

  private static void assertSchemaFailure(Neo4jRepositoryOperationException failure, Neo4jRepositoryPhase phase,
      AstRepositoryDriverStub driver) {
    assertEquals(phase, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertEquals(CLOSED_WITHOUT_REPLACEMENT, driver.events);
    assertTrue(driver.statements.isEmpty());
  }

  private static void assertCleanup(Throwable failure, Neo4jRepositoryPhase phase, Throwable cause) {
    var observed = assertInstanceOf(Neo4jRepositoryOperationException.class, failure);
    assertEquals(phase, observed.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, observed.knowledge().orElseThrow());
    assertSame(cause, assertInstanceOf(RepositoryException.class, observed.effectivePrimary()).getCause());
  }

  private static Neo4jConnectionSettings settings() {
    return new Neo4jConnectionSettings(URI.create("bolt://localhost:7687"), "neo4j", "test-only", "neo4j");
  }

  private static Map<String, Object> definition(String name, String label, List<String> properties) {
    return Map.of("name", name, "type", "NODE_PROPERTY_UNIQUENESS", "entityType", "NODE", "labelsOrTypes", List.of(label),
        "properties", properties);
  }

  private static Map<String, Object> with(Map<String, Object> definition, String field, Object value) {
    var modified = new HashMap<>(definition);
    modified.put(field, value);
    return modified;
  }

  private record Publication(Map<String, Object> entity, Map<String, Object> root, List<String> schemaStatements, String mismatchDescription,
      String mismatchCausePrefix, String replacementScope, Consumer<AstRepositoryDriverStub> save) {
    AstRepositoryDriverStub exactMetadata() {
      return new AstRepositoryDriverStub(List.of(AstRepositoryDriverStub.record(entity), AstRepositoryDriverStub.record(root)));
    }
  }

  private record Mismatch(String reason, Map<String, Object> expected, List<Map<String, Object>> rows) {
  }
}
