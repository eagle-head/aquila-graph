package org.aquilagraph.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dockerjava.api.exception.NotFoundException;
import java.io.IOException;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;

@Tag("lifecycle")
class Neo4jResourceLifecycleNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenDisposableDatabase_whenProcessCompletes_thenContainerAndVolumesAreRemoved() throws Exception {
    // Arrange
    var output = directory.resolve("successful");

    // Act
    var result = complete(output, "success");

    // Assert
    assertEquals(0, result, () -> diagnostics(output));
    assertEquals("closed", resources(output).getProperty("state"));
    assertRemoved(resources(output));
  }

  @Test
  void givenDisposableDatabase_whenProcessFails_thenFailureSurvivesAndResourcesAreRemoved() throws Exception {
    // Arrange
    var output = directory.resolve("failed");

    // Act
    var result = complete(output, "failure");

    // Assert
    assertNotEquals(0, result);
    assertTrue(diagnostics(output).contains("Deliberate lifecycle failure"), () -> diagnostics(output));
    assertEquals("closed", resources(output).getProperty("state"));
    assertRemoved(resources(output));
  }

  @Test
  void givenResourceLimitedDatabase_whenOwnerJvmIsForciblyTerminated_thenLimitsAreAppliedAndReaperRemovesResources() throws Exception {
    // Arrange
    var budget = new Properties();
    try (var input = getClass().getResourceAsStream("/neo4j-integration.properties")) {
      assertNotNull(input, "The declared integration resource budget must be available");
      budget.load(input);
    }
    var expectedMemory = Long.parseLong(budget.getProperty("container.memory.bytes"));
    var expectedNanoCpus = Long.parseLong(budget.getProperty("container.cpu.count")) * 1_000_000_000L;
    var output = directory.resolve("terminated-owner");
    var process = launch(output, "wait");
    try {
      await(() -> Files.exists(output.resolve("query-ready")), Duration.ofSeconds(120), () -> diagnostics(output));
      var resources = resources(output);
      var limits = DockerClientFactory.instance().client().inspectContainerCmd(resources.getProperty("container.id")).exec().getHostConfig();

      // Act
      process.destroyForcibly();
      assertTrue(process.waitFor(30, TimeUnit.SECONDS));

      // Assert
      assertEquals(expectedMemory, limits.getMemory().longValue());
      assertEquals(expectedMemory, limits.getMemorySwap().longValue());
      assertEquals(expectedNanoCpus, limits.getNanoCPUs().longValue());
      assertTrue(limits.getAutoRemove());
      await(() -> removed(resources), Duration.ofSeconds(60), () -> "Resources remain after owner termination: " + resources);
      assertRemoved(resources);
    } finally {
      terminate(process);
    }
  }

  @Test
  void givenRunningDatabase_whenContainerCrashes_thenDisposableVolumesDisappearWhileOwnerRemainsAlive() throws Exception {
    // Arrange
    var output = directory.resolve("terminated-container");
    var process = launch(output, "wait");
    try {
      await(() -> Files.exists(output.resolve("query-ready")), Duration.ofSeconds(120), () -> diagnostics(output));
      var resources = resources(output);

      // Act
      DockerClientFactory.instance().client().killContainerCmd(resources.getProperty("container.id")).exec();

      // Assert
      await(() -> removed(resources), Duration.ofSeconds(30), () -> "Resources remain after database termination: " + resources);
      assertTrue(process.isAlive(), "Volume removal must not depend on owner shutdown");
      assertRemoved(resources);
    } finally {
      terminate(process);
    }
  }

  @Test
  void givenExplicitDataPreservation_whenDatabaseIsClosedAndResumed_thenOnlyDataVolumeSurvivesAndStoredValueIsReadable() throws Exception {
    // Arrange
    var output = directory.resolve("preserved");
    var resumed = directory.resolve("resumed");
    String volume = null;
    try {
      // Act
      var initialResult = complete(output, "preserve");
      var resources = resources(output);
      volume = resources.getProperty("data.volume");
      assertEquals(0, initialResult, () -> diagnostics(output));
      assertTrue(volumeExists(volume));
      assertFalse(containerExists(resources.getProperty("container.id")));
      for (var mounted : resources.getProperty("mounted.volumes").split(",")) {
        if (!mounted.equals(volume)) {
          assertFalse(volumeExists(mounted), "Disposable log volume must be removed");
        }
      }
      var result = complete(resumed, "resume", output.resolve("preserved-data.properties").toString());

      // Assert
      assertEquals(0, result, () -> diagnostics(resumed));
      assertEquals("42", Files.readString(resumed.resolve("query-ready")));
      assertEquals(volume, resources(resumed).getProperty("data.volume"));
      assertTrue(volumeExists(volume));
    } finally {
      if (volume != null && volumeExists(volume)) {
        DockerClientFactory.instance().client().removeVolumeCmd(volume).exec();
      }
    }
  }

  private int complete(Path output, String mode, String... arguments) throws Exception {
    var process = launch(output, mode, arguments);
    try {
      assertTrue(process.waitFor(180, TimeUnit.SECONDS), () -> diagnostics(output));
      return process.exitValue();
    } finally {
      terminate(process);
    }
  }

  private Process launch(Path output, String mode, String... arguments) throws IOException {
    Files.createDirectories(output);
    var executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var roots = new LinkedHashSet<>(Arrays.asList(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
        .split(Pattern.quote(File.pathSeparator))));
    for (var module : List.of("identity", "source", "cfg", "frontend", "application", "repository", "cli")) {
      var classes = Path.of("target/classes/org.aquilagraph." + module).toAbsolutePath();
      if (!Files.isRegularFile(classes.resolve("module-info.class"))) {
        throw new IOException("Missing canonical module output: " + classes);
      }
      roots.add(classes.toString());
    }
    var classpath = String.join(File.pathSeparator, roots);
    var command = new ArrayList<>(List.of(executable.toString(), "-Xms64m", "-Xmx512m", "-Djava.io.tmpdir=" + directory,
        "-cp", classpath, Neo4jLifecycleProcess.class.getName(), output.toString(), mode));
    command.addAll(List.of(arguments));
    return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.resolve("process.log").toFile()).start();
  }

  private static void terminate(Process process) throws InterruptedException {
    if (process.isAlive()) {
      process.destroyForcibly();
      assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Lifecycle process did not terminate");
    }
  }

  private static Properties resources(Path output) throws IOException {
    var values = new Properties();
    try (var input = Files.newInputStream(output.resolve("resources.properties"))) {
      values.load(input);
    }
    assertFalse(values.getProperty("mounted.volumes").isBlank(), "The observation must include actual mounted volumes");
    return values;
  }

  private static boolean containerExists(String identity) {
    try {
      DockerClientFactory.instance().client().inspectContainerCmd(identity).exec();
      return true;
    } catch (NotFoundException absent) {
      return false;
    }
  }

  private static boolean volumeExists(String name) {
    try {
      DockerClientFactory.instance().client().inspectVolumeCmd(name).exec();
      return true;
    } catch (NotFoundException absent) {
      return false;
    }
  }

  private static boolean removed(Properties resources) {
    if (containerExists(resources.getProperty("container.id"))) {
      return false;
    }
    return List.of(resources.getProperty("mounted.volumes").split(",")).stream().noneMatch(Neo4jResourceLifecycleNeo4jIT::volumeExists);
  }

  private static void assertRemoved(Properties resources) {
    assertTrue(removed(resources), "Owned container or volumes remain: " + resources);
  }

  private static void await(BooleanSupplier condition, Duration timeout, Supplier<String> diagnostic) throws InterruptedException {
    var deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(250);
    }
    assertTrue(condition.getAsBoolean(), diagnostic);
  }

  private static String diagnostics(Path output) {
    try {
      return Files.readString(output.resolve("process.log"));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
