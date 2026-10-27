package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.RepositoryOperationException;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jExecutableAstRetentionNeo4jIT {
  enum CfgDisposition {
    ABSENT, REFUSED, FAILED
  }

  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenMixedSourceWithIndependentCfgOutcome_whenExplicitlySavedAndLaterQueried_thenAllExecutableValuesSurvive() {
    return Stream.of(CfgDisposition.values()).map(disposition -> DynamicTest.dynamicTest(
        "givenMixedSourceWith" + disposition + "Cfg_whenSavedAndQueried_thenExecutableValuesSurvive",
        () -> assertRetainedCfgDisposition(disposition)));
  }

  private void assertRetainedCfgDisposition(CfgDisposition disposition)
      throws IOException {
    // Arrange
    var settings = settings();
    var runner = new SourceAnalysisRunner(ExecutableAstRetentionFixture.frontend(), CfgDerivers.standard());
    var source = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(ExecutableAstRetentionFixture.snapshot(directory)),
        ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var ast = source.representation().ast();
    var original = ast.result().orElseThrow();
    var subject = original.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow();
    var supported = original.program().methods().stream().filter(method -> method.name().equals("supported")).findFirst().orElseThrow();
    var cause = new IllegalStateException("Declared CFG construction failure");
    var run = switch (disposition) {
      case ABSENT -> source;
      case REFUSED -> runner.deriveExisting(ast, new CfgRequest(original.identity(), List.of(subject.methodKey()), CfgProfile.JAVA_BOUNDED));
      case FAILED -> new SourceAnalysisRunner(ExecutableAstRetentionFixture.frontend(), RecoverableCfgFailures.atMethod(supported.methodKey(), cause))
          .deriveExisting(ast, new CfgRequest(original.identity(), List.of(supported.methodKey()), CfgProfile.JAVA_BOUNDED));
    };

    try {
      // Act
      var before = retention(settings).findAst(original.identity());
      var publication = retention(settings).retainAst(run);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(AstLookupStatus.NOT_FOUND, before.status());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(switch (disposition) {
        case ABSENT -> AnalysisCompletion.COMPLETE;
        case REFUSED -> AnalysisCompletion.PARTIAL;
        case FAILED -> AnalysisCompletion.FAILED;
      }, run.completion());
      assertFalse(run.isPublishable());
      if (disposition == CfgDisposition.ABSENT) {
        assertTrue(run.representation().cfg().isEmpty());
      } else {
        var method = run.representation().cfg().orElseThrow().methods().getFirst();
        assertEquals(disposition == CfgDisposition.REFUSED ? MethodCfgState.UNSUPPORTED : MethodCfgState.FAILED, method.state());
        if (disposition == CfgDisposition.FAILED) {
          assertSame(cause, method.failure().orElseThrow());
        }
      }
      ExecutableAstRetentionFixture.assertPreserved(original, retrieved);
      assertMaterialization(settings, ast);
    } finally {
      deleteAst(settings, original.identity());
    }
    assertEquals(AstLookupStatus.NOT_FOUND, retention(settings).findAst(original.identity()).status());
  }

  @Test
  void givenMixedAndLegacySourceSubjects_whenExactlyReplacingEachNamespace_thenOtherRetainedSubjectsRemainIntact() throws IOException {
    // Arrange
    var settings = settings();
    var mixed = ExecutableAstRetentionFixture.frontend().produce(ExecutableAstRetentionFixture.snapshot(directory),
        ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var mixedResult = mixed.result().orElseThrow();
    var oldSource = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Older.java"), "class Older { int value() { return 4; } }")));
    var bounded = ExecutableAstRetentionFixture.frontend().produce(oldSource, ExecutableAstRetentionFixture.configuration(),
        AstProfile.JAVA_SOURCE_BOUNDED);
    var legacyRun = new SourceAnalysisRunner(ExecutableAstRetentionFixture.frontend(), CfgDerivers.standard()).deriveExisting(bounded,
        new CfgRequest(bounded.result().orElseThrow().identity(), bounded.result().orElseThrow().program().methods().stream()
            .filter(method -> method.hasBody()).map(method -> method.methodKey()).toList(), CfgProfile.JAVA_BOUNDED));
    var oldResult = SourceAnalysisFixture.legacyResult(bounded.result().orElseThrow());
    var old = AstOutcome.available(oldResult);
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(mixed).status());
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(old).status());

      // Act
      var replacement = retention(settings).retainAst(mixed);
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(legacyRun);
        repository.save(legacyRun);
      }
      var retrieved = retention(settings).findAst(mixedResult.identity()).outcome().orElseThrow().result().orElseThrow();
      var older = retention(settings).findAst(oldResult.identity()).outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(PublicationStatus.SAVED, replacement.status());
      ExecutableAstRetentionFixture.assertPreserved(mixedResult, retrieved);
      assertEquals("java-source/2", older.identity().sourceModelVersion());
      assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, older.profile());
      assertEquals(oldResult.identity(), older.identity());
      assertArrayEquals(AstCodec.encode(oldResult), AstCodec.encode(older));
      assertMaterialization(settings, mixed);
      assertMaterialization(settings, old);
    } finally {
      deleteAst(settings, mixedResult.identity());
      deleteAst(settings, oldResult.identity());
      withSession(settings, session -> session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity",
          Values.parameters("runId", legacyRun.identity().key())).consume());
    }
  }

  @Test
  void givenPresentUnknownProfile_whenQueriedByItsExactIdentity_thenIncompatibilityIsDistinctFromAbsenceAndDecode() throws IOException {
    // Arrange: supplied future-profile data observes the reader, not future frontend support.
    var settings = settings();
    var ast = ExecutableAstRetentionFixture.frontend().produce(ExecutableAstRetentionFixture.snapshot(directory),
        ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow().identity();
    var unknown = AstIdentity.fromContent(original.softwareSignature(), original.sourceModelVersion(), "java-source-expanded/99",
        original.semanticProductionRevision(), original.canonicalContentDigest());
    var properties = new HashMap<>(new Neo4jAstResultCodec().map(ast).root().properties());
    properties.put("astResultKey", unknown.astResultKey());
    properties.put("key", unknown.astResultKey());
    properties.put("astProfileVersion", unknown.astProfileVersion());
    properties.put("payload", "invalid-base64");
    try {
      var before = retention(settings).findAst(unknown);
      withSession(settings, session -> session.run("CREATE (root:AstEntity:AstResult) SET root = $properties",
          Values.parameters("properties", properties)).consume());

      // Act
      var lookup = retention(settings).findAst(unknown);

      // Assert
      assertEquals(AstLookupStatus.NOT_FOUND, before.status());
      assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
      assertEquals(unknown, lookup.requestedIdentity());
      assertEquals("java-source-expanded/99", lookup.unsupportedFormat().orElseThrow());
      assertTrue(lookup.outcome().isEmpty());
    } finally {
      deleteAst(settings, unknown);
    }
  }

  @TestFactory
  Stream<DynamicTest> givenSupportedMixedSubjectWithStoredContradiction_whenQueried_thenFailurePreservesPhaseAndSourceOutcome() {
    return Stream.of("child-order", "completion", "identity", "topology").map(contradiction -> DynamicTest.dynamicTest(
        "givenSupportedMixedSubjectWith" + contradiction + "Contradiction_whenQueried_thenFailurePreservesPhaseAndSourceOutcome",
        () -> assertRejectedStoredContradiction(contradiction)));
  }

  private void assertRejectedStoredContradiction(String contradiction)
      throws IOException {
    // Arrange: deliberate supported-data corruption is isolated to this exact saved subject.
    var settings = settings();
    var ast = ExecutableAstRetentionFixture.frontend().produce(ExecutableAstRetentionFixture.snapshot(directory),
        ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    var bytes = AstCodec.encode(original);
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(ast).status());
      if (contradiction.equals("topology")) {
        withSession(settings, session -> {
          var parameters = Values.parameters("scope", original.identity().astResultKey());
          session.run("MATCH (root:AstEntity:AstResult {astResultKey: $scope}) SET root:UnexpectedAstLabel", parameters).consume();
          session.run("CREATE (:AstEntity:AstValue {astResultKey: $scope, key: 'additional-value'})", parameters).consume();
          session.run("MATCH (source)-[relation:AST_FIELD {astResultKey: $scope}]->(target) WITH source, relation, target LIMIT 1 "
              + "CREATE (source)-[duplicate:AST_FIELD]->(target) SET duplicate = properties(relation)", parameters).consume();
        });
      }
      var mutation = switch (contradiction) {
        case "child-order" -> "MATCH (:AstOccurrence)-[:AST_CHILD {astResultKey: $scope}]->(:AstValue)"
            + "-[relation:AST_FIELD {astResultKey: $scope}]->() WHERE relation.field = 'item' AND relation.position = 1 "
            + "SET relation.position = 0 RETURN count(relation) AS changed";
        case "completion" -> "MATCH (root:AstEntity:AstResult {astResultKey: $scope}) SET root.completion = 'INCOMPLETE' RETURN count(root) AS changed";
        case "identity" -> "MATCH (root:AstEntity:AstResult {astResultKey: $scope}) SET root.canonicalContentDigest = 'foreign-content' RETURN count(root) AS changed";
        case "topology" -> "MATCH (root:AstEntity:AstResult {astResultKey: $scope}) RETURN count(root) AS changed";
        default -> throw new IllegalArgumentException("Unknown stored contradiction");
      };
      withSession(settings, session -> assertTrue(session.run(mutation, Values.parameters("scope", original.identity().astResultKey()))
          .single().get("changed").asLong() > 0));

      // Act
      var failure = assertThrows(RepositoryOperationException.class, () -> retention(settings).findAst(original.identity()));
      var repaired = retention(settings).retainAst(ast);
      withSession(settings, session -> {
        var invalidLabels = session.run("MATCH (node:AstEntity {astResultKey: $scope}) WHERE size(labels(node)) <> 2 "
            + "RETURN node.key AS key, labels(node) AS labels", Values.parameters("scope", original.identity().astResultKey())).list();
        assertTrue(invalidLabels.isEmpty(), invalidLabels.toString());
      });
      var retrieved = retention(settings).findAst(original.identity()).outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(RepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
      assertArrayEquals(bytes, AstCodec.encode(original));
      assertEquals(PublicationStatus.SAVED, repaired.status());
      ExecutableAstRetentionFixture.assertPreserved(original, retrieved);
      assertMaterialization(settings, ast);
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  private static void assertMaterialization(Neo4jConnectionSettings settings, AstOutcome ast) {
    var projection = new Neo4jAstResultCodec().map(ast);
    var scope = projection.identity().astResultKey();
    withSession(settings, session -> {
      var nodes = session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN entity.key AS key, properties(entity) AS properties",
          Values.parameters("scope", scope)).list();
      assertEquals(projection.nodes().size(), nodes.size());
      assertEquals(projection.nodes().stream().collect(Collectors.toMap(Neo4jNode::key, Neo4jNode::properties)),
          nodes.stream().collect(Collectors.toMap(record -> record.get("key").asString(), record -> record.get("properties").asMap())));
      var links = session.run("MATCH (source:AstEntity)-[relation {astResultKey: $scope}]->(target:AstEntity) "
          + "RETURN relation.key AS key, type(relation) AS type, properties(relation) AS properties, source.key AS source, target.key AS target",
          Values.parameters("scope", scope)).list();
      assertEquals(projection.relationships().size(), links.size());
      var actual = links.stream().map(record -> new Neo4jRelationship(record.get("key").asString(), record.get("type").asString(),
          record.get("source").asString(), record.get("target").asString(), record.get("properties").asMap()))
          .collect(Collectors.toMap(Neo4jRelationship::key, link -> link));
      assertEquals(projection.relationships().stream().collect(Collectors.toMap(Neo4jRelationship::key, link -> link)), actual);
      assertEquals(0, session.run("MATCH (entity:AnalysisEntity {astResultKey: $scope}) RETURN count(entity) AS count",
          Values.parameters("scope", scope)).single().get("count").asInt());
    });
  }

  @TestFactory
  Stream<DynamicTest> givenIntegralMaterializedFieldStoredAsFloating_whenReplaced_thenExactScalarTypeIsRestored() {
    return Stream.of("node", "relationship").map(owner -> DynamicTest.dynamicTest(
        "givenIntegral" + owner + "FieldStoredAsFloating_whenReplaced_thenExactScalarTypeIsRestored",
        () -> assertIntegralFieldReplacement(owner)));
  }

  @Test
  void givenRootLabelOnRetainedChild_whenExactlyReplacing_thenClosedSubjectAndOtherIdentityArePreserved() throws IOException {
    // Arrange
    var settings = settings();
    var input = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("RootLabel.java"), "class RootLabel { int value() { return 7; } }")));
    var neighborInput = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Neighbor.java"), "class Neighbor { int value() { return 9; } }")));
    var ast = ExecutableAstRetentionFixture.frontend().produce(input, ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var neighbor = ExecutableAstRetentionFixture.frontend().produce(neighborInput, ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(ast).status());
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(neighbor).status());
      withSession(settings, session -> {
        var parameters = Values.parameters("scope", original.identity().astResultKey());
        session.run("MATCH (root:AstEntity:AstResult {astResultKey: $scope, key: $scope}) REMOVE root:AstResult", parameters).consume();
        var displaced = session.run("MATCH (child:AstEntity {astResultKey: $scope}) WHERE child.key <> $scope "
            + "WITH child LIMIT 1 SET child:AstResult RETURN child.key AS key", parameters).single().get("key").asString();
        assertTrue(!original.identity().astResultKey().equals(displaced));
      });
      assertThrows(RepositoryOperationException.class, () -> retention(settings).findAst(original.identity()));
      assertMaterialization(settings, neighbor);

      // Act
      var publication = retention(settings).retainAst(ast);

      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status(), () -> publication.failure()
          .map(failure -> failure.phase() + ": " + failure.effectivePrimary() + ": " + failure.effectivePrimary().getCause()).orElse("No failure"));
      var retrieved = retention(settings).findAst(original.identity()).outcome().orElseThrow().result().orElseThrow();
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertMaterialization(settings, ast);
      assertMaterialization(settings, neighbor);
      var neighboringResult = retention(settings).findAst(neighbor.result().orElseThrow().identity()).outcome().orElseThrow().result().orElseThrow();
      assertArrayEquals(AstCodec.encode(neighbor.result().orElseThrow()), AstCodec.encode(neighboringResult));
    } finally {
      deleteAst(settings, original.identity());
      deleteAst(settings, neighbor.result().orElseThrow().identity());
    }
  }

  @Test
  void givenMisplacedAndMalformedRelationships_whenExactlyReplacing_thenBothNamespacesRetainOnlyTheirCanonicalLinks() throws IOException {
    // Arrange
    var settings = settings();
    var frontend = ExecutableAstRetentionFixture.frontend();
    var configuration = ExecutableAstRetentionFixture.configuration();
    var ast = frontend.produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Owned.java"), "class Owned { int value() { return 7; } }"))),
        configuration, AstProfile.JAVA_SOURCE_EXPANDED);
    var neighbor = frontend.produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Adjacent.java"), "class Adjacent { int value() { return 9; } }"))),
        configuration, AstProfile.JAVA_SOURCE_EXPANDED);
    var projection = new Neo4jAstResultCodec().map(ast);
    var adjacent = new Neo4jAstResultCodec().map(neighbor);
    var link = projection.relationships().getFirst();
    var adjacentLink = adjacent.relationships().getFirst();
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(ast).status());
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(neighbor).status());
      withSession(settings, session -> {
        var parameters = Values.parameters("scope", projection.identity().astResultKey(), "neighbor", adjacent.identity().astResultKey(),
            "source", link.sourceKey(), "target", link.targetKey(), "adjacentSource", adjacentLink.sourceKey(),
            "adjacentTarget", adjacentLink.targetKey(), "properties", link.properties());
        var changed = session.run("MATCH (source:AstEntity {astResultKey: $scope, key: $source}), "
            + "(target:AstEntity {astResultKey: $scope, key: $target}), "
            + "(adjacentSource:AstEntity {astResultKey: $neighbor, key: $adjacentSource}), "
            + "(adjacentTarget:AstEntity {astResultKey: $neighbor, key: $adjacentTarget}) "
            + "CREATE (adjacentSource)-[wrongSource:HAS_AST_VALUES]->(target), "
            + "(source)-[wrongTarget:HAS_AST_VALUES]->(adjacentTarget), "
            + "(adjacentSource)-[foreignEndpoints:HAS_AST_VALUES]->(adjacentTarget), "
            + "(source)-[wrongType:UNEXPECTED_AST_LINK]->(target), "
            + "(source)-[missingKey:HAS_AST_VALUES]->(target), "
            + "(source)-[numericKey:HAS_AST_VALUES]->(target) "
            + "SET wrongSource = $properties, wrongTarget = $properties, foreignEndpoints = $properties, "
            + "wrongType = $properties, numericKey = $properties, numericKey.key = 7 "
            + "RETURN count(*) AS changed", parameters).single().get("changed").asInt();
        assertEquals(1, changed);
        assertEquals(1, session.run("MATCH ()-[relation:HAS_AST_VALUES {astResultKey: $scope, key: $key}]->() "
            + "WHERE startNode(relation).astResultKey = $scope AND endNode(relation).astResultKey = $scope "
            + "REMOVE relation.astResultKey RETURN count(relation) AS changed",
            Values.parameters("scope", projection.identity().astResultKey(), "key", link.key())).single().get("changed").asInt());
      });
      assertThrows(RepositoryOperationException.class, () -> retention(settings).findAst(projection.identity()));

      // Act
      var publication = retention(settings).retainAst(ast);

      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertMaterialization(settings, ast);
      assertMaterialization(settings, neighbor);
      var restored = retention(settings).findAst(projection.identity()).outcome().orElseThrow().result().orElseThrow();
      var neighboring = retention(settings).findAst(adjacent.identity()).outcome().orElseThrow().result().orElseThrow();
      assertArrayEquals(AstCodec.encode(ast.result().orElseThrow()), AstCodec.encode(restored));
      assertArrayEquals(AstCodec.encode(neighbor.result().orElseThrow()), AstCodec.encode(neighboring));
    } finally {
      deleteAst(settings, projection.identity());
      deleteAst(settings, adjacent.identity());
    }
  }

  @Test
  void givenExtraneousMaterialization_whenExactlyReplaced_thenClosedLabelsNodesAndLinksAreRestored() throws IOException {
    // Arrange
    var settings = settings();
    var input = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Topology.java"), "class Topology { int value() { return 7; } }")));
    var ast = ExecutableAstRetentionFixture.frontend().produce(input, ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(ast).status());
      withSession(settings, session -> {
        var parameters = Values.parameters("scope", original.identity().astResultKey());
        session.run("MATCH (root:AstEntity:AstResult {astResultKey: $scope}) SET root:UnexpectedAstLabel", parameters).consume();
        session.run("CREATE (:AstEntity:AstValue {astResultKey: $scope, key: 'additional-value'})", parameters).consume();
        session.run("MATCH (source)-[relation:AST_FIELD {astResultKey: $scope}]->(target) WITH source, relation, target LIMIT 1 "
            + "CREATE (source)-[duplicate:AST_FIELD]->(target) SET duplicate = properties(relation)", parameters).consume();
      });
      assertThrows(RepositoryOperationException.class, () -> retention(settings).findAst(original.identity()));

      // Act
      var publication = retention(settings).retainAst(ast);

      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status(), () -> publication.failure()
          .map(failure -> failure.phase() + ": " + failure.effectivePrimary() + ": " + failure.effectivePrimary().getCause()).orElse("No failure"));
      withSession(settings, session -> {
        var invalidLabels = session.run("MATCH (node:AstEntity {astResultKey: $scope}) WHERE size(labels(node)) <> 2 "
            + "RETURN node.key AS key, labels(node) AS labels", Values.parameters("scope", original.identity().astResultKey())).list();
        assertTrue(invalidLabels.isEmpty(), invalidLabels.toString());
      });
      var retrieved = retention(settings).findAst(original.identity()).outcome().orElseThrow().result().orElseThrow();
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertMaterialization(settings, ast);
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  private void assertIntegralFieldReplacement(String owner) throws IOException {
    // Arrange: the producer is real; deliberate stored numeric coercion is a supplied corruption.
    var settings = settings();
    var input = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Integral.java"), "class Integral { int value() { return 7; } }")));
    var ast = ExecutableAstRetentionFixture.frontend().produce(input, ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(ast).status());
      withSession(settings, session -> {
        var mutation = owner.equals("node")
            ? "MATCH (node:AstEntity {astResultKey: $scope}) WHERE node.ordinal = 1 SET node.ordinal = toFloat(node.ordinal) RETURN count(node) AS changed"
            : "MATCH ()-[relation {astResultKey: $scope}]->() WHERE relation.position = 1 SET relation.position = toFloat(relation.position) RETURN count(relation) AS changed";
        assertTrue(session.run(mutation, Values.parameters("scope", original.identity().astResultKey())).single().get("changed").asLong() > 0);
      });
      var corruption = assertThrows(RepositoryOperationException.class, () -> retention(settings).findAst(original.identity()));

      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(RepositoryPhase.DECODE, corruption.phase());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertMaterialization(settings, ast);
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  private static Neo4jConnectionSettings settings() {
    return Neo4jIntegration.settings();
  }

  @Test
  void givenCompatibleExceptionSchema_whenSavedAndLaterQueried_thenOriginalIdentityAndCompleteValuesRemainReadable() throws IOException {
    // Arrange
    var settings = settings();
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Prior.java"),
        "class Prior { int subject(int value) { try { value += 1; } finally { value += 2; } return value; } }")));
    var current = ExecutableAstRetentionFixture.frontend().produce(snapshot, ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED)
        .result().orElseThrow();
    var previous = SourceAnalysisFixture.exceptionResult(current);
    var outcome = AstOutcome.available(previous);
    try {
      // Act
      var publication = retention(settings).retainAst(outcome);
      var retained = retention(settings).findAst(previous.identity()).outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals("java-source/12", retained.identity().sourceModelVersion());
      assertEquals(previous.identity(), retained.identity());
      assertArrayEquals(AstCodec.encode(previous), AstCodec.encode(retained));
      assertEquals(current.aggregate(), retained.aggregate());
      assertMaterialization(settings, outcome);
    } finally {
      deleteAst(settings, previous.identity());
    }
  }

  private static AstRetentionService retention(Neo4jConnectionSettings settings) {
    return new AstRetentionService(new Neo4jAstResultRepositoryFactory(() -> settings));
  }

  private static void deleteAst(Neo4jConnectionSettings settings, AstIdentity identity) {
    withSession(settings, session -> Neo4jIntegration.discardAstFixture(session, identity.astResultKey()));
  }

  private static void withSession(Neo4jConnectionSettings settings, Consumer<Session> action) {
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      action.accept(session);
    }
  }
}
