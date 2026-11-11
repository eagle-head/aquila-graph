package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.testsupport.StatementBranchFixture;
import org.aquilagraph.testsupport.SourceCompositionFixture;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jAstResultRepositoryTest {
  @TempDir Path directory;

  @Test
  void givenComposedClosedSource_whenExplicitlyRetainedAndReadThroughApplicationAdapter_thenFactsAndCleanupSurvive() {
    // Arrange
    var original = SourceCompositionFixture.produce(directory, true);
    var outcome = AstOutcome.available(original);
    var writer = new AstRepositoryDriverStub();
    var reader = new AstRepositoryDriverStub();
    reader.stored = new Neo4jAstResultCodec().map(outcome);
    var query = AnalysisCapabilities.astQuery(factory(reader)::openQuery);
    var retention = AnalysisCapabilities.astRetention(factory(writer)::openRetention);
    var noSave = AnalysisCapabilities.astRetention(context -> {
      throw new AssertionError("Explicit no-save cannot acquire an adapter");
    });
    // Act
    var declined = noSave.retainAst(new AstPublicationRequest(outcome, PublicationIntent.DO_NOT_SAVE));
    var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
    var lookup = query.findAst(original.identity());
    // Assert
    assertEquals(PublicationStatus.NOT_REQUESTED, declined.status());
    assertEquals(CommitKnowledge.NOT_STARTED, declined.knowledge());
    assertEquals(PublicationStatus.SAVED, publication.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
    assertEquals(Optional.of(new Ast(original.identity())), publication.subjectIdentity());
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(original.identity(), lookup.requestedIdentity());
    var decoded = lookup.outcome().orElseThrow().result().orElseThrow();
    SourceCompositionFixture.assertComposition(decoded, true);
    SourceCompositionFixture.assertRoundTrip(original, decoded);
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return",
        "write.acknowledged", "session.close:1", "driver.close"), writer.events);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), reader.events);
  }

  @Test
  void givenAcknowledgedAstAndSecondSchemaFailure_whenRetainingTwice_thenEachAcquisitionKeepsItsOwnSubjectAndKnowledge() throws IOException {
    // Arrange
    var original = produce();
    var next = produce("Other.java", "class Other {}");
    var nextContext = context(next, RepositoryOperationKind.SAVE_AST);
    var writer = new AstRepositoryDriverStub();
    var rejected = new AstRepositoryDriverStub();
    var schemaFailure = new Neo4jException("Schema observation failed");
    rejected.schemaFailure = schemaFailure;
    var backends = List.of(writer, rejected).iterator();
    var factory = new Neo4jAstResultRepositoryFactory(Neo4jAstResultRepositoryTest::settings, (uri, auth) -> backends.next().driver());
    var retention = AnalysisCapabilities.astRetention(factory::openRetention);

    // Act
    var saved = retention.retainAst(new AstPublicationRequest(original, PublicationIntent.SAVE_COMPLETE_AST));
    var failed = retention.retainAst(new AstPublicationRequest(next, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(PublicationStatus.SAVED, saved.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, saved.knowledge());
    assertEquals(Optional.of(context(original, RepositoryOperationKind.SAVE_AST).subject()), saved.subjectIdentity());
    assertEquals(PublicationStatus.FAILED, failed.status());
    assertEquals(CommitKnowledge.NOT_STARTED, failed.knowledge());
    assertEquals(Optional.of(nextContext.subject()), failed.subjectIdentity());
    var observation = assertInstanceOf(Neo4jRepositoryOperationException.class, failed.failure().orElseThrow().carrier());
    assertEquals(nextContext, observation.context());
    assertEquals(Neo4jRepositoryPhase.SCHEMA, observation.phase());
    assertSame(schemaFailure, assertInstanceOf(RepositoryException.class, observation.effectivePrimary()).getCause());
    assertEquals("driver.close", writer.events.getLast());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), rejected.events);
  }

  @Test
  void givenSuccessfulLookupAndSecondReadFailure_whenQueryingTwice_thenEachAcquisitionKeepsItsOwnIdentityAndClosure() throws IOException {
    // Arrange
    var original = produce();
    var originalIdentity = original.result().orElseThrow().identity();
    var nextIdentity = produce("Other.java", "class Other {}").result().orElseThrow().identity();
    var reader = new AstRepositoryDriverStub();
    reader.stored = new Neo4jAstResultCodec().map(original);
    var rejected = new AstRepositoryDriverStub();
    var readFailure = new Neo4jException("Second read failed");
    rejected.readFailure = readFailure;
    var backends = List.of(reader, rejected).iterator();
    var factory = new Neo4jAstResultRepositoryFactory(Neo4jAstResultRepositoryTest::settings, (uri, auth) -> backends.next().driver());
    var query = AnalysisCapabilities.astQuery(factory::openQuery);

    // Act
    var found = query.findAst(originalIdentity);
    var failed = assertThrows(Neo4jRepositoryOperationException.class, () -> query.findAst(nextIdentity));

    // Assert
    assertEquals(AstLookupStatus.FOUND, found.status());
    assertEquals(originalIdentity, found.requestedIdentity());
    assertEquals(originalIdentity, found.outcome().orElseThrow().result().orElseThrow().identity());
    assertEquals(RepositoryOperationContext.queryAst(nextIdentity), failed.context());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failed.knowledge());
    assertEquals(Neo4jRepositoryPhase.READ, failed.phase());
    assertSame(readFailure, assertInstanceOf(RepositoryException.class, failed.effectivePrimary()).getCause());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), reader.events);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), rejected.events);
  }

  @Test
  void givenExactIncompleteAst_whenSavingThroughAcquiredRetention_thenValidationRejectsBeforeAnySession() throws IOException {
    // Arrange
    var incomplete = produce("Incomplete.java", "class Incomplete { int field = 1; }");
    var backend = new AstRepositoryDriverStub();
    var requested = context(incomplete, RepositoryOperationKind.SAVE_AST);
    var factory = factory(backend);

    // Act
    Neo4jRepositoryOperationException failure;
    try (var repository = factory.openRetention(requested)) {
      failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.saveComplete(incomplete));
    }

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, incomplete.completion());
    assertEquals(requested, failure.context());
    assertEquals(Neo4jRepositoryPhase.VALIDATION, failure.phase());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
    assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
    assertEquals(List.of("driver.close"), backend.events);
    assertTrue(backend.queries.isEmpty());
  }

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
        var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> {
          try (var repository = repository(ast, RepositoryOperationKind.QUERY_AST, backend)) {
            repository.find(result.identity());
          }
        });
        // Assert
        assertEquals(context(ast, RepositoryOperationKind.QUERY_AST), failure.context());
        assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
        assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
        var sourceFailure = assertInstanceOf(AstContractException.class, failure.effectivePrimary());
        assertTrue(sourceFailure.getMessage().contains("child category"), sourceFailure.toString());
        assertInstanceOf(IllegalArgumentException.class, sourceFailure.getCause());
        assertEquals(2, failure.getSuppressed().length);
        var sessionClose = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]);
        var driverClose = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[1]);
        assertEquals(Neo4jRepositoryPhase.READ_SESSION_CLOSE, sessionClose.phase());
        assertSame(backend.closeFailure, sessionClose.effectivePrimary());
        assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, driverClose.phase());
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
        RepositoryOperationContext.queryAst(unknown))) {
      lookup = repository.find(unknown);
    }

    // Assert
    assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
    assertEquals("java-source-expanded/99", lookup.unsupportedFormat().orElseThrow());
    assertTrue(lookup.outcome().isEmpty());
    assertEquals("driver.close", backend.events.getLast());
  }

  @Test
  void givenContradictoryStoredProfileAndMalformedRows_whenQuerying_thenProfileRejectionPrecedesContentFailure() throws IOException {
    // Arrange
    var ast = produce();
    var projection = new Neo4jAstResultCodec().map(ast);
    var root = projection.root();
    var properties = new HashMap<>(root.properties());
    properties.put("astProfileVersion", "java-source-expanded/99");
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstProjection(projection.identity(),
        List.of(new Neo4jNode(root.key(), root.label(), properties)), List.of());
    backend.malformedNodeRecords = true;
    var query = AnalysisCapabilities.astQuery(factory(backend)::openQuery);

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query.findAst(projection.identity()));

    // Assert
    assertEquals(RepositoryOperationContext.queryAst(projection.identity()), failure.context());
    assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    var primary = assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
    assertEquals("Stored AST source profile differs from the exact requested identity", primary.getMessage());
    assertTrue(backend.queries.getLast().contains("type(relation)"));
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
  }

  @Test
  void givenContradictoryStoredProfileAndRelationshipTailFailure_whenQuerying_thenTheOriginalReadFailureWins() throws IOException {
    // Arrange
    var projection = new Neo4jAstResultCodec().map(produce());
    var identity = projection.identity();
    var properties = new HashMap<>(projection.root().properties());
    assertEquals(identity.astProfileVersion(), properties.put("astProfileVersion", "java-source-expanded/99"));
    assertFalse(projection.relationships().isEmpty());
    var backend = new AstRepositoryDriverStub();
    backend.stored = projection;
    backend.rootProperties = List.of(properties);
    var tailFailure = new Neo4jException("Read failed after materialized relationship rows");
    backend.relationshipStreamFailure = tailFailure;
    var query = AnalysisCapabilities.astQuery(factory(backend)::openQuery);

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query.findAst(identity));

    // Assert
    assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
    assertEquals(Neo4jRepositoryPhase.READ, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertSame(tailFailure, assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).getCause());
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
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
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> {
      try (var repository = new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), operationContext,
          new Neo4jAstResultCodec(), ignored -> { throw primary; })) {
        repository.saveComplete(ast);
      }
    });

    // Assert
    assertEquals(operationContext, failure.context());
    assertEquals(Neo4jRepositoryPhase.MAPPING, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertSame(primary, failure.effectivePrimary());
    assertEquals(1, failure.getSuppressed().length);
    var cleanup = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]);
    assertEquals(operationContext, cleanup.context());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, cleanup.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, cleanup.knowledge().orElseThrow());
    assertSame(backend.driverCloseFailure, cleanup.effectivePrimary().getCause());
    assertEquals(List.of("driver.close"), backend.events);
    assertTrue(backend.queries.isEmpty());
  }

  @Test
  void givenForeignMappedSubject_whenSaving_thenMappingRejectsItBeforeOpeningStorage() throws IOException {
    // Arrange
    var ast = produce();
    var foreign = produce("Other.java", "class Other { int value() { return 9; } }");
    var codec = new Neo4jAstResultCodec();
    var foreignProjection = codec.map(foreign);
    var backend = new AstRepositoryDriverStub();
    var operationContext = context(ast, RepositoryOperationKind.SAVE_AST);

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> {
      try (var repository = new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), operationContext,
          codec, ignored -> foreignProjection)) {
        repository.saveComplete(ast);
      }
    });

    // Assert
    assertEquals(operationContext, failure.context());
    assertEquals(Neo4jRepositoryPhase.MAPPING, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(List.of("driver.close"), backend.events);
    assertTrue(backend.queries.isEmpty());
  }

  @Test
  void givenReadWorkAndCleanupFailures_whenFinding_thenExactReadCauseAndOrderedCleanupPreventLookup() throws IOException {
    var ast = produce();
    var identity = ast.result().orElseThrow().identity();
    var operationContext = context(ast, RepositoryOperationKind.QUERY_AST);
    var driverFailure = new Neo4jException("Stored result read failed");
    var unmatched = new IllegalArgumentException("Unexpected read failure");
    var declared = new RepositoryException("Declared read failure", new IllegalStateException("Original cause"));
    for (var primary : List.<RuntimeException>of(driverFailure, unmatched, declared)) {
      // Arrange
      var backend = new AstRepositoryDriverStub();
      backend.readFailure = primary;
      backend.closeFailure = new IllegalStateException("Read session cleanup failed");
      backend.failingClose = 0;
      backend.driverCloseFailure = new Neo4jException("Driver cleanup after read failed");
      var query = AnalysisCapabilities.astQuery(factory(backend)::openQuery);

      // Act
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query.findAst(identity));

      // Assert
      assertEquals(operationContext, failure.context());
      assertEquals(Neo4jRepositoryPhase.READ, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
      if (primary == driverFailure) {
        var translated = assertInstanceOf(RepositoryException.class, failure.effectivePrimary());
        assertSame(primary, translated.getCause());
      } else {
        assertSame(primary, failure.effectivePrimary());
      }
      assertEquals(2, failure.getSuppressed().length);
      var sessionCleanup = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]);
      assertEquals(operationContext, sessionCleanup.context());
      assertEquals(Neo4jRepositoryPhase.READ_SESSION_CLOSE, sessionCleanup.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, sessionCleanup.knowledge().orElseThrow());
      assertSame(backend.closeFailure, sessionCleanup.effectivePrimary());
      var driverCleanup = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[1]);
      assertEquals(operationContext, driverCleanup.context());
      assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, driverCleanup.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, driverCleanup.knowledge().orElseThrow());
      assertSame(backend.driverCloseFailure, driverCleanup.effectivePrimary().getCause());
      assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
      assertEquals(1, backend.queries.size());
      assertTrue(backend.queries.getFirst().contains("RETURN properties(result)"));
    }
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
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.SAVE_AST, backend).saveComplete(ast));

    // Assert
    assertEquals(Neo4jRepositoryPhase.SCHEMA, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertTrue(failure.effectivePrimary() instanceof RepositoryException);
    assertSame(primary, failure.effectivePrimary().getCause());
    var suppressed = (Neo4jRepositoryOperationException) failure.getSuppressed()[0];
    assertEquals(Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE, suppressed.phase());
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
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.SAVE_AST, backend).saveComplete(ast));

    // Assert
    assertEquals(2, backend.events.stream().filter("callback.return"::equals).count());
    assertEquals(Neo4jRepositoryPhase.REPLACEMENT, failure.phase());
    assertEquals(CommitKnowledge.UNKNOWN, failure.knowledge().orElseThrow());
    var cleanup = (Neo4jRepositoryOperationException) failure.getSuppressed()[0];
    assertEquals(Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE, cleanup.phase());
    assertEquals(CommitKnowledge.UNKNOWN, cleanup.knowledge().orElseThrow());
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
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.saveComplete(ast));
    repository.close();

    // Assert
    assertEquals(Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE, failure.phase());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, failure.knowledge().orElseThrow());
    assertTrue(backend.events.contains("write.acknowledged"));
  }

  @Test
  void givenCompletedSaveOrQuery_whenRepeatedOnTheSameAstRepository_thenTypedRejectionPrecedesAnySecondSession() throws IOException {
    var ast = produce();
    var identity = ast.result().orElseThrow().identity();
    var operations = Map.<RepositoryOperationKind, Consumer<Neo4jAstResultRepository>>of(
        RepositoryOperationKind.SAVE_AST, repository -> repository.saveComplete(ast),
        RepositoryOperationKind.QUERY_AST, repository -> repository.find(identity));
    for (var entry : operations.entrySet()) {
      // Arrange
      var backend = new AstRepositoryDriverStub();
      var operation = entry.getValue();
      var requested = context(ast, entry.getKey());
      try (var repository = repository(ast, entry.getKey(), backend)) {
        operation.accept(repository);
        var priorEvents = List.copyOf(backend.events);
        var priorQueries = List.copyOf(backend.queries);

        // Act
        var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> operation.accept(repository));

        // Assert
        assertEquals(requested, failure.context());
        assertEquals(Neo4jRepositoryPhase.VALIDATION, failure.phase());
        var knowledge = entry.getKey() == RepositoryOperationKind.SAVE_AST ? CommitKnowledge.KNOWN_COMMIT : CommitKnowledge.NOT_STARTED;
        assertEquals(Optional.of(knowledge), failure.knowledge());
        assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
        assertEquals(priorEvents, backend.events);
        assertEquals(priorQueries, backend.queries);
      }
      assertEquals("driver.close", backend.events.getLast());
    }
  }

  @Test
  void givenDifferentAstSubject_whenAttemptingAndRetryingSaveOrQuery_thenBothFailBeforeOpeningSessions() throws IOException {
    // Arrange
    var ast = produce();
    var foreign = produce("Other.java", "class Other {}");
    var identity = ast.result().orElseThrow().identity();
    var foreignIdentity = foreign.result().orElseThrow().identity();
    var saveContext = RepositoryOperationContext.saveAst(identity);
    var queryContext = RepositoryOperationContext.queryAst(identity);
    var writer = new AstRepositoryDriverStub();
    var reader = new AstRepositoryDriverStub();
    try (var retention = factory(writer).openRetention(saveContext);
        var query = factory(reader).openQuery(queryContext)) {
      // Act
      var wrongSave = assertThrows(Neo4jRepositoryOperationException.class, () -> retention.saveComplete(foreign));
      var retrySave = assertThrows(Neo4jRepositoryOperationException.class, () -> retention.saveComplete(ast));
      var wrongQuery = assertThrows(Neo4jRepositoryOperationException.class, () -> query.find(foreignIdentity));
      var retryQuery = assertThrows(Neo4jRepositoryOperationException.class, () -> query.find(identity));

      // Assert
      for (var entry : Map.of(saveContext, List.of(wrongSave, retrySave), queryContext, List.of(wrongQuery, retryQuery)).entrySet()) {
        for (var failure : entry.getValue()) {
          assertEquals(entry.getKey(), failure.context());
          assertEquals(Neo4jRepositoryPhase.VALIDATION, failure.phase());
          assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
          assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
        }
      }
    }
    for (var backend : List.of(writer, reader)) {
      assertEquals(List.of("driver.close"), backend.events);
      assertTrue(backend.queries.isEmpty());
    }
  }

  @Test
  void givenWrongOperation_whenSaving_thenValidationFailurePrecedesAnySession() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).saveComplete(ast));

    // Assert
    assertEquals(Neo4jRepositoryPhase.VALIDATION, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
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
  void givenEachNormalLookupStateAndEitherCleanupFailure_whenQuerying_thenTheApplicableClosurePreventsReturn() throws IOException {
    // Arrange
    var projection = new Neo4jAstResultCodec().map(produce());
    var identity = projection.identity();
    for (var status : AstLookupStatus.values()) {
      for (var phase : List.of(Neo4jRepositoryPhase.READ_SESSION_CLOSE, Neo4jRepositoryPhase.REPOSITORY_CLOSE)) {
        var backend = new AstRepositoryDriverStub();
        if (status == AstLookupStatus.FOUND) {
          backend.stored = projection;
        } else if (status == AstLookupStatus.INCOMPATIBLE) {
          backend.version = "unsupported-java-source";
        }
        var close = new Neo4jException("Controlled " + phase + " failure after " + status);
        if (phase == Neo4jRepositoryPhase.READ_SESSION_CLOSE) {
          backend.closeFailure = close;
          backend.failingClose = 0;
        } else {
          backend.driverCloseFailure = close;
        }
        var query = AnalysisCapabilities.astQuery(factory(backend)::openQuery);

        // Act
        var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query.findAst(identity));

        // Assert
        assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
        assertEquals(phase, failure.phase());
        assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
        assertSame(close, assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
      }
    }
  }

  @Test
  void givenSupportedPresentRootAndMissingValues_whenFinding_thenDecodeFailureCannotBecomeAbsence() throws IOException {
    // Arrange
    var ast = produce();
    var backend = new AstRepositoryDriverStub();
    backend.version = Neo4jAstResultCodec.FORMAT_VERSION;

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).find(ast.result().orElseThrow().identity()));

    // Assert
    assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
    assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertEquals("session.close:0", backend.events.getLast());
  }

  @Test
  void givenMalformedSupportedEnvelopeAndCleanupFailures_whenQuerying_thenSourceFailurePrecedesBothClosures()
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
    backend.driverCloseFailure = new Neo4jException("Driver cleanup failed after source rejection");
    var query = AnalysisCapabilities.astQuery(factory(backend)::openQuery);

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query.findAst(projection.identity()));

    // Assert
    assertEquals(context(ast, RepositoryOperationKind.QUERY_AST), failure.context());
    assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    var sourceFailure = assertInstanceOf(AstContractException.class, failure.effectivePrimary());
    assertTrue(sourceFailure.getMessage().contains("Retained AST decoding failed"));
    assertInstanceOf(IllegalArgumentException.class, sourceFailure.getCause());
    assertEquals(2, failure.getSuppressed().length);
    var cleanup = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]);
    assertEquals(failure.context(), cleanup.context());
    assertEquals(Neo4jRepositoryPhase.READ_SESSION_CLOSE, cleanup.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, cleanup.knowledge().orElseThrow());
    assertSame(backend.closeFailure, cleanup.effectivePrimary());
    var driverCleanup = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[1]);
    assertEquals(failure.context(), driverCleanup.context());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, driverCleanup.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, driverCleanup.knowledge().orElseThrow());
    assertSame(backend.driverCloseFailure, assertInstanceOf(RepositoryException.class, driverCleanup.effectivePrimary()).getCause());
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
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> factory.openRetention(context));

    // Assert
    assertEquals(Neo4jRepositoryPhase.ACQUISITION, failure.phase());
    var declared = assertInstanceOf(RepositoryException.class, failure.effectivePrimary());
    assertEquals("Invalid Neo4j repository configuration", declared.description());
    assertSame(primary, declared.getCause());
    var cleanup = (Neo4jRepositoryOperationException) failure.getSuppressed()[0];
    assertEquals(context, cleanup.context());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, cleanup.phase());
    assertSame(backend.driverCloseFailure, cleanup.effectivePrimary().getCause());
    assertEquals(List.of("driver.close"), backend.events);
  }

  @Test
  void givenQueryAcquisitionAndPartialDriverCloseFailures_whenFinding_thenExactOperationAndBothCausesSurvive() throws IOException {
    // Arrange
    var ast = produce();
    var identity = ast.result().orElseThrow().identity();
    var operationContext = RepositoryOperationContext.queryAst(identity);
    var backend = new AstRepositoryDriverStub();
    var primary = new IllegalArgumentException("Query configuration failed");
    var cleanup = new Neo4jException("Partial query driver close failed");
    backend.driverCloseFailure = cleanup;
    var factory = new Neo4jAstResultRepositoryFactory(Neo4jAstResultRepositoryTest::settings, ignored -> backend.driver(), ignored -> {
      throw primary;
    });
    var query = AnalysisCapabilities.astQuery(factory::openQuery);

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query.findAst(identity));

    // Assert
    assertEquals(operationContext, failure.context());
    assertEquals(Neo4jRepositoryPhase.ACQUISITION, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    var declared = assertInstanceOf(RepositoryException.class, failure.effectivePrimary());
    assertSame(primary, declared.getCause());
    assertEquals(1, failure.getSuppressed().length);
    var closure = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]);
    assertEquals(operationContext, closure.context());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, closure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, closure.knowledge().orElseThrow());
    assertSame(cleanup, assertInstanceOf(RepositoryException.class, closure.effectivePrimary()).getCause());
    assertEquals(List.of("driver.close"), backend.events);
    assertTrue(backend.queries.isEmpty());
  }

  @Test
  void givenMalformedStreamedNode_whenQueryIsAttemptedTwice_thenDecodeFailureConsumesTheAcquiredOperation() throws IOException {
    // Arrange
    var ast = produce();
    var identity = ast.result().orElseThrow().identity();
    var backend = new AstRepositoryDriverStub();
    backend.stored = new Neo4jAstResultCodec().map(ast);
    backend.malformedNodeRecords = true;
    var requested = RepositoryOperationContext.queryAst(identity);

    // Act
    Neo4jRepositoryOperationException failure;
    Neo4jRepositoryOperationException retry;
    try (var query = factory(backend).openQuery(requested)) {
      failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query.find(identity));
      retry = assertThrows(Neo4jRepositoryOperationException.class, () -> query.find(identity));
    }

    // Assert
    assertEquals(requested, failure.context());
    assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    var invalidRow = assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
    assertTrue(invalidRow.getMessage().contains("exact namespace"));
    assertEquals(requested, retry.context());
    assertEquals(Neo4jRepositoryPhase.VALIDATION, retry.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, retry.knowledge().orElseThrow());
    var used = assertInstanceOf(IllegalArgumentException.class, retry.effectivePrimary());
    assertEquals("An AST repository has one exact operation lifetime", used.getMessage());
    assertEquals(3, backend.queries.size());
    assertTrue(backend.queries.getLast().contains("type(relation)"));
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), backend.events);
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
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> repository(ast, RepositoryOperationKind.QUERY_AST, backend).find(ast.result().orElseThrow().identity()));

    // Assert
    assertEquals(Neo4jRepositoryPhase.READ, failure.phase());
    assertSame(backend.nodeStreamFailure, failure.effectivePrimary().getCause());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertEquals("session.close:0", backend.events.getLast());
  }

  AstOutcome produce() throws IOException {
    return produce("Stored.java", "class Stored { int value() { return 1; } }");
  }

  private AstOutcome produce(String filename, String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of(filename), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static RepositoryOperationContext context(AstOutcome ast, RepositoryOperationKind kind) {
    return new RepositoryOperationContext(kind, new Ast(ast.result().orElseThrow().identity()));
  }

  private static Neo4jAstResultRepository repository(AstOutcome ast, RepositoryOperationKind kind, AstRepositoryDriverStub backend) {
    return new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), context(ast, kind));
  }

  private static Neo4jAstResultRepositoryFactory factory(AstRepositoryDriverStub backend) {
    return new Neo4jAstResultRepositoryFactory(Neo4jAstResultRepositoryTest::settings, (uri, auth) -> backend.driver());
  }

  private static Neo4jConnectionSettings settings() {
    return new Neo4jConnectionSettings(URI.create("bolt://localhost:7687"), "neo4j", "test-only", "neo4j");
  }

}
