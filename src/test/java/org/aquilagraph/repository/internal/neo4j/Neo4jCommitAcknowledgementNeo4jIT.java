package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.CompletedRunPublicationService;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.Values;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.exceptions.Neo4jException;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jCommitAcknowledgementNeo4jIT {
  private static final int DRIVER_WAIT_SECONDS = 10;
  private static final int PUBLICATION_WAIT_SECONDS = 30;
  private static final int WORKER_STOP_SECONDS = 5;

  @TempDir Path sourceRoot;

  @Test
  void givenCommitResponseWithheldOnLoopbackBolt_whenPublicationFails_thenDirectQueriesObserveWholeReplacementAndIndependentRun()
      throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var target = loopbackTarget(settings.uri());
    var directUri = directUri(target);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var primary = completedFixture("Acknowledgement" + UUID.randomUUID().toString().replace("-", ""), "previous");
    var independent = completedFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), "independent");
    var replacement = withWarning(primary, "replacement");
    var mapper = new Neo4jAnalysisRunMapper();
    var expectedReplacement = expectedState(mapper.map(replacement));
    var runIds = List.of(primary.identity().key(), independent.identity().key());

    try (var verification = new DriverWork(directUri, settings)) {
      await(verification.driver.verifyConnectivityAsync());
      try {
        try (var seeding = new DriverWork(directUri, settings)) {
          seeding.call(() -> {
            try (var repository = new Neo4jAnalysisRunRepository(seeding.driver, sessionConfig, mapper::map)) {
              repository.save(primary);
              repository.save(independent);
            }
            return null;
          });
        }
        var prior = storedState(verification.driver, sessionConfig, primary.identity().key());
        var independentBefore = storedState(verification.driver, sessionConfig, independent.identity().key());
        assertEquals(expectedState(mapper.map(primary)), prior);
        assertEquals(expectedState(mapper.map(independent)), independentBefore);
        assertNotEquals(prior, expectedReplacement);

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          var publicationService = new CompletedRunPublicationService(
              () -> new Neo4jAnalysisRunRepository(publication.driver, sessionConfig, mapper::map));
          proxy.arm();

          // Act
          var executionFailure = assertThrows(ExecutionException.class, () -> publication.call(() -> publicationService.publish(replacement)));

          // Assert
          var failure = assertInstanceOf(AnalysisRepositoryException.class, executionFailure.getCause());
          assertSame(replacement, failure.analysisRun());
          assertInstanceOf(Neo4jException.class, failure.repositoryFailure().getCause());
          proxy.requireHealthyRelay();
          var observation = proxy.observation();
          System.out.println("BOLT_COMMIT_ACKNOWLEDGEMENT_OBSERVATION=" + observation);
          assertNotEquals("not negotiated", observation.negotiatedVersion());
          assertEquals(1, observation.beginMessages());
          assertEquals(1, observation.commitMessages());
          assertEquals(1, observation.withheldCommitSuccesses());
          assertEquals(0, observation.forwardedCommitResponses());
          var observedReplacement = storedState(verification.driver, sessionConfig, primary.identity().key());
          assertEquals(expectedReplacement, observedReplacement);
          assertNotEquals(prior, observedReplacement);
          assertEquals(independentBefore, storedState(verification.driver, sessionConfig, independent.identity().key()));
        }
      } finally {
        cleanup(verification.driver, sessionConfig, runIds);
      }
    }
  }

  @Test
  void givenAstCommitResponseWithheld_whenRetentionFails_thenExactReplacementIsDurableWhileCommitKnowledgeRemainsUnknown()
      throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var target = loopbackTarget(settings.uri());
    var directUri = directUri(target);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var typeName = "Retained" + UUID.randomUUID().toString().replace("-", "");
    var previous = astFixture(typeName, sourceRoot.resolve("previous"));
    var replacement = astFixture(typeName, sourceRoot.resolve("replacement"));
    var independent = astFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), sourceRoot.resolve("independent"));
    var identity = replacement.result().orElseThrow().identity();
    var independentIdentity = independent.result().orElseThrow().identity();
    var context = new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, identity);
    var codec = new Neo4jAstResultCodec();
    var replacementProjection = codec.map(replacement);
    System.out.println("AST_TRANSPORT_PROJECTION_SIZES=" + replacementProjection.nodes().stream()
        .collect(Collectors.groupingBy(Neo4jNode::label, Collectors.summingInt(node -> node.properties().toString().length()))));
    var expectedReplacement = expectedAstState(replacementProjection);
    assertEquals(AstCompletion.COMPLETE, replacement.completion());
    assertEquals(previous.result().orElseThrow().identity(), identity);

    try (var verification = new DriverWork(directUri, settings)) {
      await(verification.driver.verifyConnectivityAsync());
      try {
        for (var outcome : List.of(previous, independent)) {
          try (var seeding = new DriverWork(directUri, settings)) {
            seeding.call(() -> {
              var saveContext = new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, outcome.result().orElseThrow().identity());
              try (var repository = new Neo4jAstResultRepository(seeding.driver, sessionConfig, saveContext)) {
                repository.saveComplete(outcome);
              }
              return null;
            });
          }
        }
        var prior = storedAstState(verification.driver, sessionConfig, identity);
        var independentBefore = storedAstState(verification.driver, sessionConfig, independentIdentity);
        assertEquals(expectedAstState(codec.map(previous)), prior);
        assertNotEquals(prior, expectedReplacement);

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          var retention = new AstRetentionService(operation -> new Neo4jAstResultRepository(publication.driver, sessionConfig, operation));
          proxy.arm();

          // Act
          var outcome = publication.call(() -> retention.retainAst(replacement));

          // Assert
          assertEquals(PublicationStatus.FAILED, outcome.status());
          assertEquals(CommitKnowledge.UNKNOWN, outcome.knowledge());
          assertEquals(Optional.of(new Ast(identity)), outcome.subjectIdentity());
          var failure = outcome.failure().orElseThrow().carrier();
          assertEquals(context, failure.context());
          assertEquals(RepositoryPhase.REPLACEMENT, failure.phase());
          assertEquals(CommitKnowledge.UNKNOWN, failure.knowledge());
          assertInstanceOf(Neo4jException.class, failure.effectivePrimary().getCause());
          proxy.requireHealthyRelay();
          var observation = proxy.observation();
          System.out.println("AST_BOLT_COMMIT_ACKNOWLEDGEMENT_OBSERVATION=" + observation);
          assertNotEquals("not negotiated", observation.negotiatedVersion());
          assertEquals(1, observation.beginMessages());
          assertEquals(1, observation.commitMessages());
          assertEquals(1, observation.withheldCommitSuccesses());
          assertEquals(0, observation.forwardedCommitResponses());
          assertEquals(expectedReplacement, storedAstState(verification.driver, sessionConfig, identity));
          assertEquals(independentBefore, storedAstState(verification.driver, sessionConfig, independentIdentity));
        }
        try (var querying = new DriverWork(directUri, settings)) {
          var queryContext = new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, identity);
          var lookup = querying.call(() -> {
            try (var repository = new Neo4jAstResultRepository(querying.driver, sessionConfig, queryContext)) {
              return repository.find(identity);
            }
          });
          assertEquals(AstLookupStatus.FOUND, lookup.status());
          assertEquals(replacement.result().orElseThrow().snapshot(), lookup.outcome().orElseThrow().result().orElseThrow().snapshot());
        }
      } finally {
        cleanupAst(verification.driver, sessionConfig, List.of(identity, independentIdentity));
      }
    }
  }

  private static AstOutcome astFixture(String typeName, Path root) throws IOException {
    var source = SourceFile.fromContent(Path.of(typeName + ".java"), "interface " + typeName + " {}");
    var input = InputSnapshot.fromSources(root, List.of(source));
    return new JavaFrontend(new JavaSourceLoader()).produce(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);
  }

  @Test
  void givenAstReplacementRejectedBeforeCommit_whenRetentionFails_thenPriorAndIndependentSubjectsRemainIntact() throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var target = loopbackTarget(settings.uri());
    var directUri = directUri(target);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var typeName = "Retained" + UUID.randomUUID().toString().replace("-", "");
    var previous = astFixture(typeName, sourceRoot.resolve("previous"));
    var replacement = astFixture(typeName, sourceRoot.resolve("replacement"));
    var independent = astFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), sourceRoot.resolve("independent"));
    var identity = replacement.result().orElseThrow().identity();
    var independentIdentity = independent.result().orElseThrow().identity();
    var context = new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, identity);
    var replacementCallbacks = new AtomicInteger();
    var codec = new Neo4jAstResultCodec();
    assertEquals(previous.result().orElseThrow().identity(), identity);

    try (var verification = new DriverWork(directUri, settings)) {
      await(verification.driver.verifyConnectivityAsync());
      try {
        for (var outcome : List.of(previous, independent)) {
          try (var seeding = new DriverWork(directUri, settings)) {
            seeding.call(() -> {
              var saveContext = new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, outcome.result().orElseThrow().identity());
              try (var repository = new Neo4jAstResultRepository(seeding.driver, sessionConfig, saveContext)) {
                repository.saveComplete(outcome);
              }
              return null;
            });
          }
        }
        var prior = storedAstState(verification.driver, sessionConfig, identity);
        var independentBefore = storedAstState(verification.driver, sessionConfig, independentIdentity);
        assertEquals(expectedAstState(codec.map(previous)), prior);
        assertNotEquals(prior, expectedAstState(codec.map(replacement)));

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          var failingDriver = rejectAfterReplacement(publication.driver, replacementCallbacks);
          var retention = new AstRetentionService(operation -> new Neo4jAstResultRepository(failingDriver, sessionConfig, operation));

          // Act
          var outcome = publication.call(() -> retention.retainAst(replacement));

          // Assert
          assertEquals(PublicationStatus.FAILED, outcome.status());
          assertEquals(CommitKnowledge.UNKNOWN, outcome.knowledge());
          assertEquals(Optional.of(new Ast(identity)), outcome.subjectIdentity());
          var failure = outcome.failure().orElseThrow().carrier();
          assertEquals(context, failure.context());
          assertEquals(RepositoryPhase.REPLACEMENT, failure.phase());
          var serverFailure = assertInstanceOf(Neo4jException.class, failure.effectivePrimary().getCause());
          assertEquals("Neo.ClientError.Statement.ArithmeticError", serverFailure.code());
          assertEquals(1, replacementCallbacks.get());
          proxy.requireHealthyRelay();
          var observation = proxy.observation();
          System.out.println("AST_REJECTED_COMMIT_OBSERVATION=" + observation);
          assertNotEquals("not negotiated", observation.negotiatedVersion());
          assertEquals(1, observation.beginMessages());
          assertEquals(0, observation.commitMessages());
          assertEquals(0, observation.withheldCommitSuccesses());
          assertEquals(0, observation.forwardedCommitResponses());
          assertEquals(prior, storedAstState(verification.driver, sessionConfig, identity));
          assertEquals(independentBefore, storedAstState(verification.driver, sessionConfig, independentIdentity));
        }
      } finally {
        cleanupAst(verification.driver, sessionConfig, List.of(identity, independentIdentity));
      }
    }
  }

  private static Driver rejectAfterReplacement(Driver driver, AtomicInteger callbacks) {
    return Driver.class.cast(Proxy.newProxyInstance(Driver.class.getClassLoader(), new Class<?>[]{Driver.class},
        (ignored, method, arguments) -> {
          var value = invokeDelegate(method, driver, arguments);
          if (!(value instanceof Session session)) {
            return value;
          }
          return Session.class.cast(Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
              (sessionProxy, sessionMethod, sessionArguments) -> {
                if (sessionMethod.getName().equals("executeWrite") && sessionArguments != null
                    && sessionArguments.length > 0 && sessionArguments[0] instanceof TransactionCallback<?> callback) {
                  return session.executeWrite(transaction -> {
                    var result = callback.execute(transaction);
                    callbacks.incrementAndGet();
                    transaction.run("RETURN 1 / 0").consume();
                    return result;
                  });
                }
                return invokeDelegate(sessionMethod, session, sessionArguments);
              }));
        }));
  }

  private static @Nullable Object invokeDelegate(Method method, Object delegate, @Nullable Object @Nullable [] arguments) throws Throwable {
    try {
      return method.invoke(delegate, arguments);
    } catch (InvocationTargetException exception) {
      throw Objects.requireNonNull(exception.getCause(), "invoked cause");
    }
  }

  private static StoredState expectedAstState(Neo4jAstProjection projection) {
    var nodes = projection.nodes().stream().collect(Collectors.toMap(Neo4jNode::key,
        node -> new StoredNode(Set.of("AstEntity", node.label()), Values.value(node.properties()).asMap())));
    var relationships = projection.relationships().stream().collect(Collectors.toMap(Neo4jRelationship::key,
        relation -> new StoredRelationship(relation.type(), relation.sourceKey(), relation.targetKey(), Values.value(relation.properties()).asMap())));
    return new StoredState(nodes, relationships);
  }

  private static StoredState storedAstState(Driver driver, SessionConfig configuration, AstIdentity identity) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      var parameters = Values.parameters("scope", identity.astResultKey());
      var nodes = await(session.value.runAsync("MATCH (n:AstEntity {astResultKey: $scope}) RETURN n.key AS key, labels(n) AS labels, "
          + "properties(n) AS properties", parameters).thenCompose(cursor -> cursor.listAsync())).stream()
          .collect(Collectors.toMap(record -> record.get("key").asString(), Neo4jCommitAcknowledgementNeo4jIT::storedNode));
      var relationships = await(session.value.runAsync("MATCH (source:AstEntity)-[relation]->(target:AstEntity) "
          + "WHERE relation.astResultKey = $scope OR source.astResultKey = $scope OR target.astResultKey = $scope "
          + "RETURN relation.key AS key, source.key AS sourceKey, target.key AS targetKey, type(relation) AS type, properties(relation) AS properties",
          parameters).thenCompose(cursor -> cursor.listAsync())).stream().collect(Collectors.toMap(record -> record.get("key").asString(),
              record -> new StoredRelationship(record.get("type").asString(), record.get("sourceKey").asString(),
                  record.get("targetKey").asString(), record.get("properties").asMap())));
      return new StoredState(nodes, relationships);
    }
  }

  private static void cleanupAst(Driver driver, SessionConfig configuration, List<AstIdentity> identities) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      await(session.value.runAsync("MATCH (entity:AstEntity) WHERE entity.astResultKey IN $scopes "
          + "CALL (entity) { DETACH DELETE entity } IN TRANSACTIONS OF 1000 ROWS",
          Values.parameters("scopes", identities.stream().map(AstIdentity::astResultKey).toList())).thenCompose(cursor -> cursor.consumeAsync()));
    }
  }

  private AnalysisRun completedFixture(String typeName, String warningCode) {
    var source = "class " + typeName + " { int value(int input) { return input; } }";
    return withWarning(RepositoryTestRuns.analyze(sourceRoot, typeName + ".java", source), warningCode);
  }

  private static AnalysisRun withWarning(AnalysisRun run, String suffix) {
    var origin = run.representation().ast().result().orElseThrow().program().types().getFirst().origins().getFirst();
    var warning = new AnalysisDiagnostic(StableId.sha256("diagnostic", run.identity().key(), suffix), "publication.acknowledgement." + suffix,
        DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "Supplied publication warning: " + suffix, List.of(origin),
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return AnalysisRun.create(run.identity(), run.representation(), List.of(warning));
  }

  private static InetSocketAddress loopbackTarget(URI uri) throws IOException {
    if ((!"bolt".equals(uri.getScheme()) && !"neo4j".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("The acknowledgement test requires configured unencrypted loopback Bolt");
    }
    var addresses = InetAddress.getAllByName(uri.getHost());
    for (var address : addresses) {
      if (!address.isLoopbackAddress()) {
        throw new IllegalArgumentException("The acknowledgement test cannot target a nonloopback backend");
      }
    }
    if (addresses.length == 0) {
      throw new IOException("The configured local Bolt host has no resolved loopback address");
    }
    var port = uri.getPort() < 0 ? 7687 : uri.getPort();
    var address = Arrays.stream(addresses).filter(Inet4Address.class::isInstance).findFirst().orElse(addresses[0]);
    return new InetSocketAddress(address, port);
  }

  private static URI directUri(InetSocketAddress target) throws URISyntaxException {
    return new URI("bolt", null, target.getAddress().getHostAddress(), target.getPort(), null, null, null);
  }

  private static Config observationConfig() {
    return Config.builder().withoutEncryption().withMaxTransactionRetryTime(0, TimeUnit.MILLISECONDS).withMaxConnectionPoolSize(1)
        .withConnectionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS).withConnectionAcquisitionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS)
        .withEventLoopThreads(2).withTelemetryDisabled(true).build();
  }

  private static StoredState expectedState(Neo4jGraphProjection projection) {
    var nodes = projection.nodes().stream().collect(Collectors.toMap(Neo4jNode::key,
        node -> new StoredNode(Set.of("AnalysisEntity", node.label()), Values.value(node.properties()).asMap())));
    var relationships = projection.relationships().stream().collect(Collectors.toMap(Neo4jRelationship::key,
        relation -> new StoredRelationship(relation.type(), relation.sourceKey(), relation.targetKey(), Values.value(relation.properties()).asMap())));
    return new StoredState(nodes, relationships);
  }

  private static StoredState storedState(Driver driver, SessionConfig configuration, String runId) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      var parameters = Values.parameters("runId", runId);
      var nodes = await(session.value.runAsync("MATCH (n:AnalysisEntity {runId: $runId}) RETURN n.key AS key, labels(n) AS labels, "
          + "properties(n) AS properties", parameters).thenCompose(cursor -> cursor.listAsync())).stream()
          .collect(Collectors.toMap(record -> record.get("key").asString(), Neo4jCommitAcknowledgementNeo4jIT::storedNode));
      var relationships = await(session.value.runAsync("MATCH (source:AnalysisEntity)-[relation]->(target:AnalysisEntity) "
          + "WHERE relation.runId = $runId OR source.runId = $runId OR target.runId = $runId "
          + "RETURN relation.key AS key, source.key AS sourceKey, target.key AS targetKey, type(relation) AS type, properties(relation) AS properties",
          parameters).thenCompose(cursor -> cursor.listAsync())).stream().collect(Collectors.toMap(record -> record.get("key").asString(),
              record -> new StoredRelationship(record.get("type").asString(), record.get("sourceKey").asString(),
                  record.get("targetKey").asString(), record.get("properties").asMap())));
      return new StoredState(nodes, relationships);
    }
  }

  private static StoredNode storedNode(Record record) {
    return new StoredNode(new HashSet<>(record.get("labels").asList(value -> value.asString())), record.get("properties").asMap());
  }

  private static void cleanup(Driver driver, SessionConfig configuration, List<String> runIds) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      await(session.value.runAsync("MATCH (entity:AnalysisEntity) WHERE entity.runId IN $runIds DETACH DELETE entity",
          Values.parameters("runIds", runIds)).thenCompose(cursor -> cursor.consumeAsync()));
    }
  }

  private static <T> T await(CompletionStage<T> stage) throws IOException {
    try {
      return stage.toCompletableFuture().get(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while awaiting an owned Neo4j operation", exception);
    } catch (ExecutionException exception) {
      throw new IOException("An owned Neo4j operation failed", exception);
    } catch (TimeoutException exception) {
      throw new IOException("An owned Neo4j operation did not return within the observation bound", exception);
    }
  }

  private static final class DriverWork implements AutoCloseable {
    private final Driver driver;
    private final Runnable disconnect;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
      var thread = new Thread(action, "neo4j-publication-observation");
      thread.setDaemon(true);
      return thread;
    });

    private DriverWork(URI uri, Neo4jConnectionSettings settings) {
      this(uri, settings, () -> { });
    }

    private DriverWork(URI uri, Neo4jConnectionSettings settings, Runnable disconnect) {
      this.disconnect = disconnect;
      driver = GraphDatabase.driver(uri, AuthTokens.basic(settings.username(), settings.password()), observationConfig());
    }

    private <T> T call(Callable<T> action) throws ExecutionException, IOException {
      try {
        return worker.submit(action).get(PUBLICATION_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while awaiting the owned repository call", exception);
      } catch (TimeoutException exception) {
        throw new IOException("The owned repository call exceeded its observation bound", exception);
      }
    }

    @Override
    public void close() throws IOException {
      disconnect.run();
      try (var _ = new WorkerShutdown(worker)) {
        await(driver.closeAsync());
      }
    }
  }

  private static final class WorkerShutdown implements AutoCloseable {
    private final ExecutorService worker;

    private WorkerShutdown(ExecutorService worker) {
      this.worker = worker;
    }

    @Override
    public void close() throws IOException {
      worker.shutdownNow();
      try {
        if (!worker.awaitTermination(WORKER_STOP_SECONDS, TimeUnit.SECONDS)) {
          throw new IOException("The owned repository worker did not terminate within the bound");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while awaiting owned repository worker termination", exception);
      }
    }
  }

  private static final class SessionOwner implements AutoCloseable {
    private final AsyncSession value;

    private SessionOwner(AsyncSession value) {
      this.value = value;
    }

    @Override
    public void close() throws IOException {
      await(value.closeAsync());
    }
  }

  private record StoredState(Map<String, StoredNode> nodes, Map<String, StoredRelationship> relationships) {
  }

  private record StoredNode(Set<String> labels, Map<String, Object> properties) {
  }

  private record StoredRelationship(String type, String sourceKey, String targetKey, Map<String, Object> properties) {
  }
}
