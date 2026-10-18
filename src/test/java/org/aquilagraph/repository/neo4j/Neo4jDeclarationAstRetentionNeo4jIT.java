package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationException;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
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
class Neo4jDeclarationAstRetentionNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenCompleteNonSealedHierarchy_whenExplicitlyRetainedAndQueriedWithoutCompiler_thenCorrectedPropertiesAndIdentitySurvive()
      throws Exception {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.produce(directory, "Hierarchy.java", """
        sealed class Base permits Open {}
        non-sealed abstract class Open extends Base { int subject() { return 7; } }
        sealed interface Contract permits Extension {}
        non-sealed interface Extension extends Contract {}
        """);

    // Act
    var retained = independentQuery(settings, ast);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals("javac-public/7", retained.identity().semanticProductionRevision());
    assertEquals("java-source/18", retained.identity().sourceModelVersion());
    var modifiers = retained.coverage().validationEvidence().outcomes().stream()
        .filter(value -> value.address().role().equals("TypeElement::Element#getModifiers()"))
        .flatMap(value -> value.encounteredValue().stream()).flatMap(value -> value.items().stream())
        .filter(value -> value.value().equals("non-sealed")).toList();
    assertEquals(2, modifiers.size());
    for (var item : modifiers) {
      assertEquals("non-sealed", item.identity());
      assertEquals(CoveragePropertyValue.Provenance.COMPILER_DERIVED, item.provenance());
    }
    assertEquals(List.of("Open"), retained.program().types().stream().filter(type -> type.qualifiedName().equals("Open"))
        .map(type -> type.qualifiedName()).toList());
    assertTrue(retained.diagnostics().isEmpty(), retained.diagnostics().toString());
  }

  @Test
  void givenQualifiedAnonymousCreation_whenSavedClosedAndQueried_thenInstantiatedConstructorTargetsRemainExact() throws IOException {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.produce(directory, "Example.java",
        "class Outer<T> { class Inner { Inner(T value) {} } } class Example { "
            + "Outer<String> owner = new Outer<>(); Object value = owner.new Inner(\"x\") { String text = \"retained\"; }; }");
    var original = ast.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      DeclarationAstRetentionFixture.assertPreserved(original, retained);
      var creation = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION
          && !node.children(SyntaxRole.CLASS_BODY).isEmpty()).findFirst().orElseThrow();
      var evidence = retained.aggregate().executionScopes().stream()
          .flatMap(scope -> retained.aggregate().executionSyntax(scope.key()).orElseThrow()
              .useEvidence(creation.nodeId(), SyntaxRole.ARGUMENT, 0).stream()).toList();
      assertEquals(1, evidence.size());
      assertEquals("java.lang.String", evidence.getFirst().selectedTarget().displayName());
      var properties = retained.coverage().validationEvidence().outcomes().stream()
          .filter(outcome -> outcome.address().partition() == SourceContext.Partition.WRITTEN_PROPERTY
              && outcome.address().role().startsWith("NewClassTree::")
              && outcome.occurrence().orElseThrow().origins().equals(List.of(creation.origin().orElseThrow())))
          .collect(Collectors.toMap(outcome -> outcome.address().role(), outcome -> outcome));
      var expected = Map.of("NewClassTree::Tree#getKind()", List.of("NEW_CLASS"),
          "NewClassTree::NewClassTree#getEnclosingExpression()", List.of("owner"),
          "NewClassTree::NewClassTree#getIdentifier()", List.of("Inner"),
          "NewClassTree::NewClassTree#getTypeArguments()", List.<String>of(),
          "NewClassTree::NewClassTree#getArguments()", List.of("\"x\""),
          "NewClassTree::NewClassTree#getClassBody()", List.of("{ String text = \"retained\"; }"));
      assertEquals(expected.keySet(), properties.keySet());
      expected.forEach((role, values) -> {
        var outcome = properties.get(role);
        var property = outcome.encounteredValue().orElseThrow();
        assertEquals(values, property.items().stream().map(CoveragePropertyValue.Item::value).toList());
        assertEquals(values.isEmpty() ? CoveragePropertyValue.State.EMPTY
            : role.endsWith("#getArguments()") ? CoveragePropertyValue.State.ITEMS : CoveragePropertyValue.State.SCALAR, property.state());
        assertEquals(values.isEmpty() ? FeatureContextOutcome.Mode.NEGATIVE : FeatureContextOutcome.Mode.FACTS, outcome.support().mode());
      });
      assertMaterialization(settings, ast);
    } finally {
      delete(settings, original.identity());
    }
    assertEquals(AstLookupStatus.NOT_FOUND, retention(settings).findAst(original.identity()).status());
  }

  @Test
  void givenSavedMixedDeclarations_whenQueriedInAnIndependentProcessWithoutCompilerModules_thenClosedSourceBytesRemainExact() throws Exception {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.mixed(directory);

    // Act
    var retained = independentQuery(settings, ast);

    // Assert
    DeclarationAstRetentionFixture.assertMixed(retained);
  }

  @Test
  void givenSavedSharedLocalsAndAnnotatedArrayReferences_whenQueriedWithoutCompilerModules_thenExactCanonicalAttachmentsSurvive()
      throws Exception {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.produce(directory, "Example.java", """
        import java.lang.annotation.*;
        @Target({ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Mark {}
        class Example {
          Object field = new @Mark String("field");
          static { int[] array = new int @Mark [1]; }
          Object subject() {
            @Mark int first = 1, second = 2;
            java.util.function.IntFunction<String[]> factory = @Mark String[]::new;
            return factory.apply(first + second);
          }
        }
        """);

    // Act
    var retained = independentQuery(settings, ast);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    var nodes = retained.aggregate().nodes();
    var locals = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value
        && (value.name().equals("first") || value.name().equals("second"))).toList();
    assertEquals(2, locals.size());
    var annotation = locals.getFirst().requiredChild(SyntaxRole.ANNOTATION);
    assertTrue(locals.getLast().children(SyntaxRole.ANNOTATION).isEmpty());
    assertEquals(List.of(annotation.nodeId()), locals.getLast().value(SyntaxValue.LocalDeclaration.class)
        .bindingEvidence().orElseThrow().sharedAnnotationKeys());
    var reference = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow();
    var qualifier = reference.requiredChild(SyntaxRole.QUALIFIER);
    assertEquals(SyntaxKind.TYPE_USE, qualifier.syntaxKind());
    assertEquals(1, qualifier.children(SyntaxRole.ANNOTATION).size());
    assertEquals(qualifier.origin().orElseThrow().startOffset(), qualifier.requiredChild(SyntaxRole.ANNOTATION)
        .origin().orElseThrow().startOffset());
  }

  private AstResult independentQuery(Neo4jConnectionSettings settings, AstOutcome ast) throws Exception {
    var original = ast.result().orElseThrow();
    var identity = original.identity();
    var output = directory.resolve("retained-query.ast");
    var log = directory.resolve("retained-query.log");
    var classpath = Path.of("target/test-classes").toAbsolutePath() + File.pathSeparator
        + Path.of("target/aquila-graph-0.1.0-SNAPSHOT.jar").toAbsolutePath();
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(ast).status());

      // Act
      var executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
      var builder = new ProcessBuilder(executable.toString(),
          "-Xms64m", "-Xmx1g",
          "--limit-modules", "java.base,java.logging,java.naming,java.management,jdk.unsupported",
          "-Djava.io.tmpdir=" + directory, "-cp", classpath, RetainedAstQueryProcess.class.getName(), identity.softwareSignature(),
          identity.sourceModelVersion(), identity.astProfileVersion(), identity.semanticProductionRevision(), identity.canonicalContentDigest(),
          output.toString()).redirectErrorStream(true).redirectOutput(log.toFile());
      builder.environment().putAll(Neo4jIntegration.environment());
      var process = builder.start();
      try {
        var completed = process.waitFor(60, TimeUnit.SECONDS);

        // Assert
        var diagnostics = Files.readString(log);
        System.out.print(diagnostics);
        assertTrue(completed, () -> "Independent query timed out: " + process.pid() + "\n" + diagnostics);
        assertEquals(0, process.exitValue(), diagnostics);
        assertArrayEquals(AstCodec.encode(original), Files.readAllBytes(output));
        System.out.println("Independent query process " + process.pid() + " completed with exit " + process.exitValue());
        return AstCodec.decode(Files.readAllBytes(output), identity);
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
          assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Independent query process did not close");
        }
      }
    } finally {
      delete(settings, identity);
    }
  }

  @TestFactory
  Stream<DynamicTest> givenMixedDeclarationsAndIndependentCfgDisposition_whenSavedClosedAndQueried_thenCompleteSourceSurvives() {
    // Arrange
    var dispositions = Stream.of("ABSENT", "REFUSED", "FAILED");

    // Act
    var observations = dispositions.map(disposition -> DynamicTest.dynamicTest("givenMixedDeclarationsAnd" + disposition
        + "Cfg_whenSavedClosedAndQueried_thenCompleteSourceSurvives", () -> assertMixedRetention(disposition)));

    // Assert
    return observations;
  }

  private void assertMixedRetention(String disposition) throws IOException {
    // Arrange
    var settings = settings();
    var ast = DeclarationAstRetentionFixture.mixed(directory);
    var original = ast.result().orElseThrow();
    DeclarationAstRetentionFixture.assertMixed(original);
    var subject = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var supported = original.program().methods().stream().filter(value -> value.name().equals("supported")).findFirst().orElseThrow();
    var cause = new IllegalStateException("Declared CFG construction failure");
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var selected = disposition.equals("FAILED") ? supported : subject;
    var request = new CfgRequest(original.identity(), List.of(selected.methodKey()), CfgProfile.JAVA_BOUNDED);
    var run = disposition.equals("ABSENT")
        ? runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(original.snapshot()), DeclarationAstRetentionFixture.configuration(),
            AstProfile.JAVA_SOURCE_UNITS, Optional.empty(), PublicationIntent.DO_NOT_SAVE))
        : disposition.equals("FAILED")
        ? new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), RecoverableCfgFailures.atMethod(supported.methodKey(), cause))
            .deriveExisting(ast, request)
        : runner.deriveExisting(ast, request);
    try {
      // Act
      var before = retention(settings).findAst(original.identity());
      var publication = retention(settings).retainAst(run);
      var lookup = retention(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(AstLookupStatus.NOT_FOUND, before.status());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertFalse(run.isPublishable());
      assertEquals(disposition.equals("ABSENT") ? AnalysisCompletion.COMPLETE
          : disposition.equals("FAILED") ? AnalysisCompletion.FAILED : AnalysisCompletion.PARTIAL, run.completion());
      if (disposition.equals("ABSENT")) {
        assertTrue(run.representation().cfg().isEmpty());
      } else {
        assertEquals(disposition.equals("FAILED") ? MethodCfgState.FAILED : MethodCfgState.UNSUPPORTED,
            run.representation().cfg().orElseThrow().methods().getFirst().state());
        if (disposition.equals("FAILED")) {
          assertSame(cause, run.representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
        }
      }
      DeclarationAstRetentionFixture.assertPreserved(original, retained);
      DeclarationAstRetentionFixture.assertMixed(retained);
      assertMaterialization(settings, ast);
    } finally {
      delete(settings, original.identity());
    }
    assertEquals(AstLookupStatus.NOT_FOUND, retention(settings).findAst(original.identity()).status());
  }

  @TestFactory
  Stream<DynamicTest> givenLegalUnitShape_whenSavedClosedAndQueried_thenActualRootAndOriginalQualificationSurvive() {
    // Arrange
    var units = List.of(new Unit("Ordinary.java", "class Ordinary {}", true),
        new Unit("Compact.java", "int field = 2; void main() {}", true),
        new Unit("module-info.java", "module retained.example { requires java.base; }", false),
        new Unit("package-info.java", "@Deprecated package retained.example;", false),
        new Unit("Empty.java", "", false),
        new Unit("Imports.java", "import java.util.List; ;", false));

    // Act
    var observations = units.stream().map(unit -> DynamicTest.dynamicTest("given" + unit.filename()
        + "_whenSavedClosedAndQueried_thenRootAndQualificationSurvive", () -> {
          // Arrange
          var settings = settings();
          var ast = DeclarationAstRetentionFixture.produce(directory, unit.filename(), unit.source());
          var original = ast.result().orElseThrow();
          try {
            // Act
            var publication = retention(settings).retainAst(ast);
            var lookup = retention(settings).findAst(original.identity());
            var retained = lookup.outcome().orElseThrow().result().orElseThrow();

            // Assert
            assertEquals(PublicationStatus.SAVED, publication.status());
            assertEquals(AstLookupStatus.FOUND, lookup.status());
            DeclarationAstRetentionFixture.assertPreserved(original, retained);
            assertEquals(1, retained.aggregate().roots().size());
            assertEquals(unit.hasType(), !retained.program().types().isEmpty());
            assertEquals(unit.source().length(), retained.aggregate().roots().getFirst().origin().orElseThrow().length());
            assertMaterialization(settings, ast);
          } finally {
            delete(settings, original.identity());
          }
        }));

    // Assert
    return observations;
  }

  @Test
  void givenCurrentAndOriginalHistoricalSubjects_whenReplacingSourceAndLegacyNamespaces_thenEveryOtherIdentityRemainsReadable() throws IOException {
    // Arrange
    var settings = settings();
    var current = DeclarationAstRetentionFixture.mixed(directory);
    var historical = new ArrayList<AstResult>();
    for (var format = 2; format <= 18; format++) { historical.add(HistoricalAstFixtures.read(format)); }
    var simple = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("LegacyRun.java"), "class LegacyRun { int value() { return 4; } }"))),
        DeclarationAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var result = simple.result().orElseThrow();
    var run = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard()).deriveExisting(simple,
        new CfgRequest(result.identity(), result.program().methods().stream().filter(value -> value.hasBody()).map(value -> value.methodKey()).toList(),
            CfgProfile.JAVA_BOUNDED));
    try {
      assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(current).status());
      for (var prior : historical) { assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(AstOutcome.available(prior)).status()); }

      // Act
      var replaced = retention(settings).retainAst(current);
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(run);
        repository.save(run);
      }
      var retained = retention(settings).findAst(current.result().orElseThrow().identity()).outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertTrue(run.isPublishable());
      assertEquals(PublicationStatus.SAVED, replaced.status());
      DeclarationAstRetentionFixture.assertPreserved(current.result().orElseThrow(), retained);
      DeclarationAstRetentionFixture.assertMixed(retained);
      assertMaterialization(settings, current);
      for (var prior : historical) {
        var lookup = retention(settings).findAst(prior.identity());
        assertEquals(AstLookupStatus.FOUND, lookup.status());
        DeclarationAstRetentionFixture.assertPreserved(prior, lookup.outcome().orElseThrow().result().orElseThrow());
        assertMaterialization(settings, AstOutcome.available(prior));
      }
    } finally {
      delete(settings, current.result().orElseThrow().identity());
      for (var prior : historical) { delete(settings, prior.identity()); }
      withSession(settings, session -> session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity",
          Values.parameters("runId", run.identity().key())).consume());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenPresentUnsupportedIdentity_whenQueried_thenIncompatibleDiffersFromAbsenceAndDecodeFailure() {
    // Arrange
    var versions = Stream.of("format", "profile");

    // Act
    var observations = versions.map(version -> DynamicTest.dynamicTest("givenPresentUnsupported" + version
        + "_whenQueried_thenIncompatibleDiffersFromAbsenceAndDecodeFailure", () -> {
          // Arrange
          var settings = settings();
          var ast = DeclarationAstRetentionFixture.mixed(directory);
          var original = ast.result().orElseThrow().identity();
          var unknown = AstIdentity.fromContent(original.softwareSignature(), version.equals("format") ? "java-source/future" : original.sourceModelVersion(),
              version.equals("profile") ? "java-source-expanded/future" : original.astProfileVersion(), original.semanticProductionRevision(), original.canonicalContentDigest());
          var properties = new HashMap<>(new Neo4jAstResultCodec().map(ast).root().properties());
          properties.put("key", unknown.astResultKey());
          properties.put("astResultKey", unknown.astResultKey());
          properties.put("formatVersion", unknown.sourceModelVersion());
          properties.put("sourceModelVersion", unknown.sourceModelVersion());
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
            assertEquals(version.equals("format") ? unknown.sourceModelVersion() : unknown.astProfileVersion(), lookup.unsupportedFormat().orElseThrow());
            assertTrue(lookup.outcome().isEmpty());
          } finally {
            delete(settings, unknown);
          }
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenSupportedDeclarationResultWithStoredContradiction_whenQueriedAndReplaced_thenFailureIsTruthfulAndRepairIsExact() {
    // Arrange
    var contradictions = Stream.of("order", "payload", "identity", "completion");

    // Act
    var observations = contradictions.map(contradiction -> DynamicTest.dynamicTest("givenSupportedDeclarationResultWith" + contradiction
        + "Contradiction_whenQueriedAndReplaced_thenFailureIsTruthfulAndRepairIsExact", () -> {
          // Arrange
          var settings = settings();
          var ast = DeclarationAstRetentionFixture.mixed(directory);
          var original = ast.result().orElseThrow();
          var identity = original.identity();
          var mutation = switch (contradiction) {
            case "order" -> "MATCH (:AstOccurrence)-[:AST_CHILD {astResultKey: $scope}]->(:AstValue)-[r:AST_FIELD {astResultKey: $scope}]->() "
                + "WHERE r.field = 'item' AND r.position = 1 SET r.position = 0 RETURN count(r) AS changed";
            case "payload" -> "MATCH (r:AstEntity:AstResult {astResultKey: $scope}) SET r.payload = $malformed RETURN count(r) AS changed";
            case "identity" -> "MATCH (r:AstEntity:AstResult {astResultKey: $scope}) SET r.canonicalContentDigest = 'foreign-content' RETURN count(r) AS changed";
            case "completion" -> "MATCH (r:AstEntity:AstResult {astResultKey: $scope}) SET r.completion = 'INCOMPLETE' RETURN count(r) AS changed";
            default -> throw new IllegalArgumentException("Unknown stored contradiction");
          };
          try {
            assertEquals(PublicationStatus.SAVED, retention(settings).retainAst(ast).status());
            withSession(settings, session -> assertTrue(session.run(mutation, Values.parameters("scope", identity.astResultKey(),
                "malformed", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}))).single().get("changed").asLong() > 0));

            // Act
            var failure = assertThrows(RepositoryOperationException.class, () -> retention(settings).findAst(identity));
            var replacement = retention(settings).retainAst(ast);
            var lookup = retention(settings).findAst(identity);

            // Assert
            assertEquals(new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, identity), failure.context());
            assertEquals(RepositoryPhase.DECODE, failure.phase());
            assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
            assertEquals(PublicationStatus.SAVED, replacement.status());
            assertEquals(AstLookupStatus.FOUND, lookup.status());
            DeclarationAstRetentionFixture.assertPreserved(original, lookup.outcome().orElseThrow().result().orElseThrow());
            assertMaterialization(settings, ast);
          } finally {
            delete(settings, identity);
          }
        }));

    // Assert
    return observations;
  }

  private record Unit(String filename, String source, boolean hasType) {}

  private static Neo4jConnectionSettings settings() {
    return Neo4jIntegration.settings();
  }

  private static AstRetentionService retention(Neo4jConnectionSettings settings) {
    return new AstRetentionService(new Neo4jAstResultRepositoryFactory(() -> settings));
  }

  private static void assertMaterialization(Neo4jConnectionSettings settings, AstOutcome ast) {
    var projection = new Neo4jAstResultCodec().map(ast);
    withSession(settings, session -> {
      var parameters = Values.parameters("scope", projection.identity().astResultKey());
      var nodes = session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN entity.key AS key, properties(entity) AS properties", parameters).list();
      assertEquals(projection.nodes().stream().collect(Collectors.toMap(Neo4jNode::key, Neo4jNode::properties)),
          nodes.stream().collect(Collectors.toMap(record -> record.get("key").asString(), record -> record.get("properties").asMap())));
      var links = session.run("MATCH (source:AstEntity)-[relation {astResultKey: $scope}]->(target:AstEntity) RETURN relation.key AS key, "
          + "type(relation) AS type, source.key AS source, target.key AS target, properties(relation) AS properties", parameters).list();
      assertEquals(projection.relationships().stream().collect(Collectors.toMap(Neo4jRelationship::key, value -> value)),
          links.stream().map(record -> new Neo4jRelationship(record.get("key").asString(), record.get("type").asString(),
              record.get("source").asString(), record.get("target").asString(), record.get("properties").asMap()))
              .collect(Collectors.toMap(Neo4jRelationship::key, value -> value)));
      assertEquals(0, session.run("MATCH (entity:AnalysisEntity {astResultKey: $scope}) RETURN count(entity) AS count", parameters).single().get("count").asInt());
    });
  }

  private static void delete(Neo4jConnectionSettings settings, AstIdentity identity) {
    withSession(settings, session -> Neo4jIntegration.discardAstFixture(session, identity.astResultKey()));
  }

  private static void withSession(Neo4jConnectionSettings settings, Consumer<Session> action) {
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      action.accept(session);
    }
  }
}
