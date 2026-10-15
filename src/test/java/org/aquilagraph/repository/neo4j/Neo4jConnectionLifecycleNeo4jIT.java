package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.async.AsyncSession;

@ExtendWith(Neo4jIntegration.class)
class Neo4jConnectionLifecycleNeo4jIT {
  private static final int DRIVER_WAIT_SECONDS = 10;
  private static final int CONNECTION_TIMEOUT_SECONDS = 5;
  private static final int RELEASE_WAIT_SECONDS = 10;
  private static final int RELEASE_POLL_MILLIS = 50;

  @Test
  void givenSingleConnectionPoolAndOwnedAgent_whenSequentialSessionsAndDriverClose_thenCapacityReturnsAndOwnedConnectionsDisappear()
      throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var uri = directLoopbackUri(settings.uri());
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var ownership = UUID.randomUUID().toString();
    var subjectAgent = "AquilaGraphConnectionLifecycle/" + ownership;
    var observerAgent = "AquilaGraphConnectionObserver/" + ownership;
    try (var observer = new DriverOwner(uri, settings, observerAgent)) {
      await(observer.driver.verifyConnectivityAsync());
      List<Integer> firstValues;
      List<Integer> secondValues;
      Set<String> firstConnections;
      Set<String> secondConnections;

      // Act
      try (var subject = new DriverOwner(uri, settings, subjectAgent)) {
        firstValues = values(subject.driver, sessionConfig, 17);
        firstConnections = connectionIds(observer.driver, sessionConfig, subjectAgent);
        secondValues = values(subject.driver, sessionConfig, 23);
        secondConnections = connectionIds(observer.driver, sessionConfig, subjectAgent);
      }
      var observedConnections = new HashSet<>(firstConnections);
      observedConnections.addAll(secondConnections);
      var remaining = awaitConnectionRemoval(observer.driver, sessionConfig, subjectAgent, Set.copyOf(observedConnections));

      // Assert
      assertEquals(List.of(17), firstValues);
      assertEquals(List.of(23), secondValues);
      assertFalse(firstConnections.isEmpty(), "The observer must see the first owned Bolt connection before driver close");
      assertFalse(secondConnections.isEmpty(), "The observer must see the second session's owned Bolt connection before driver close");
      assertEquals(1, firstConnections.size(), "The direct driver's configured pool permits one connection");
      assertEquals(1, secondConnections.size(), "The second consumed and closed session reacquires capacity in the same bounded pool");
      assertEquals(0L, remaining, "The independent observer must see neither an observed owned ID nor the exact owned user agent");
      System.out.println("NEO4J_CONNECTION_LIFECYCLE_OBSERVATION=firstOwnedConnections:" + firstConnections.size()
          + ",secondOwnedConnections:" + secondConnections.size() + ",sameConnection:" + firstConnections.equals(secondConnections)
          + ",remainingOwnedConnections:" + remaining + ",driverCloseCompleted:true");
    }
  }

  private static List<Integer> values(Driver driver, SessionConfig sessionConfig, int value) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, sessionConfig))) {
      var results = session.value.runAsync("RETURN $value AS value", Map.of("value", value))
          .thenCompose(cursor -> cursor.listAsync(record -> record.get("value").asInt())
              .thenCompose(records -> cursor.consumeAsync().thenApply(summary -> records)));
      return await(results);
    }
  }

  private static Set<String> connectionIds(Driver driver, SessionConfig sessionConfig, String userAgent) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, sessionConfig))) {
      var connections = session.value
          .runAsync("CALL dbms.listConnections() YIELD connectionId, connector, userAgent "
              + "WHERE connector = 'bolt' AND userAgent = $userAgent RETURN connectionId", Map.of("userAgent", userAgent))
          .thenCompose(cursor -> cursor.listAsync(record -> record.get("connectionId").asString())
              .thenCompose(records -> cursor.consumeAsync().thenApply(summary -> records)));
      return await(connections).stream().collect(Collectors.toUnmodifiableSet());
    }
  }

  private static long remainingConnectionCount(Driver observer, SessionConfig sessionConfig, String userAgent,
      Set<String> observedConnections, long waitNanos) throws IOException {
    try (var session = new SessionOwner(observer.session(AsyncSession.class, sessionConfig))) {
      var remaining = session.value
          .runAsync("CALL dbms.listConnections() YIELD connectionId, connector, userAgent "
              + "WHERE connector = 'bolt' AND (userAgent = $userAgent OR connectionId IN $connectionIds) "
              + "RETURN count(connectionId) AS remaining", Map.of("userAgent", userAgent, "connectionIds", List.copyOf(observedConnections)))
          .thenCompose(cursor -> cursor.singleAsync()
              .thenCompose(record -> cursor.consumeAsync().thenApply(summary -> record.get("remaining").asLong())));
      return await(remaining, waitNanos);
    }
  }

  private static long awaitConnectionRemoval(Driver observer, SessionConfig sessionConfig, String userAgent,
      Set<String> observedConnections) throws IOException {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RELEASE_WAIT_SECONDS);
    long remaining;
    do {
      var waitNanos = deadline - System.nanoTime();
      if (waitNanos <= 0) {
        throw new IOException("Owned Bolt connections did not disappear within the observation bound");
      }
      remaining = remainingConnectionCount(observer, sessionConfig, userAgent, observedConnections, waitNanos);
      if (remaining == 0) {
        return remaining;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(RELEASE_POLL_MILLIS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while observing owned Bolt connection removal", exception);
      }
    } while (true);
  }

  private static <T> T await(CompletionStage<T> stage) throws IOException {
    return await(stage, TimeUnit.SECONDS.toNanos(DRIVER_WAIT_SECONDS));
  }

  private static <T> T await(CompletionStage<T> stage, long waitNanos) throws IOException {
    try {
      return stage.toCompletableFuture().get(waitNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while awaiting the public Neo4j lifecycle operation", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new IOException("The public Neo4j lifecycle operation did not complete normally within the bound", exception);
    }
  }

  private static URI directLoopbackUri(URI configured) throws IOException, URISyntaxException {
    if (!("bolt".equals(configured.getScheme()) || "neo4j".equals(configured.getScheme()))) {
      throw new IllegalArgumentException("The connection lifecycle observation requires configured unencrypted local Bolt");
    }
    var addresses = InetAddress.getAllByName(configured.getHost());
    if (Arrays.stream(addresses).anyMatch(address -> !address.isLoopbackAddress())) {
      throw new IllegalArgumentException("The connection lifecycle observation requires a loopback backend");
    }
    var address = Arrays.stream(addresses).filter(Inet4Address.class::isInstance).findFirst().orElse(addresses[0]);
    var port = configured.getPort() < 0 ? 7687 : configured.getPort();
    return new URI("bolt", null, address.getHostAddress(), port, null, null, null);
  }

  private static final class DriverOwner implements AutoCloseable {
    private final Driver driver;

    private DriverOwner(URI uri, Neo4jConnectionSettings settings, String userAgent) {
      var config = Config.builder().withoutEncryption().withUserAgent(userAgent).withMaxConnectionPoolSize(1)
          .withConnectionTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .withConnectionAcquisitionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS).withEventLoopThreads(2)
          .withMaxTransactionRetryTime(0, TimeUnit.MILLISECONDS).withAutoCommitRetriesDisabled(true).withTelemetryDisabled(true).build();
      driver = GraphDatabase.driver(uri, AuthTokens.basic(settings.username(), settings.password()), config);
    }

    @Override
    public void close() throws IOException {
      await(driver.closeAsync());
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
}
