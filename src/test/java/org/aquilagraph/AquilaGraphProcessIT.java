package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AquilaGraphProcessIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenPackagedJarWithSupportedHeaderTypes_whenProcessRuns_thenCompletionIsReported()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(
        sourceRoot.resolve("Supported.java"),
        "class Supported { int value(int input) { return input + 1; } }");
    // Act
    var result = runPackagedJar(sourceRoot, "--release", "25");

    // Assert
    assertTrue(result.completed(), "The packaged analyzer process did not finish within 30 seconds: " + result.output());
    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("Completion: COMPLETED"), result.output());
    assertFalse(result.output().contains("frontend.unsupported-tree-kind.compilation-unit"), result.output());
    assertFalse(result.output().contains("frontend.unsupported-tree-kind.primitive-type"), result.output());
    assertTrue(result.output().contains("Java release: 25"), result.output());
    assertTrue(result.output().contains("Java source files: 1"), result.output());
    assertTrue(result.output().contains("Repository: NOT_REQUESTED"), result.output());
    assertEquals("", result.error());
  }

  @Test
  void givenPackagedJarCalculator_whenProcessRuns_thenUnsupportedInitializersAreReported()
      throws IOException, InterruptedException {
    // Arrange
    var calculator =
        Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();

    // Act
    var result = runPackagedJar(calculator);

    // Assert
    assertTrue(result.completed(), "The packaged analyzer process did not finish within 30 seconds: " + result.output());
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertTrue(result.error().contains("frontend.unsupported-field-initializer"), result.error());
    assertTrue(result.error().contains("frontend.unsupported-enum-constant-initialization"), result.error());
    assertTrue(result.output().contains("Repository: NOT_ELIGIBLE"), result.output());
  }

  @Test
  void givenPackagedJarAndMalformedUtf8Source_whenProcessRuns_thenInputIsRejected()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.write(
        sourceRoot.resolve("Malformed.java"), new byte[] {(byte) 0xC3, (byte) 0x28});
    // Act
    var result = runPackagedJar(sourceRoot);

    // Assert
    assertTrue(result.completed(), "The packaged analyzer process did not finish within 30 seconds: " + result.output());
    assertEquals(2, result.exitCode(), result.output());
    assertEquals("", result.output());
    assertTrue(result.error().contains("Input rejected:"), result.error());
    assertTrue(result.error().contains("Malformed.java"), result.error());
  }

  @Test
  void givenPackagedJarAndDuplicateReleaseWithoutValue_whenProcessRuns_thenDuplicateRefusalPrecedesMissingValue()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = temporaryDirectory.resolve("unread-source");

    // Act
    var result = runPackagedJar(sourceRoot, "--release", "25", "--release");

    // Assert
    assertTrue(result.completed());
    assertEquals(2, result.exitCode());
    assertEquals("", result.output());
    assertEquals(List.of("--release may be supplied only once",
        "Usage: java -jar aquila-graph.jar <source-root> [--release <feature>] [--persist]"), result.error().lines().toList());
  }

  @Test
  void givenPackagedJarAndMissingCompilerModule_whenProcessRuns_thenEnvironmentFailureHasNoRunSummary()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Supported.java"), "class Supported { int value() { return 1; } }");

    // Act
    var result = runPackagedJar(List.of("--limit-modules", "java.base,java.compiler,java.logging"), Map.of(), sourceRoot);

    // Assert
    assertTrue(result.completed());
    assertEquals(1, result.exitCode(), result.error());
    assertEquals("", result.output());
    assertTrue(result.error().startsWith("Analysis failed:"), result.error());
    assertFalse(result.error().contains("Usage:"), result.error());
  }

  @Test
  void givenPackagedJarAndCompletedSourceWithoutSave_whenDatabaseSettingsAreInvalid_thenSettingsRemainLazy()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Supported.java"), "class Supported { int value() { return 1; } }");

    // Act
    var result = runPackagedJar(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot);

    // Assert
    assertTrue(result.completed());
    assertEquals(0, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: COMPLETED"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_REQUESTED" + System.lineSeparator()), result.output());
    assertEquals("", result.error());
  }

  @Test
  void givenPackagedJarAndCompletedSaveRequest_whenDatabaseSettingsAreInvalid_thenCompletedRunAndFailureArePresented()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Supported.java"), "class Supported { int value() { return 1; } }");

    // Act
    var result = runPackagedJar(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot, "--persist");

    // Assert
    assertTrue(result.completed());
    assertEquals(3, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: COMPLETED"), result.output());
    assertFalse(result.output().contains("Repository:"), result.output());
    assertEquals("Repository save failed: Invalid Neo4j repository configuration" + System.lineSeparator(), result.error());
  }

  @Test
  void givenPackagedJarAndIncompleteSaveRequest_whenDatabaseSettingsAreInvalid_thenSettingsRemainLazy()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Unsupported.java"), "class Unsupported { void run() { while (true) { } } }");

    // Act
    var result = runPackagedJar(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot, "--persist");

    // Assert
    assertTrue(result.completed());
    assertEquals(1, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_ELIGIBLE" + System.lineSeparator()), result.output());
    assertTrue(result.error().contains("frontend.unsupported-tree-kind.while-loop"), result.error());
    assertFalse(result.error().contains("Repository save failed:"), result.error());
  }

  @Test
  void givenPackagedJarAndEmptySaveRequest_whenDatabaseSettingsAreInvalid_thenRejectedRunRemainsIneligible()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));

    // Act
    var result = runPackagedJar(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot, "--persist");

    // Assert
    assertTrue(result.completed());
    assertEquals(2, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: REJECTED"), result.output());
    assertTrue(result.output().contains("Java source files: 0"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_ELIGIBLE" + System.lineSeparator()), result.output());
    assertFalse(result.output().contains("Java types:"), result.output());
    assertFalse(result.error().contains("Repository save failed:"), result.error());
  }

  private ProcessResult runPackagedJar(Path sourceRoot, String... options) throws IOException, InterruptedException {
    return runPackagedJar(List.of(), Map.of(), sourceRoot, options);
  }

  private ProcessResult runPackagedJar(List<String> javaOptions, Map<String, String> environment, Path sourceRoot, String... options)
      throws IOException, InterruptedException {
    var javaName = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaName);
    var jar = Path.of("target/aquila-graph-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
    var command = new ArrayList<>(List.of(javaExecutable.toString()));
    command.addAll(javaOptions);
    command.addAll(List.of("-jar", jar.toString(), sourceRoot.toString()));
    command.addAll(List.of(options));
    var outputFile = temporaryDirectory.resolve("process-output.txt");
    var errorFile = temporaryDirectory.resolve("process-error.txt");
    var builder = new ProcessBuilder(command).redirectOutput(outputFile.toFile()).redirectError(errorFile.toFile());
    builder.environment().putAll(environment);
    var process = builder.start();
    try {
      var completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
      if (!completed) {
        process.destroyForcibly().waitFor();
      }
      return new ProcessResult(completed, process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8),
          Files.readString(errorFile, StandardCharsets.UTF_8));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly().waitFor();
      }
    }
  }

  private record ProcessResult(boolean completed, int exitCode, String output, String error) {
  }
}
