package org.aquilagraph.repository.neo4j;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.RepositoryOperationException;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.application.SourceAnalysisRunner;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

class Neo4jAstResultRepositoryNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenBoundedAndExpandedProfiles_whenSavingAndQueryingRealRepository_thenDistinctResultsRetainClosedSourceValues()
      throws IOException {
    // Arrange
    var settings = settings();
    var input = snapshot("class Stored { int value(int x) { return x + 7; } }");
    var bounded = frontend().produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = frontend().produce(input, configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var retention = retention(settings);

    try {
      // Act
      var boundedSave = retention.retainAst(bounded);
      var expandedSave = retention.retainAst(expanded);
      var boundedLookup = retention.findAst(bounded.result().orElseThrow().identity());
      var expandedLookup = retention.findAst(expanded.result().orElseThrow().identity());

      // Assert
      assertEquals(PublicationStatus.SAVED, boundedSave.status());
      assertEquals(PublicationStatus.SAVED, expandedSave.status());
      assertEquals(AstLookupStatus.FOUND, boundedLookup.status());
      assertEquals(AstLookupStatus.FOUND, expandedLookup.status());
      assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, boundedLookup.outcome().orElseThrow().profile());
      assertEquals(AstProfile.JAVA_SOURCE_EXPANDED, expandedLookup.outcome().orElseThrow().profile());
      assertArrayEquals(AstCodec.encode(bounded.result().orElseThrow()), AstCodec.encode(boundedLookup.outcome().orElseThrow().result().orElseThrow()));
      assertArrayEquals(AstCodec.encode(expanded.result().orElseThrow()), AstCodec.encode(expandedLookup.outcome().orElseThrow().result().orElseThrow()));
      assertEquals(expanded.result().orElseThrow().aggregate(), expandedLookup.outcome().orElseThrow().result().orElseThrow().aggregate());
      withSession(settings, session -> assertEquals(2, session.run(
          "MATCH (result:AstEntity:AstResult) WHERE result.astResultKey IN $subjects RETURN count(result) AS count",
          Values.parameters("subjects", List.of(bounded.result().orElseThrow().identity().astResultKey(),
              expanded.result().orElseThrow().identity().astResultKey()))).single().get("count").asInt()));
    } finally {
      deleteAst(settings, bounded.result().orElseThrow().identity());
      deleteAst(settings, expanded.result().orElseThrow().identity());
    }
  }

  @Test
  void givenCompleteSourceAndLaterReturnedConstructionFailure_whenRetainingAst_thenEverySourceValueRemainsIndependentlyQueryable()
      throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int first() { return 1; } int second() { return 2; } int third() { return 3; } }"),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var original = ast.result().orElseThrow();
    var keys = List.of("first", "second", "third").stream().map(name -> original.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var cause = new IllegalStateException("Declared construction observation");
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing derivation must not produce source");
    }, RecoverableCfgFailures.atMethod(keys.get(1), cause));
    var run = runner.deriveExisting(ast, new CfgRequest(original.identity(), keys, CfgProfile.JAVA_BOUNDED));

    try {
      // Act
      var retention = retention(settings);
      var publication = retention.retainAst(run);
      var lookup = retention.findAst(original.identity());

      // Assert
      assertEquals(AnalysisCompletion.FAILED, run.completion());
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertSame(ast, run.representation().ast());
      var methods = run.representation().cfg().orElseThrow().methods();
      assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.FAILED, MethodCfgState.AVAILABLE),
          methods.stream().map(method -> method.state()).toList());
      assertSame(cause, methods.get(1).failure().orElseThrow());
      assertTrue(methods.get(1).graph().isEmpty());
      assertEquals(original.identity(), methods.getFirst().graph().orElseThrow().identity().orElseThrow().astIdentity());
      assertEquals(original.identity(), methods.getLast().graph().orElseThrow().identity().orElseThrow().astIdentity());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
      withSession(settings, session -> assertEquals(0,
          session.run("MATCH (entity:AstEntity:ControlFlowGraph {astResultKey: $scope}) RETURN count(entity) AS count",
              Values.parameters("scope", original.identity().astResultKey())).single().get("count").asInt()));
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteSourceAndPerMethodCfgRefusal_whenRetainingAst_thenRefusedAndAvailableBodiesRemainQueryable() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int choose(int value) { return switch (value) { default -> 1; }; } int value() { return 2; } }"),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var original = ast.result().orElseThrow();
    var keys = List.of("choose", "value").stream().map(name -> original.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var run = new SourceAnalysisRunner(frontend(), CfgDerivers.standard()).deriveExisting(ast,
        new CfgRequest(original.identity(), keys, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));

    try {
      // Act
      var retention = retention(settings);
      var publication = retention.retainAst(run);
      var lookup = retention.findAst(original.identity());

      // Assert
      assertEquals(AnalysisCompletion.PARTIAL, run.completion());
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertSame(ast, run.representation().ast());
      var methods = run.representation().cfg().orElseThrow().methods();
      assertEquals(List.of(MethodCfgState.UNSUPPORTED, MethodCfgState.AVAILABLE), methods.stream().map(method -> method.state()).toList());
      assertTrue(methods.getFirst().graph().isEmpty());
      assertEquals(original.bodyRef(keys.getFirst()), methods.getFirst().body());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteSourceWithoutCfg_whenSavingClosingAndFinding_thenDatabaseRoundTripRetainsEveryPublicValue() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("@Deprecated class Stored { int value(int x) { return x + 7; } String text(Object x) { return \"a\" + x; } }"),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var original = ast.result().orElseThrow();
    var retention = retention(settings);

    try {
      // Act
      var publication = retention.retainAst(ast);
      var lookup = retention.findAst(original.identity());
      var decoded = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), decoded.identity());
      assertEquals(original.snapshot(), decoded.snapshot());
      assertEquals(original.environment(), decoded.environment());
      assertEquals(original.program(), decoded.program());
      assertEquals(original.aggregate(), decoded.aggregate());
      assertEquals(original.coverage(), decoded.coverage());
      assertEquals(original.diagnostics(), decoded.diagnostics());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
      withSession(settings, session -> {
        var record = session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count",
            Values.parameters("scope", original.identity().astResultKey())).single();
        assertEquals(AstCodec.materialize(original).nodes().size() + 1, record.get("count").asInt());
        assertEquals(0, session.run("MATCH (entity:AstEntity:ControlFlowGraph {astResultKey: $scope}) RETURN count(entity) AS count",
            Values.parameters("scope", original.identity().astResultKey())).single().get("count").asInt());
      });
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenRejectedCfgWithCompleteSource_whenExplicitlyRetainingAst_thenSourceRemainsIndependentlyQueryable() throws IOException {
    // Arrange
    var settings = settings();
    var input = snapshot("class Stored { int value() { return 2; } }");
    var runner = new SourceAnalysisRunner(frontend(), CfgDerivers.standard());
    var run = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(input), configuration(), AstProfile.JAVA_SOURCE_BOUNDED,
        Optional.of(new CfgSelection(List.of("unknown-method-key"), CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    var identity = run.representation().ast().result().orElseThrow().identity();

    try {
      // Act
      var retention = retention(settings);
      var publication = retention.retainAst(run);
      var lookup = retention.findAst(identity);

      // Assert
      assertEquals(AnalysisCompletion.REJECTED, run.completion());
      assertEquals(AstCompletion.COMPLETE, run.representation().ast().completion());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(identity, lookup.requestedIdentity());
    } finally {
      deleteAst(settings, identity);
    }
  }

  @Test
  void givenUnsupportedVersionAndMalformedEnvelope_whenFinding_thenIncompatibilityBypassesDecodeAndSupportedCorruptionFails() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 3; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var retention = retention(settings);

    try {
      retention.retainAst(ast);
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) "
          + "SET result.formatVersion = 'future-source-format', result.payload = 'invalid-base64'",
          Values.parameters("scope", identity.astResultKey())).consume());

      // Act and assert
      assertEquals(AstLookupStatus.INCOMPATIBLE, retention.findAst(identity).status());
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) "
          + "SET result.formatVersion = $format", Values.parameters("scope", identity.astResultKey(), "format", AstCodec.FORMAT_VERSION)).consume());
      var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));
      assertEquals(RepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    } finally {
      deleteAst(settings, identity);
    }
    assertEquals(AstLookupStatus.NOT_FOUND, retention.findAst(identity).status());
  }

  @Test
  void givenRetainedAstAndCompletedRun_whenReplacingTheCompletedRun_thenAstNamespaceAndContentSurvive() throws IOException {
    // Arrange
    var settings = settings();
    var input = snapshot("class Stored { int value() { return 4; } }");
    var frontend = frontend();
    var ast = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var completedRun = new SourceAnalysisFixture().analyze(ast);
    var retention = retention(settings);
    assertEquals(identity, completedRun.representation().ast().result().orElseThrow().identity());

    try {
      // Act
      retention.retainAst(ast);
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(completedRun);
        repository.save(completedRun);
      }
      var lookup = retention.findAst(identity);

      // Assert
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(ast.result().orElseThrow()), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
    } finally {
      deleteAst(settings, identity);
      withSession(settings, session -> session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity",
          Values.parameters("runId", completedRun.identity().key())).consume());
    }
  }

  @Test
  void givenForeignSameKeySourceEndpoint_whenFinding_thenSupportedCorrelationsAreRejected() throws IOException {
    assertRejectedEndpointAlias(true, true, false);
  }

  @Test
  void givenForeignSameKeyTargetEndpoint_whenFinding_thenSupportedCorrelationsAreRejected() throws IOException {
    assertRejectedEndpointAlias(false, true, false);
  }

  @Test
  void givenSameKeyTargetWithoutAstNamespace_whenFinding_thenSupportedCorrelationsAreRejected() throws IOException {
    assertRejectedEndpointAlias(false, false, false);
  }

  @Test
  void givenAdditionalIncomingRelationshipWithoutAstNamespace_whenFinding_thenHiddenCorruptionIsRejected() throws IOException {
    assertRejectedEndpointAlias(true, false, true);
  }

  private void assertRejectedEndpointAlias(boolean aliasSource, boolean foreignScope, boolean retainOriginalRelationship) throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 7; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var result = ast.result().orElseThrow();
    var identity = result.identity();
    var scope = identity.astResultKey();
    var retention = retention(settings);
    var endpoint = aliasSource ? "source" : "target";
    var aliasLabels = foreignScope ? ":AstEntity:AstValue" : "";
    var mutation = "MATCH (source:AstEntity)-[relation:HAS_AST_VALUES {astResultKey: $scope}]->(target:AstEntity) "
        + "CREATE (alias" + aliasLabels + ") SET alias = " + endpoint
        + " {.*, astResultKey: $aliasScope, endpointAliasFor: $scope} CREATE (" + (aliasSource ? "alias" : "source")
        + ")-[replacement:HAS_AST_VALUES]->(" + (aliasSource ? "target" : "alias") + ") SET replacement = properties(relation) "
        + (retainOriginalRelationship ? "" : "DELETE relation ") + "RETURN count(replacement) AS count";

    try {
      assertEquals(PublicationStatus.SAVED, retention.retainAst(ast).status());
      var baseline = retention.findAst(identity);
      assertEquals(AstLookupStatus.FOUND, baseline.status());
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(baseline.outcome().orElseThrow().result().orElseThrow()));
      withSession(settings, session -> {
        var parameters = Values.parameters("scope", scope, "aliasScope", foreignScope ? "foreign-endpoint:" + scope : scope);
        var countQuery = "MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count";
        var countBefore = session.run(countQuery, parameters).single().get("count").asLong();
        var replacement = session.run(mutation, parameters);
        assertEquals(1L, replacement.single().get("count").asLong());
        replacement.consume();
        assertEquals(countBefore, session.run(countQuery, parameters).single().get("count").asLong());
        var payload = session.run("MATCH (root:AstEntity:AstResult {astResultKey: $scope}) RETURN root.payload AS payload", parameters)
            .single().get("payload").asString();
        assertEquals(Base64.getEncoder().encodeToString(AstCodec.encode(result)), payload);
      });

      // Act
      var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));

      // Assert
      assertEquals(RepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    } finally {
      withSession(settings, session -> session.run("MATCH (alias {endpointAliasFor: $scope}) DETACH DELETE alias",
          Values.parameters("scope", scope)).consume());
      deleteAst(settings, identity);
    }
  }

  private InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
  }

  @Test
  void givenRetainedEntitiesWithoutTheirResultRoot_whenFinding_thenCorruptionCannotBecomeAbsence() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 5; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var retention = retention(settings);
    try {
      retention.retainAst(ast);
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) DETACH DELETE result",
          Values.parameters("scope", identity.astResultKey())).consume());

      // Act
      var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));

      // Assert
      assertEquals(RepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    } finally {
      deleteAst(settings, identity);
    }
  }

  @Test
  void givenIncompatibleNamedConstraint_whenSavingAgain_thenSchemaFailurePreventsReplacementAndPriorAstSurvives() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 6; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var retention = retention(settings);
    try {
      retention.retainAst(ast);
      withSession(settings, session -> {
        session.run("DROP CONSTRAINT ast_entity_identity").consume();
        session.run("CREATE CONSTRAINT ast_entity_identity FOR (entity:AstEntity) REQUIRE entity.incompatibleIdentity IS UNIQUE").consume();
      });

      // Act
      var publication = retention.retainAst(ast);
      var lookup = retention.findAst(identity);

      // Assert
      assertEquals(PublicationStatus.FAILED, publication.status());
      assertEquals(RepositoryPhase.SCHEMA, publication.failure().orElseThrow().phase());
      assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(ast.result().orElseThrow()), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
    } finally {
      withSession(settings, session -> {
        session.run("DROP CONSTRAINT ast_entity_identity IF EXISTS").consume();
        session.run("CREATE CONSTRAINT ast_entity_identity FOR (entity:AstEntity) REQUIRE (entity.astResultKey, entity.key) IS UNIQUE").consume();
      });
      deleteAst(settings, identity);
    }
  }

  private static JavaFrontend frontend() {
    return new JavaFrontend(new JavaSourceLoader());
  }

  private static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }

  private static Neo4jConnectionSettings settings() {
    return Neo4jConnectionSettings.fromEnvironment(System.getenv());
  }

  private static AstRetentionService retention(Neo4jConnectionSettings settings) {
    return new AstRetentionService(new Neo4jAstResultRepositoryFactory(() -> settings));
  }

  private static void deleteAst(Neo4jConnectionSettings settings, AstIdentity identity) {
    withSession(settings, session -> session.run("MATCH (entity:AstEntity {astResultKey: $scope}) DETACH DELETE entity",
        Values.parameters("scope", identity.astResultKey())).consume());
  }

  private static void withSession(Neo4jConnectionSettings settings, Consumer<Session> action) {
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      action.accept(session);
    }
  }
}
