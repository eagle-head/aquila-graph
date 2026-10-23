package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jAstResultRepositoryTest {
  @TempDir Path directory;

  @Test
  void givenExpandedClosedValue_whenFindingThroughAdapter_thenEverySourceValueAndProfileSurvive() throws IOException {
    // Arrange: the driver is supplied; this observes decoding, not database persistence.
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), "class Stored { int value(int x) { return x + 7; } }"))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED);
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();

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
      var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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
    var backend = new Backend();
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

  private static Neo4jAstResultRepository repository(AstOutcome ast, RepositoryOperationKind kind, Backend backend) {
    return new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), context(ast, kind));
  }

  private static Neo4jConnectionSettings settings() {
    return new Neo4jConnectionSettings(URI.create("bolt://localhost:7687"), "neo4j", "test-only", "neo4j");
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
  }

  /** Explicit isolated driver observations; this double does not establish database commits. */
  private static final class Backend {
    private final List<String> events = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private int sessions;
    private int attempts = 1;
    private int failingClose = -1;
    private RuntimeException schemaFailure;
    private RuntimeException readFailure;
    private RuntimeException writeFailure;
    private RuntimeException closeFailure;
    private RuntimeException driverCloseFailure;
    private RuntimeException nodeStreamFailure;
    private boolean malformedNodeRecords;
    private String version = "absent";
    private Neo4jAstProjection stored;

    private Driver driver() {
      return proxy(Driver.class, (ignored, method, arguments) -> switch (method.getName()) {
        case "session" -> session(sessions++);
        case "close" -> {
          events.add("driver.close");
          if (driverCloseFailure != null) { throw driverCloseFailure; }
          yield null;
        }
        default -> throw new AssertionError("Unexpected driver operation: " + method.getName());
      });
    }

    private Session session(int ordinal) {
      events.add("session.open:" + ordinal);
      return proxy(Session.class, (ignored, method, arguments) -> switch (method.getName()) {
        case "run" -> {
          var query = (String) arguments[0];
          queries.add(query);
          if (schemaFailure != null) { throw schemaFailure; }
          yield result(query.startsWith("SHOW CONSTRAINTS") ? constraints() : List.of());
        }
        case "executeWrite" -> {
          events.add("write.begin");
          var callback = (TransactionCallback<?>) arguments[0];
          for (var attempt = 0; attempt < attempts; attempt++) {
            callback.execute(transaction());
            events.add("callback.return");
          }
          if (writeFailure != null) { throw writeFailure; }
          events.add("write.acknowledged");
          yield null;
        }
        case "executeRead" -> ((TransactionCallback<?>) arguments[0]).execute(transaction());
        case "close" -> {
          events.add("session.close:" + ordinal);
          if (ordinal == failingClose) { throw closeFailure; }
          yield null;
        }
        default -> throw new AssertionError("Unexpected session operation: " + method.getName());
      });
    }

    private TransactionContext transaction() {
      return proxy(TransactionContext.class, (ignored, method, arguments) -> {
        if (!method.getName().equals("run")) { throw new AssertionError(method.getName()); }
        var query = (String) arguments[0];
        queries.add(query);
        if (query.contains("RETURN properties(result)") && readFailure != null) { throw readFailure; }
        var rows = query.contains("RETURN properties(result)") && !version.equals("absent")
            ? List.of(record(Map.of("properties", Map.of("formatVersion", version)))) : List.<Record>of();
        if (query.contains("RETURN entity.key AS key, elementId(entity) AS address")) {
          rows = ((Value) arguments[1]).get("nodes").asList(value -> value.asMap()).stream()
              .map(node -> record(Map.of("key", node.get("key"), "address", "node/" + node.get("key")))).toList();
        }
        if (stored != null) {
          if (query.contains("RETURN properties(result)")) {
            rows = List.of(record(Map.of("properties", stored.root().properties())));
          } else if (query.contains("labels(entity)")) {
            rows = stored.nodes().stream().map(node -> record(Map.of("key", node.key(), "labels",
                malformedNodeRecords ? List.of("AstEntity", node.label(), "Unexpected") : List.of("AstEntity", node.label()),
                "properties", node.properties(), "address", "node/" + node.key()))).toList();
          } else if (query.contains("elementId(relation) AS address")) {
            rows = stored.relationships().stream().map(relation -> record(Map.of("address", "relation/" + relation.key(),
                "key", relation.key(), "type", relation.type(), "properties", relation.properties(),
                "source", "node/" + relation.sourceKey(), "target", "node/" + relation.targetKey()))).toList();
          } else if (query.contains("type(relation)")) {
            var labelsByKey = new HashMap<String, List<String>>();
            stored.nodes().forEach(node -> labelsByKey.put(node.key(), List.of("AstEntity", node.label())));
            rows = stored.relationships().stream().map(relation -> record(Map.of("key", relation.key(), "type", relation.type(),
                "sourceKey", relation.sourceKey(), "targetKey", relation.targetKey(), "properties", relation.properties(),
                "sourceLabels", labelsByKey.get(relation.sourceKey()), "sourceAstResultKey", stored.identity().astResultKey(),
                "targetLabels", labelsByKey.get(relation.targetKey()), "targetAstResultKey", stored.identity().astResultKey()))).toList();
          }
        }
        if (query.contains("RETURN count(entity)")) {
          rows = List.of(record(Map.of("count", 0L)));
        }
        return result(rows, query.contains("labels(entity)") ? nodeStreamFailure : null);
      });
    }

    private static Result result(List<Record> records) {
      return result(records, null);
    }

    private static Result result(List<Record> records, RuntimeException streamFailure) {
      return proxy(Result.class, (ignored, method, arguments) -> switch (method.getName()) {
        case "consume" -> null;
        case "stream" -> streamFailure == null ? records.stream() : Stream.concat(records.stream(), Stream.<Record>generate(() -> {
          throw streamFailure;
        }).limit(1));
        case "single" -> records.getFirst();
        case "list" -> {
          if (arguments != null && arguments.length != 0) { throw new AssertionError("Unexpected mapped result"); }
          yield records;
        }
        default -> throw new AssertionError("Unexpected result operation: " + method.getName());
      });
    }

    private static Record record(Map<String, Object> properties) {
      return proxy(Record.class, (ignored, method, arguments) -> switch (method.getName()) {
        case "get" -> Values.value(properties.get((String) arguments[0]));
        default -> throw new AssertionError("Unexpected record operation: " + method.getName());
      });
    }

    private static List<Record> constraints() {
      return List.of(constraint("ast_entity_identity", "AstEntity", List.of("astResultKey", "key")),
          constraint("ast_result_identity", "AstResult", List.of("astResultKey")));
    }

    private static Record constraint(String name, String label, List<String> properties) {
      return record(Map.of("name", name, "type", "NODE_PROPERTY_UNIQUENESS", "entityType", "NODE", "labelsOrTypes", List.of(label),
          "properties", properties));
    }
  }
}
