package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot, "--release", "25");

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
  void givenPackagedJarAndRepeatedParenthesizedTargets_whenProcessRuns_thenHistoricalDiagnosticsRemainRepeated()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Parenthesized.java"),
        "class Parenthesized { int value(int a) { (a) = 1; (a) = 2; return a; } }");

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);

    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_REQUESTED" + System.lineSeparator()), result.output());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: PARENTHESIZED",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: PARENTHESIZED"),
        result.error().lines().toList());
  }

  @Test
  void givenPackagedJarAndParenthesizedTargetBeforeLoop_whenProcessRuns_thenHistoricalSourceOrderIsPreserved()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Parenthesized.java"),
        "class Parenthesized { int value(int a) { (a) = 1; while (a > 0) { a = 0; } return a; } }");

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);

    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode(), result.error());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.while-loop: Java compiler tree kind WHILE_LOOP is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: PARENTHESIZED",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: WHILE_LOOP"),
        result.error().lines().toList());
  }

  @Test
  void givenPackagedJarAndParenthesizedTargetAfterLoop_whenProcessRuns_thenHistoricalSourceOrderIsPreserved()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Parenthesized.java"),
        "class Parenthesized { int value(int a) { while (a > 0) { a = 0; } (a) = 1; return a; } }");

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);

    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode(), result.error());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.while-loop: Java compiler tree kind WHILE_LOOP is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: WHILE_LOOP",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: PARENTHESIZED"),
        result.error().lines().toList());
  }

  @Test
  void givenPackagedJarCalculator_whenProcessRuns_thenUnsupportedInitializersAreReported()
      throws IOException, InterruptedException {
    // Arrange
    var calculator =
        Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(calculator);

    // Assert
    assertTrue(result.completed(), "The packaged analyzer process did not finish within 30 seconds: " + result.output());
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertTrue(result.error().contains("frontend.unsupported-field-initializer"), result.error());
    assertTrue(result.error().contains("frontend.unsupported-enum-constant-initialization"), result.error());
    assertTrue(result.output().contains("Repository: NOT_REQUESTED"), result.output());
  }

  @Test
  void givenPackagedJarAndMalformedUtf8Source_whenProcessRuns_thenInputIsRejected()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.write(
        sourceRoot.resolve("Malformed.java"), new byte[] {(byte) 0xC3, (byte) 0x28});
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);

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
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot, "--release", "25", "--release");

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
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(List.of("--limit-modules", "java.base,java.compiler,java.logging"), Map.of(), sourceRoot);

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
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot);

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
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot, "--persist");

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
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot, "--persist");

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
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(List.of(), Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", ""), sourceRoot, "--persist");

    // Assert
    assertTrue(result.completed());
    assertEquals(2, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: REJECTED"), result.output());
    assertTrue(result.output().contains("Java source files: 0"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_ELIGIBLE" + System.lineSeparator()), result.output());
    assertFalse(result.output().contains("Java types:"), result.output());
    assertFalse(result.error().contains("Repository save failed:"), result.error());
  }

}
