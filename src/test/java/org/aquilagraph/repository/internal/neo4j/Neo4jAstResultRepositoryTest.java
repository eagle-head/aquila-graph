package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationException;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.testsupport.StatementBranchFixture;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jAstResultRepositoryTest {
  @TempDir Path directory;

  @Test
  void givenDeclarationInRetainedBranchAndCleanupFailures_whenFinding_thenDecodeCausePrecedesOrderedCleanup() {
    for (var role : List.of(SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH)) {
      for (var kind : StatementBranchFixture.DECLARATIONS) {
        // Arrange
        var ast = StatementBranchFixture.produce(directory, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
        var result = ast.result().orElseThrow();
        var projection = new Neo4jAstResultCodec().map(ast);
        var root = projection.root();
        var properties = new HashMap<>(root.properties());
        var malformed = StatementBranchFixture.withDeclaration(AstCodec.encode(result), result.identity().sourceModelVersion(),
            StatementBranchFixture.branch(ast, role), kind, false);
        properties.put("payload", Base64.getEncoder().encodeToString(malformed));
        var nodes = new ArrayList<>(projection.nodes());
        nodes.set(nodes.indexOf(root), new Neo4jNode(root.key(), root.label(), properties));
        var backend = new AstRepositoryDriverStub();
        backend.stored = new Neo4jAstProjection(projection.identity(), nodes, projection.relationships());
        backend.closeFailure = new IllegalStateException("Read cleanup after invalid branch");
        backend.failingClose = 0;
        backend.driverCloseFailure = new Neo4jException("Driver cleanup after invalid branch");
        // Act
        var failure = assertThrows(RepositoryOperationException.class, () -> {
          try (var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend)) {
            repository.find(result.identity());
          }
        });
        // Assert
        assertEquals(context(ast, RepositoryOperationKind.QUERY_AST), failure.context());
        assertEquals(RepositoryPhase.DECODE, failure.phase());
        assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
        var sourceFailure = assertInstanceOf(AstContractException.class, failure.effectivePrimary());
        assertTrue(sourceFailure.getMessage().contains("child category"), sourceFailure.toString());
        assertInstanceOf(IllegalArgumentException.class, sourceFailure.getCause());
        assertEquals(2, failure.getSuppressed().length);
        var sessionClose = assertInstanceOf(RepositoryOperationException.class, failure.getSuppressed()[0]);
        var driverClose = assertInstanceOf(RepositoryOperationException.class, failure.getSuppressed()[1]);
        assertEquals(RepositoryPhase.READ_SESSION_CLOSE, sessionClose.phase());
        assertSame(backend.closeFailure, sessionClose.effectivePrimary());
        assertEquals(RepositoryPhase.REPOSITORY_CLOSE, driverClose.phase());
        assertSame(backend.driverCloseFailure, driverClose.effectivePrimary().getCause());
        assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
      }
    }
  }

  @Test
  void givenValidBranches_whenFindingThroughAdapter_thenExactClosedValuesAndNormalCleanupSurvive() {
    // Arrange
    var ast = StatementBranchFixture.produce(directory, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var result = ast.result().orElseThrow();
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstResultCodec().map(ast);
    // Act
    AstLookup lookup;
    try (var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend)) {
      lookup = repository.find(result.identity());
    }
    // Assert
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
  }

  @Test
  void givenExpandedClosedValue_whenFindingThroughAdapter_thenEverySourceValueAndProfileSurvive() throws IOException {
    // Arrange: the driver is supplied; this observes decoding, not database persistence.
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), "class Stored { int value(int x) { return x + 7; } }"))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED);
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstResultCodec().map(ast);

    // Act
    AstLookup lookup;
    try (var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend)) {
      lookup = repository.find(ast.result().orElseThrow().identity());
    }

    // Assert
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(AstProfile.JAVA_SOURCE_EXPANDED, lookup.outcome().orElseThrow().profile());
    assertEquals(ast.result().orElseThrow().aggregate(), lookup.outcome().orElseThrow().result().orElseThrow().aggregate());
    assertEquals(ast.result().orElseThrow().coverage(), lookup.outcome().orElseThrow().result().orElseThrow().coverage());
    assertEquals("driver.close", backend.events.getLast());
  }

  @Test
  void givenPresentUnsupportedSourceProfile_whenFinding_thenIncompatibleIsDistinctFromMissingOrDecodeFailure() throws IOException {
    // Arrange: an isolated stored version, not compiler output or a database claim.
    var ast = produce();
    var projection = new Neo4jAstResultCodec().map(ast);
    var original = projection.identity();
    var unknown = AstIdentity.fromContent(original.softwareSignature(), original.sourceModelVersion(), "java-source-expanded/99",
        original.semanticProductionRevision(), original.canonicalContentDigest());
    var root = projection.root();
    var properties = new HashMap<>(root.properties());
    properties.put("astProfileVersion", unknown.astProfileVersion());
    properties.put("astResultKey", unknown.astResultKey());
    properties.put("key", unknown.astResultKey());
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstProjection(unknown, List.of(new Neo4jNode(unknown.astResultKey(), root.label(), properties)), List.of());

    // Act
    AstLookup lookup;
    try (var repository = new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(),
        new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, unknown))) {
      lookup = repository.find(unknown);
    }

    // Assert
    assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
    assertEquals("java-source-expanded/99", lookup.unsupportedFormat().orElseThrow());
    assertTrue(lookup.outcome().isEmpty());
    assertEquals("driver.close", backend.events.getLast());
  }

  @Test
  void givenStoredProfileContradictingRequestedIdentity_whenFinding_thenDecodeFailureCannotBecomeIncompatible() throws IOException {
    // Arrange
    var ast = produce();
    var projection = new Neo4jAstResultCodec().map(ast);
    var root = projection.root();
    var properties = new HashMap<>(root.properties());
    properties.put("astProfileVersion", "java-source-expanded/99");
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstProjection(projection.identity(),
        List.of(new Neo4jNode(root.key(), root.label(), properties)), List.of());

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> {
      try (var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend)) {
        repository.find(projection.identity());
      }
    });

    // Assert
    assertEquals(RepositoryPhase.DECODE, failure.phase());
    assertTrue(failure.effectivePrimary().getMessage().contains("source profile differs"));
    assertEquals("driver.close", backend.events.getLast());
  }

  @Test
  void givenProjectionMappingFailure_whenSaving_thenExactFailurePrecedesAllStorageEffectsAndDriverCleanup() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    var primary = new IllegalStateException("Projection mapping failed");
    backend.driverCloseFailure = new Neo4jException("Driver cleanup after mapping failed");
    var operationContext = context(ast, RepositoryOperationKind.SAVE_AST);

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> {
      try (var repository = new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), operationContext,
          new Neo4jAstResultCodec(), ignored -> { throw primary; })) {
        repository.saveComplete(ast);
      }
    });

    // Assert
    assertEquals(operationContext, failure.context());
    assertEquals(RepositoryPhase.MAPPING, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    assertSame(primary, failure.effectivePrimary());
    assertEquals(1, failure.getSuppressed().length);
    var cleanup = assertInstanceOf(RepositoryOperationException.class, failure.getSuppressed()[0]);
    assertEquals(operationContext, cleanup.context());
    assertEquals(RepositoryPhase.REPOSITORY_CLOSE, cleanup.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, cleanup.knowledge());
    assertSame(backend.driverCloseFailure, cleanup.effectivePrimary().getCause());
    assertEquals(List.of("driver.close"), backend.events);
    assertTrue(backend.queries.isEmpty());
  }

  @Test
  void givenForeignMappedSubject_whenSaving_thenMappingRejectsItBeforeOpeningStorage() throws IOException {
    // Arrange
    var ast = produce();
    var foreign = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Other.java"), "class Other { int value() { return 9; } }"))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
    var codec = new Neo4jAstResultCodec();
    var foreignProjection = codec.map(foreign);
    var backend = new AstRepositoryDriverStub();
    var operationContext = context(ast, RepositoryOperationKind.SAVE_AST);

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> {
      try (var repository = new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), operationContext,
          codec, ignored -> foreignProjection)) {
        repository.saveComplete(ast);
      }
    });

    // Assert
    assertEquals(operationContext, failure.context());
    assertEquals(RepositoryPhase.MAPPING, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(List.of("driver.close"), backend.events);
    assertTrue(backend.queries.isEmpty());
  }

  @Test
  void givenReadWorkAndCleanupFailures_whenFinding_thenExactReadCauseAndOrderedCleanupPreventLookup() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    var primary = new Neo4jException("Stored result read failed");
    backend.readFailure = primary;
    backend.closeFailure = new IllegalStateException("Read session cleanup failed");
    backend.failingClose = 0;
    backend.driverCloseFailure = new Neo4jException("Driver cleanup after read failed");
    var operationContext = context(ast, RepositoryOperationKind.QUERY_AST);

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> {
      try (var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend)) {
        repository.find(ast.result().orElseThrow().identity());
      }
    });

    // Assert
    assertEquals(operationContext, failure.context());
    assertEquals(RepositoryPhase.READ, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    assertInstanceOf(RepositoryException.class, failure.effectivePrimary());
    assertSame(primary, failure.effectivePrimary().getCause());
    assertEquals(2, failure.getSuppressed().length);
    var sessionCleanup = assertInstanceOf(RepositoryOperationException.class, failure.getSuppressed()[0]);
    assertEquals(operationContext, sessionCleanup.context());
    assertEquals(RepositoryPhase.READ_SESSION_CLOSE, sessionCleanup.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, sessionCleanup.knowledge());
    assertSame(backend.closeFailure, sessionCleanup.effectivePrimary());
    var driverCleanup = assertInstanceOf(RepositoryOperationException.class, failure.getSuppressed()[1]);
    assertEquals(operationContext, driverCleanup.context());
    assertEquals(RepositoryPhase.REPOSITORY_CLOSE, driverCleanup.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, driverCleanup.knowledge());
    assertSame(backend.driverCloseFailure, driverCleanup.effectivePrimary().getCause());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
    assertEquals(1, backend.queries.size());
    assertTrue(backend.queries.getFirst().contains("RETURN properties(result)"));
  }

  @Test
  void givenCompleteSource_whenSaving_thenSchemaClosePrecedesReplacementAndWholeOperationAcknowledgement() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    var repository = repository(ast, RepositoryOperationKind.SAVE_AST, backend);

    // Act
    repository.saveComplete(ast);
    repository.close();

    // Assert
    assertTrue(backend.events.indexOf("session.close:0") < backend.events.indexOf("write.begin"));
    assertTrue(backend.events.indexOf("callback.return") < backend.events.indexOf("write.acknowledged"));
    assertEquals("driver.close", backend.events.getLast());
    assertTrue(backend.queries.stream().anyMatch(query -> query.contains("MATCH (entity:AstEntity")));
    assertTrue(backend.queries.stream().noneMatch(query -> query.contains("AnalysisEntity")));
  }

  @Test
  void givenSchemaAndSchemaCloseFailures_whenSaving_thenReplacementNeverBeginsAndBothCreatingPhasesSurvive() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    var primary = new Neo4jException("Schema inspection failed");
    var cleanup = new IllegalStateException("Schema session close failed");
    backend.schemaFailure = primary;
    backend.closeFailure = cleanup;
    backend.failingClose = 0;

    // Act
    var failure = assertThrows(RepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.SAVE_AST, backend).saveComplete(ast));

    // Assert
    assertEquals(RepositoryPhase.SCHEMA, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    assertTrue(failure.effectivePrimary() instanceof RepositoryException);
    assertSame(primary, failure.effectivePrimary().getCause());
    var suppressed = (RepositoryOperationException) failure.getSuppressed()[0];
    assertEquals(RepositoryPhase.SCHEMA_SESSION_CLOSE, suppressed.phase());
    assertSame(cleanup, suppressed.effectivePrimary());
    assertFalse(backend.events.contains("write.begin"));
  }

  @Test
  void givenCallbackReturnsAndAcknowledgementLoss_whenSaving_thenRetryDoesNotInventCommitOrRollback() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.attempts = 2;
    backend.writeFailure = new Neo4jException("Acknowledgement unavailable");
    backend.closeFailure = new IllegalArgumentException("Publication session close failed");
    backend.failingClose = 1;

    // Act
    var failure = assertThrows(RepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.SAVE_AST, backend).saveComplete(ast));

    // Assert
    assertEquals(2, backend.events.stream().filter("callback.return"::equals).count());
    assertEquals(RepositoryPhase.REPLACEMENT, failure.phase());
    assertEquals(CommitKnowledge.UNKNOWN, failure.knowledge());
    var cleanup = (RepositoryOperationException) failure.getSuppressed()[0];
    assertEquals(RepositoryPhase.PUBLICATION_SESSION_CLOSE, cleanup.phase());
    assertEquals(CommitKnowledge.UNKNOWN, cleanup.knowledge());
    assertSame(backend.closeFailure, cleanup.effectivePrimary());
    assertFalse(backend.events.contains("write.acknowledged"));
  }

  @Test
  void givenAcknowledgedCommitAndPublicationCloseFailure_whenSaving_thenKnownCommitSurvives() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.closeFailure = new Neo4jException("Publication close failed");
    backend.failingClose = 1;
    var repository = repository(ast, RepositoryOperationKind.SAVE_AST, backend);

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> repository.saveComplete(ast));
    repository.close();

    // Assert
    assertEquals(RepositoryPhase.PUBLICATION_SESSION_CLOSE, failure.phase());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, failure.knowledge());
    assertTrue(backend.events.contains("write.acknowledged"));
  }

  @Test
  void givenWrongOperation_whenSaving_thenValidationFailurePrecedesAnySession() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();

    // Act
    var failure = assertThrows(RepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).saveComplete(ast));

    // Assert
    assertEquals(RepositoryPhase.VALIDATION, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    assertTrue(backend.events.isEmpty());
  }

  @Test
  void givenAbsentSubjectOrUnsupportedVersion_whenFinding_thenReadClosesBeforeNormalReturnWithoutLoadingMaterialization() throws IOException {
    for (var version : List.of("absent", "unsupported-java-source")) {
      // Arrange
      var ast = produce();
      var backend = new AstRepositoryDriverStub();
      backend.version = version;
      var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend);

      // Act
      var lookup = repository.find(ast.result().orElseThrow().identity());

      // Assert
      assertEquals(version.equals("absent") ? AstLookupStatus.NOT_FOUND : AstLookupStatus.INCOMPATIBLE, lookup.status());
      assertEquals("session.close:0", backend.events.getLast());
      assertEquals(version.equals("absent") ? 2 : 1, backend.queries.size());
      assertTrue(lookup.outcome().isEmpty());
    }
  }

  @Test
  void givenNormalAbsentReadAndReadCloseFailure_whenFinding_thenNoAbsenceResultIsReturned() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.closeFailure = new Neo4jException("Read close failed");
    backend.failingClose = 0;

    // Act
    var failure = assertThrows(RepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).find(ast.result().orElseThrow().identity()));

    // Assert
    assertEquals(RepositoryPhase.READ_SESSION_CLOSE, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
  }

  @Test
  void givenSupportedPresentRootAndMissingValues_whenFinding_thenDecodeFailureCannotBecomeAbsence() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.version = Neo4jAstResultCodec.FORMAT_VERSION;

    // Act
    var failure = assertThrows(RepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).find(ast.result().orElseThrow().identity()));

    // Assert
    assertEquals(RepositoryPhase.DECODE, failure.phase());
    assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    assertEquals("session.close:0", backend.events.getLast());
  }

  @Test
  void givenMalformedSupportedEnvelopeAndReadCloseFailure_whenFinding_thenSourceContractFailureAndCleanupRemainObservable()
      throws IOException {
    // Arrange
    var ast = produce();
    var projection = new Neo4jAstResultCodec().map(ast);
    var nodes = new ArrayList<>(projection.nodes());
    var root = projection.root();
    var properties = new HashMap<>(root.properties());
    var encoded = AstCodec.encode(ast.result().orElseThrow());
    properties.put("payload", Base64.getEncoder().encodeToString(Arrays.copyOf(encoded, encoded.length - 1)));
    nodes.set(nodes.indexOf(root), new Neo4jNode(root.key(), root.label(), properties));
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstProjection(projection.identity(), nodes, projection.relationships());
    backend.closeFailure = new IllegalStateException("Read cleanup failed after source rejection");
    backend.failingClose = 0;

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> {
      try (var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend)) {
        repository.find(ast.result().orElseThrow().identity());
      }
    });

    // Assert
    assertEquals(context(ast, RepositoryOperationKind.QUERY_AST), failure.context());
    assertEquals(RepositoryPhase.DECODE, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    var sourceFailure = assertInstanceOf(AstContractException.class, failure.effectivePrimary());
    assertTrue(sourceFailure.getMessage().contains("Retained AST decoding failed"));
    assertInstanceOf(IllegalArgumentException.class, sourceFailure.getCause());
    var cleanup = assertInstanceOf(RepositoryOperationException.class, failure.getSuppressed()[0]);
    assertEquals(RepositoryPhase.READ_SESSION_CLOSE, cleanup.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, cleanup.knowledge());
    assertSame(backend.closeFailure, cleanup.effectivePrimary());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
  }

  @Test
  void givenSetupFailureAfterDriverReturn_whenAcquiring_thenPartialOwnershipClosesAndPreservesBothFailures() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.driverCloseFailure = new Neo4jException("Partial driver close failed");
    var primary = new IllegalArgumentException("Configuration failed");
    var factory = new Neo4jAstResultRepositoryFactory(Neo4jAstResultRepositoryTest::settings, ignored -> backend.driver(), ignored -> {
      throw primary;
    });
    var context = context(ast, RepositoryOperationKind.SAVE_AST);

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> factory.open(context));

    // Assert
    assertEquals(RepositoryPhase.ACQUISITION, failure.phase());
    assertSame(primary, failure.effectivePrimary());
    var cleanup = (RepositoryOperationException) failure.getSuppressed()[0];
    assertEquals(context, cleanup.context());
    assertEquals(RepositoryPhase.REPOSITORY_CLOSE, cleanup.phase());
    assertSame(backend.driverCloseFailure, cleanup.effectivePrimary().getCause());
    assertEquals(List.of("driver.close"), backend.events);
  }

  @Test
  void givenMalformedStreamedNode_whenReadCompletes_thenContentFailureRetainsDecodePhase() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstResultCodec().map(ast);
    backend.malformedNodeRecords = true;

    // Act
    var failure = assertThrows(RepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).find(ast.result().orElseThrow().identity()));

    // Assert
    assertEquals(RepositoryPhase.DECODE, failure.phase());
    assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
    assertTrue(backend.queries.stream().anyMatch(query -> query.contains("type(relation)")));
    assertEquals("session.close:0", backend.events.getLast());
  }

  @Test
  void givenMalformedNodeAndLaterStreamFailure_whenFinding_thenTransportFailureRetainsReadPriority() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstResultCodec().map(ast);
    backend.malformedNodeRecords = true;
    backend.nodeStreamFailure = new Neo4jException("Transport failed after malformed content");

    // Act
    var failure = assertThrows(RepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).find(ast.result().orElseThrow().identity()));

    // Assert
    assertEquals(RepositoryPhase.READ, failure.phase());
    assertSame(backend.nodeStreamFailure, failure.effectivePrimary().getCause());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
    assertEquals("session.close:0", backend.events.getLast());
  }

  AstOutcome produce() throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), "class Stored { int value() { return 1; } }"))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static RepositoryOperationContext context(AstOutcome ast, RepositoryOperationKind kind) {
    return new RepositoryOperationContext(kind, ast.result().orElseThrow().identity());
  }

  private static Neo4jAstResultRepository repository(AstOutcome ast, RepositoryOperationKind kind, AstRepositoryDriverStub backend) {
    return new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), context(ast, kind));
  }

  private static Neo4jConnectionSettings settings() {
    return new Neo4jConnectionSettings(URI.create("bolt://localhost:7687"), "neo4j", "test-only", "neo4j");
  }

}
