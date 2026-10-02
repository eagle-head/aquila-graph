package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
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
import org.aquilagraph.application.port.RepositoryException;
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

class Neo4jMetadataInspectionNeo4jIT {
  private static final int DRIVER_WAIT_SECONDS = 10;
  private static final int SAVE_WAIT_SECONDS = 30;
  private static final int WORKER_STOP_SECONDS = 5;

  @TempDir Path sourceRoot;

  @Test
  void givenBothConstraintResultsCompleted_whenMetadataTransportFails_thenNoPublicationBeginsAndCompletePriorRunsRemain()
      throws Exception {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var target = loopbackTarget(settings.uri());
    var directUri = new URI("bolt", null, target.getAddress().getHostAddress(), target.getPort(), null, null, null);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var primary = completedFixture("Metadata" + UUID.randomUUID().toString().replace("-", ""), "previous");
    var independent = completedFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), "independent");
    var replacement = withWarning(primary, "replacement");
    var mapper = new Neo4jAnalysisRunMapper();
    var runIds = List.of(primary.runId(), independent.runId());

    try (var observer = new DriverWork(directUri, settings)) {
      await(observer.driver.verifyConnectivityAsync());
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
        var prior = storedState(observer.driver, sessionConfig, primary.runId());
        var independentBefore = storedState(observer.driver, sessionConfig, independent.runId());
        assertEquals(expectedState(mapper.map(primary)), prior);
        assertEquals(expectedState(mapper.map(independent)), independentBefore);
        assertNotEquals(prior, expectedState(mapper.map(replacement)));

        try (var proxy = new BoltMetadataInspectionFailureProxy(target);
            var failing = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          // Act
          var execution = assertThrows(ExecutionException.class, () -> failing.call(() -> {
            try (var repository = new Neo4jAnalysisRunRepository(failing.driver, sessionConfig, mapper::map)) {
              repository.save(replacement);
            }
            return null;
          }));

          // Assert
          var failure = assertInstanceOf(RepositoryException.class, execution.getCause());
          assertInstanceOf(Neo4jException.class, failure.getCause());
          proxy.requireHealthyRelay();
          var observation = proxy.observation();
          System.out.println("BOLT_METADATA_INSPECTION_OBSERVATION=" + observation);
          assertNotEquals("not negotiated", observation.negotiatedVersion());
          assertEquals(1, observation.entityConstraintCompletions());
          assertEquals(1, observation.runConstraintCompletions());
          assertEquals(1, observation.inspectionRequests());
          assertEquals(0, observation.forwardedInspections());
          assertEquals(0, observation.beginMessages());
          assertEquals(prior, storedState(observer.driver, sessionConfig, primary.runId()));
          assertEquals(independentBefore, storedState(observer.driver, sessionConfig, independent.runId()));
        }
      } finally {
        cleanup(observer.driver, sessionConfig, runIds);
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
    return withWarning(new AnalysisRun(runId, snapshot, environment, Optional.of(representation), List.of(), AnalysisCompletion.COMPLETED),
        warningCode);
  }

  private static AnalysisRun withWarning(AnalysisRun run, String suffix) {
    var origin = run.representation().orElseThrow().program().types().getFirst().origins().getFirst();
    var warning = new AnalysisDiagnostic(StableId.sha256("diagnostic", run.runId(), suffix), "publication.metadata." + suffix,
        DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "Supplied metadata-observation warning: " + suffix, List.of(origin),
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return new AnalysisRun(run.runId(), run.inputSnapshot(), run.environmentSnapshot(), run.representation(), List.of(warning),
        AnalysisCompletion.COMPLETED);
  }

  private static InetSocketAddress loopbackTarget(URI uri) throws IOException {
    if ((!"bolt".equals(uri.getScheme()) && !"neo4j".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("The metadata observation requires configured unencrypted loopback Bolt");
    }
    var addresses = InetAddress.getAllByName(uri.getHost());
    if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !address.isLoopbackAddress())) {
      throw new IOException("The configured metadata backend does not resolve only to loopback addresses");
    }
    var address = Arrays.stream(addresses).filter(Inet4Address.class::isInstance).findFirst().orElse(addresses[0]);
    return new InetSocketAddress(address, uri.getPort() < 0 ? 7687 : uri.getPort());
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
          .collect(Collectors.toMap(record -> record.get("key").asString(), Neo4jMetadataInspectionNeo4jIT::storedNode));
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
      throw new IOException("Interrupted while awaiting an owned metadata observation operation", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new IOException("An owned metadata observation operation did not return normally within its bound", exception);
    }
  }

  private static final class DriverWork implements AutoCloseable {
    private final Driver driver;
    private final Runnable disconnect;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
      var thread = new Thread(action, "neo4j-metadata-observation");
      thread.setDaemon(true);
      return thread;
    });

    private DriverWork(URI uri, Neo4jConnectionSettings settings) {
      this(uri, settings, () -> { });
    }

    private DriverWork(URI uri, Neo4jConnectionSettings settings, Runnable disconnect) {
      this.disconnect = disconnect;
      var config = Config.builder().withoutEncryption().withMaxTransactionRetryTime(0, TimeUnit.MILLISECONDS).withMaxConnectionPoolSize(1)
          .withConnectionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS).withConnectionAcquisitionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS)
          .withEventLoopThreads(2).withTelemetryDisabled(true).withAutoCommitRetriesDisabled(true).build();
      driver = GraphDatabase.driver(uri, AuthTokens.basic(settings.username(), settings.password()), config);
    }

    private <T> T call(Callable<T> action) throws ExecutionException, IOException {
      try {
        return worker.submit(action).get(SAVE_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while awaiting the owned metadata repository call", exception);
      } catch (TimeoutException exception) {
        throw new IOException("The owned metadata repository call exceeded its observation bound", exception);
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
          throw new IOException("The owned metadata repository worker did not terminate within the bound");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while awaiting owned metadata repository worker termination", exception);
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
