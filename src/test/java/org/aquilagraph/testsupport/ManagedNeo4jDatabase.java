package org.aquilagraph.testsupport;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.TestcontainersConfiguration;

/** Owns an isolated database, its bounded resources, and verified disposal. */
public final class ManagedNeo4jDatabase implements AutoCloseable {
  public static final String RESOURCE_LABEL = "org.aquilagraph.resource";
  public static final String DATABASE_RESOURCE = "integration-database";
  private final GenericContainer<?> container;
  private final DockerClient docker;
  private final Path output;
  private final Properties metadata = new Properties();
  private final Object logLock = new Object();
  private final AtomicReference<IOException> logFailure = new AtomicReference<>();
  private final String password;
  private final boolean preserveData;
  private final String dataVolume;
  private final boolean existingData;
  private List<String> mountedVolumes = List.of();
  private String containerId;
  private boolean closed;

  private ManagedNeo4jDatabase(Path output, boolean preserveData, Properties preserved) throws IOException {
    if (Boolean.parseBoolean(TestcontainersConfiguration.getInstance().getEnvVarOrProperty("ryuk.disabled", "false"))) {
      throw new IllegalStateException("Database integration requires the Testcontainers resource reaper");
    }
    this.output = Files.createDirectories(output.toAbsolutePath().normalize());
    this.preserveData = preserveData;
    var configuration = configuration();
    docker = DockerClientFactory.instance().client();
    var identity = UUID.randomUUID().toString();
    existingData = !preserved.isEmpty();
    password = existingData ? preserved.getProperty("password") : UUID.randomUUID().toString();
    dataVolume = existingData ? preserved.getProperty("data.volume") : preserveData ? "aquila-graph-test-data-" + identity : "";
    if (existingData && (!preserveData || password == null || dataVolume == null
        || !"preserved-integration-data".equals(docker.inspectVolumeCmd(dataVolume).exec().getLabels().get(RESOURCE_LABEL)))) {
      throw new IllegalArgumentException("Resume requires credentials and an explicitly preserved integration volume");
    }
    var memory = Long.parseLong(configuration.getProperty("container.memory.bytes"));
    var processors = Long.parseLong(configuration.getProperty("container.cpu.count"));
    container = new GenericContainer<>(DockerImageName.parse(configuration.getProperty("image")))
        .withExposedPorts(7687)
        .withReuse(false)
        .withLabel(RESOURCE_LABEL, DATABASE_RESOURCE)
        .withEnv("NEO4J_AUTH", "neo4j/" + password)
        .withEnv("NEO4J_server_memory_heap_initial__size", configuration.getProperty("heap.initial"))
        .withEnv("NEO4J_server_memory_heap_max__size", configuration.getProperty("heap.maximum"))
        .withEnv("NEO4J_server_jvm_additional", "-XX:MaxDirectMemorySize=" + configuration.getProperty("jvm.direct.memory.maximum")
            + " -XX:TrimNativeHeapInterval=" + configuration.getProperty("jvm.native.heap.trim.interval.millis"))
        .withEnv("NEO4J_server_memory_pagecache_size", configuration.getProperty("pagecache.size"))
        .withEnv("NEO4J_dbms_memory_transaction_total_max", configuration.getProperty("transaction.memory.total"))
        .withEnv("NEO4J_db_tx__log_rotation_size", configuration.getProperty("transaction.log.rotation.size"))
        .withEnv("NEO4J_db_tx__log_rotation_retention__policy", configuration.getProperty("transaction.log.retention"))
        .waitingFor(Wait.forListeningPort())
        .withStartupTimeout(Duration.ofSeconds(Long.parseLong(configuration.getProperty("startup.timeout.seconds"))))
        .withLogConsumer(frame -> appendLog(frame.getUtf8String()))
        .withCreateContainerCmdModifier(command -> {
          command.withName("aquila-graph-integration-" + identity);
          command.getHostConfig().withMemory(memory).withMemorySwap(memory).withNanoCPUs(processors * 1_000_000_000L).withAutoRemove(true)
              .withLogConfig(new LogConfig(LogConfig.LoggingType.JSON_FILE, Map.of("max-size", "10m", "max-file", "2")));
          if (preserveData) {
            command.getHostConfig().withBinds(new Bind(dataVolume, new Volume("/data")));
          }
        });
    metadata.setProperty("preserve.data", Boolean.toString(preserveData));
    metadata.setProperty("container.memory.bytes", Long.toString(memory));
    metadata.setProperty("container.cpu.count", Long.toString(processors));
    metadata.setProperty("data.volume", dataVolume);
    metadata.setProperty("state", "starting");
  }

  public static ManagedNeo4jDatabase start(Path output, boolean preserveData) throws IOException {
    return start(new ManagedNeo4jDatabase(output, preserveData, new Properties()));
  }

  public static ManagedNeo4jDatabase resume(Path output, Path descriptor) throws IOException {
    var preserved = new Properties();
    try (var reader = Files.newBufferedReader(descriptor)) {
      preserved.load(reader);
    }
    if (preserved.isEmpty()) {
      throw new IllegalArgumentException("A preserved database descriptor must contain its volume and credentials");
    }
    return start(new ManagedNeo4jDatabase(output, true, preserved));
  }

  private static ManagedNeo4jDatabase start(ManagedNeo4jDatabase database) throws IOException {
    try {
      database.start();
      return database;
    } catch (RuntimeException | IOException failure) {
      try {
        database.close();
      } catch (RuntimeException | IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  private void start() throws IOException {
    if (preserveData) {
      if (!existingData) {
        docker.createVolumeCmd().withName(dataVolume).withLabels(Map.of(RESOURCE_LABEL, "preserved-integration-data")).exec();
      }
      var connection = new Properties();
      connection.setProperty("username", "neo4j");
      connection.setProperty("password", password);
      connection.setProperty("database", "neo4j");
      connection.setProperty("data.volume", dataVolume);
      try (var writer = Files.newBufferedWriter(output.resolve("preserved-data.properties"))) {
        connection.store(writer, "Explicitly preserved test database; keep these credentials private");
      }
    }
    container.start();
    containerId = container.getContainerId();
    metadata.setProperty("container.id", containerId);
    mountedVolumes = docker.inspectContainerCmd(containerId).exec().getMounts().stream()
        .map(mount -> mount.getName()).filter(name -> name != null && !name.isBlank()).toList();
    metadata.setProperty("mounted.volumes", String.join(",", mountedVolumes));
    metadata.setProperty("uri", settings().uri().toString());
    metadata.setProperty("state", "checking-readiness");
    saveMetadata();
    awaitQuery();
    metadata.setProperty("state", "ready");
    saveMetadata();
    System.out.println("Managed Neo4j ready: " + containerId + "; resources: " + output.resolve("resources.properties"));
    if (preserveData) {
      System.out.println("Explicit data preservation: " + dataVolume + "; credentials: " + output.resolve("preserved-data.properties"));
    }
  }

  public Neo4jConnectionSettings settings() {
    return new Neo4jConnectionSettings(URI.create("bolt://" + container.getHost() + ":" + container.getMappedPort(7687)),
        "neo4j", password, "neo4j");
  }

  public Map<String, String> environment() {
    var settings = settings();
    return Map.of("AQUILA_GRAPH_NEO4J_URI", settings.uri().toString(), "AQUILA_GRAPH_NEO4J_USERNAME", settings.username(),
        "AQUILA_GRAPH_NEO4J_PASSWORD", settings.password(), "AQUILA_GRAPH_NEO4J_DATABASE", settings.database());
  }

  private void awaitQuery() throws IOException {
    var deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
    var settings = settings();
    var configuration = Config.builder().withConnectionTimeout(5, TimeUnit.SECONDS).withMaxTransactionRetryTime(0, TimeUnit.SECONDS).build();
    Neo4jException lastFailure = null;
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), password), configuration)) {
      while (System.nanoTime() < deadline) {
        try (var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
          if (session.run("RETURN 1 AS ready").single().get("ready").asInt() != 1) {
            throw new IllegalStateException("Unexpected database readiness response");
          }
          return;
        } catch (Neo4jException failure) {
          lastFailure = failure;
          try {
            Thread.sleep(250);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Database readiness was interrupted", interrupted);
          }
        }
      }
    }
    throw new IOException("Database readiness query did not complete before its deadline", lastFailure);
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    if (containerId == null) {
      containerId = container.getContainerId();
    }
    IOException observationFailure = null;
    try {
      if (containerId != null && container.isRunning()) {
        recordMemoryUsage();
      }
    } catch (IOException failure) {
      observationFailure = failure;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      observationFailure = new IOException("Database memory observation was interrupted", interrupted);
    }
    try {
      if (containerId != null && containerExists(containerId)) {
        docker.stopContainerCmd(containerId).withTimeout(30).exec();
      }
    } catch (NotFoundException alreadyRemoved) {
      // Automatic removal may finish between inspection and shutdown.
    } finally {
      container.stop();
    }
    var deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (!disposableResourcesRemoved() && System.nanoTime() < deadline) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IOException("Database cleanup verification was interrupted", interrupted);
      }
    }
    if (!disposableResourcesRemoved()) {
      throw new IllegalStateException("Owned disposable database resources remain after shutdown: " + metadata);
    }
    if (preserveData && !volumeExists(dataVolume)) {
      throw new IllegalStateException("Explicitly preserved database volume was removed: " + dataVolume);
    }
    metadata.setProperty("state", "closed");
    saveMetadata();
    closed = true;
    if (observationFailure != null) {
      throw observationFailure;
    }
    if (logFailure.get() != null) {
      throw new IOException("Database log capture failed", logFailure.get());
    }
  }

  private void recordMemoryUsage() throws IOException, InterruptedException {
    var result = container.execInContainer("sh", "-c",
        "if [ -r /sys/fs/cgroup/memory.peak ]; then cat /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.peak; "
            + "elif [ -r /sys/fs/cgroup/memory/memory.max_usage_in_bytes ]; then "
            + "cat /sys/fs/cgroup/memory/memory.usage_in_bytes /sys/fs/cgroup/memory/memory.max_usage_in_bytes; "
            + "else printf unavailable; fi");
    if (result.getExitCode() != 0) {
      throw new IOException("Database memory observation failed: " + result.getStderr());
    }
    var values = result.getStdout().trim().split("\\s+");
    if (values.length == 2) {
      metadata.setProperty("memory.current.bytes", values[0]);
      metadata.setProperty("memory.peak.bytes", values[1]);
    } else if (result.getStdout().trim().equals("unavailable")) {
      metadata.setProperty("memory.metrics", "unavailable");
    } else {
      throw new IOException("Unexpected database memory observation: " + result.getStdout());
    }
  }

  private boolean disposableResourcesRemoved() {
    return (containerId == null || !containerExists(containerId))
        && mountedVolumes.stream().filter(volume -> !preserveData || !volume.equals(dataVolume)).noneMatch(this::volumeExists);
  }

  private boolean volumeExists(String name) {
    try {
      docker.inspectVolumeCmd(name).exec();
      return true;
    } catch (NotFoundException absent) {
      return false;
    }
  }

  private boolean containerExists(String identity) {
    try {
      docker.inspectContainerCmd(identity).exec();
      return true;
    } catch (NotFoundException absent) {
      return false;
    }
  }

  private void saveMetadata() throws IOException {
    try (var writer = Files.newBufferedWriter(output.resolve("resources.properties"))) {
      metadata.store(writer, "Managed database resources");
    }
  }

  private void appendLog(String text) {
    synchronized (logLock) {
      try {
        Files.writeString(output.resolve("neo4j.log"), text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException failure) {
        logFailure.compareAndSet(null, failure);
      }
    }
  }

  private static Properties configuration() throws IOException {
    var values = new Properties();
    try (var input = ManagedNeo4jDatabase.class.getResourceAsStream("/neo4j-integration.properties")) {
      if (input == null) {
        throw new IOException("Missing managed Neo4j integration configuration");
      }
      values.load(input);
    }
    return values;
  }
}
