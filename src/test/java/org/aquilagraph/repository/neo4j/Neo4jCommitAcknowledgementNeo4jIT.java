package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
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
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.RepositorySavePolicy;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CfgEdge;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.OperationOperand;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.SoftwareVersion;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jCommitAcknowledgementNeo4jIT {
  private static final int DRIVER_WAIT_SECONDS = 10;
  private static final int PUBLICATION_WAIT_SECONDS = 30;
  private static final int WORKER_STOP_SECONDS = 5;

  @TempDir Path sourceRoot;

  @Test
  void givenCommitResponseWithheldOnLoopbackBolt_whenPublicationFails_thenDirectQueriesObserveWholeReplacementAndIndependentRun()
      throws Exception {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var target = loopbackTarget(settings.uri());
    var directUri = directUri(target);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var primary = completedFixture("Acknowledgement" + UUID.randomUUID().toString().replace("-", ""), "previous");
    var independent = completedFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), "independent");
    var replacement = withWarning(primary, "replacement");
    var mapper = new Neo4jAnalysisRunMapper();
    var expectedReplacement = expectedState(mapper.map(replacement));
    var runIds = List.of(primary.runId(), independent.runId());

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
        var prior = storedState(verification.driver, sessionConfig, primary.runId());
        var independentBefore = storedState(verification.driver, sessionConfig, independent.runId());
        assertEquals(expectedState(mapper.map(primary)), prior);
        assertEquals(expectedState(mapper.map(independent)), independentBefore);
        assertNotEquals(prior, expectedReplacement);

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          var service = new AnalysisApplicationService((root, configuration) -> replacement,
              () -> new Neo4jAnalysisRunRepository(publication.driver, sessionConfig, mapper::map));
          var request = new AnalysisRequest(sourceRoot, replacement.compilerConfiguration(), RepositorySavePolicy.SAVE_COMPLETED);
          proxy.arm();

          // Act
          var executionFailure = assertThrows(ExecutionException.class, () -> publication.call(() -> service.execute(request)));

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
          var observedReplacement = storedState(verification.driver, sessionConfig, primary.runId());
          assertEquals(expectedReplacement, observedReplacement);
          assertNotEquals(prior, observedReplacement);
          assertEquals(independentBefore, storedState(verification.driver, sessionConfig, independent.runId()));
        }
      } finally {
        cleanup(verification.driver, sessionConfig, runIds);
      }
    }
  }

  private AnalysisRun completedFixture(String typeName, String warningCode) {
    var source = "class " + typeName + " { int value(int input) { return input; } }";
    var file = SourceFile.fromContent(Path.of(typeName + ".java"), source);
    var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(file));
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var environment = new AnalysisEnvironmentSnapshot(configuration, "supplied-compiler", "supplied-vendor", "25-supplied",
        List.of("--release", "25"), List.of(), List.of());
    var typeKey = StableId.sha256("type", typeName);
    var methodKey = StableId.sha256("method", typeKey, "value(int)");
    var intType = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
    var wholeSource = new SourceSpan(file.sourceFileKey(), 0, source.length());
    var parameter = new JavaParameter(StableId.sha256("parameter", methodKey, "input"), "input", intType, true, List.of(wholeSource));
    var method = new JavaMethod(methodKey, typeKey, "value", "value(int)", "int", List.of(parameter), Set.of(), false, true, true,
        List.of(wholeSource));
    var type = new JavaType(typeKey, typeName, JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        List.of(), true, List.of(), List.of(), List.of(method), List.of(), List.of(wholeSource));
    var graphId = StableId.sha256("cfg", methodKey);
    var entry = new EntryNode(StableId.sha256("cfg-node", graphId, "entry"), graphId);
    var exit = new ExitNode(StableId.sha256("cfg-node", graphId, "exit"), graphId);
    var input = new FlowValue(StableId.sha256("cfg-value", parameter.parameterKey()), graphId, ValueKind.PARAMETER, Optional.of("input"),
        Optional.empty(), Optional.of(intType));
    var returning = new OperationNode(StableId.sha256("cfg-node", graphId, "return"), graphId, OperationKind.RETURN, "return",
        List.of(new OperationOperand(0, input)), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(wholeSource));
    var graph = new ControlFlowGraph(graphId, methodKey, entry, List.of(returning), List.of(input), exit, List.of(
        new CfgEdge(StableId.sha256("cfg-edge", graphId, "entry-return"), graphId, entry.nodeId(), returning.nodeId(), TransferKind.NORMAL,
            Optional.empty()),
        new CfgEdge(StableId.sha256("cfg-edge", graphId, "return-exit"), graphId, returning.nodeId(), exit.nodeId(), TransferKind.RETURN,
            Optional.empty())));
    var representation = new ProgramRepresentation(new JavaProgram(List.of(type)),
        List.of(new MethodAnalysis(StableId.sha256("method-analysis", methodKey), method, graph)));
    var runId = StableId.sha256("analysis-run", SoftwareVersion.from(snapshot, environment).signature());
    var original = new AnalysisRun(runId, snapshot, environment, Optional.of(representation), List.of(), AnalysisCompletion.COMPLETED);
    return withWarning(original, warningCode);
  }

  private static AnalysisRun withWarning(AnalysisRun run, String suffix) {
    var origin = run.representation().orElseThrow().program().types().getFirst().origins().getFirst();
    var warning = new AnalysisDiagnostic(StableId.sha256("diagnostic", run.runId(), suffix), "publication.acknowledgement." + suffix,
        DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "Supplied publication warning: " + suffix, List.of(origin),
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return new AnalysisRun(run.runId(), run.inputSnapshot(), run.environmentSnapshot(), run.representation(), List.of(warning),
        AnalysisCompletion.COMPLETED);
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
      try (var shutdown = new WorkerShutdown(worker)) {
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
