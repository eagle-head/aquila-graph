package org.aquilagraph.repository.internal.neo4j;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.repository.internal.neo4j.StoredPublication.Namespace;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.async.AsyncSession;

/** Owns bounded live publication work and independent stored-state observations. */
final class Neo4jPublicationObservation {
  private static final int DRIVER_WAIT_SECONDS = 10;
  private static final int PUBLICATION_WAIT_SECONDS = 30;
  private static final int WORKER_STOP_SECONDS = 5;

  private Neo4jPublicationObservation() {}

  static InetSocketAddress loopbackTarget(URI uri) throws IOException {
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

  static URI directUri(InetSocketAddress target) throws URISyntaxException {
    return new URI("bolt", null, target.getAddress().getHostAddress(), target.getPort(), null, null, null);
  }

  static Config observationConfig() {
    return Config.builder().withoutEncryption().withMaxTransactionRetryTime(0, TimeUnit.MILLISECONDS).withMaxConnectionPoolSize(1)
        .withConnectionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS).withConnectionAcquisitionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS)
        .withEventLoopThreads(2).withTelemetryDisabled(true).build();
  }

  static StoredPublication storedState(Driver driver, SessionConfig configuration, String runId) throws IOException {
    return storedState(driver, configuration, Namespace.RUN, runId);
  }

  static StoredPublication storedState(Driver driver, SessionConfig configuration, Namespace namespace, String scope) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      var parameters = Values.parameters("scope", scope);
      var nodes = await(session.value.runAsync(namespace.nodesQuery(), parameters).thenCompose(cursor -> cursor.listAsync()));
      var relationships = await(session.value.runAsync(namespace.relationshipsQuery(), parameters).thenCompose(cursor -> cursor.listAsync()));
      return StoredPublication.fromRecords(nodes, relationships);
    }
  }

  static void cleanup(Driver driver, SessionConfig configuration, List<String> runIds) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      await(session.value.runAsync("MATCH (entity:AnalysisEntity) WHERE entity.runId IN $runIds DETACH DELETE entity",
          Values.parameters("runIds", runIds)).thenCompose(cursor -> cursor.consumeAsync()));
    }
  }

  static <T> T await(CompletionStage<T> stage) throws IOException {
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

  static final class DriverWork implements AutoCloseable {
    final Driver driver;
    private final Runnable disconnect;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
      var thread = new Thread(action, "neo4j-publication-observation");
      thread.setDaemon(true);
      return thread;
    });

    DriverWork(URI uri, Neo4jConnectionSettings settings) {
      this(uri, settings, () -> { });
    }

    DriverWork(URI uri, Neo4jConnectionSettings settings, Runnable disconnect) {
      this.disconnect = disconnect;
      driver = GraphDatabase.driver(uri, AuthTokens.basic(settings.username(), settings.password()), observationConfig());
    }

    <T> T call(Callable<T> action) throws ExecutionException, IOException {
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

  static final class SessionOwner implements AutoCloseable {
    final AsyncSession value;

    SessionOwner(AsyncSession value) {
      this.value = value;
    }

    @Override
    public void close() throws IOException {
      await(value.closeAsync());
    }
  }

  static AnalysisRun completedRun(Path sourceRoot, String typeName) throws IOException {
    var source = """
        class %s {
          int field;
          int evaluate(int input, int divisor) {
            return switch (input) {
              case 0 -> field;
              default -> input / divisor;
            };
          }
        }
        """.formatted(typeName);
    Files.writeString(sourceRoot.resolve(typeName + ".java"), source);
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    return new SourceAnalysisFixture().analyze(new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow());
  }

  static StoredPublication expectedProjection(Neo4jGraphProjection projection) {
    return Namespace.RUN.expected(projection.runId(), projection.nodes(), projection.relationships());
  }

  static StoredPublication storedProjection(Session session, String runId) {
    var parameters = Values.parameters("scope", runId);
    return StoredPublication.fromRecords(session.run(Namespace.RUN.nodesQuery(), parameters).list(),
        session.run(Namespace.RUN.relationshipsQuery(), parameters).list());
  }

  static void cleanup(Neo4jConnectionSettings settings, List<String> runIds) {
    try (var driver =
            GraphDatabase.driver(
                settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      session
          .run(
              "MATCH (entity:AnalysisEntity) WHERE entity.runId IN $runIds DETACH DELETE entity",
              Values.parameters("runIds", runIds))
          .consume();
    }
  }

  static AnalysisRun withPublicationWarning(AnalysisRun run, String state) {
    var warning = new AnalysisDiagnostic("diagnostic:publication-" + state, "publication." + state, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "Publication warning: " + state, List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return AnalysisRun.create(run.identity(), run.representation(), List.of(warning));
  }

}
