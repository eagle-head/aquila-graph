package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.aquilagraph.testsupport.LiteralLabelFixture;

class AquilaGraphProcessIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenCompilerNormalizedLiteralLabels_whenPackagedAnalyzerRuns_thenBoundedGraphsComplete() throws Exception {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("literal-labels"));
    Files.writeString(sourceRoot.resolve("Example.java"), LiteralLabelFixture.SOURCE);

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);

    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(0, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: COMPLETED"), result.output());
    assertEquals("", result.error());
  }

  @Test
  void givenWrittenUnaryAndMixedStringStages_whenPackagedAnalyzerRuns_thenSupportedGraphsComplete() throws Exception {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("written-source"));
    Files.writeString(sourceRoot.resolve("Example.java"), "class Example { int minimum() { return -2147483648; } "
        + "String first() { return 1 + 2 + \"a\" + \"b\"; } String second(String x) { return x + \"c\" + \"d\"; } }");

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);

    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(0, result.exitCode(), result.error());
    assertTrue(result.output().contains("Completion: COMPLETED"), result.output());
    assertEquals("", result.error());
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidRepeatableMixtures_whenPackagedAnalyzerRuns_thenCompilerErrorsAndIncompleteExitRemainObservable() {
    // Arrange
    var annotations = List.of("@Tag(1) @Tag(2) @Tags({@Tag(3)})", "@Tags({@Tag(1)}) @Tags({@Tag(2)}) @Groups({@Tags({@Tag(3)})})");
    // Act
    var observations = annotations.stream().map(value -> DynamicTest.dynamicTest(
        "given" + (value.startsWith("@Tag(") ? "RepeatedBaseWithContainer" : "RepeatedContainersWithGroup")
            + "_whenPackagedAnalyzerRuns_thenCompilerErrorsAndIncompleteExitRemainObservable", () -> {
          // Arrange
          var sourceRoot = Files.createDirectories(temporaryDirectory.resolve(value.startsWith("@Tag(") ? "base" : "containers"));
          Files.writeString(sourceRoot.resolve("Invalid.java"), """
              import java.lang.annotation.Repeatable;
              @Repeatable(Tags.class) @interface Tag { int value(); }
              @Repeatable(Groups.class) @interface Tags { Tag[] value(); }
              @interface Groups { Tags[] value(); }
              """ + value + " class Invalid {}");

          // Act
          var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);

          // Assert
          assertTrue(result.completed(), result.error());
          assertEquals(1, result.exitCode());
          assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
          assertEquals(1, result.error().lines().filter(line -> line.startsWith(
              "ERROR FRONTEND compiler.err.invalid.repeatable.annotation.repeated.and.container.present:")).count(), result.error());
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenPackagedUnsupportedDeclarationWithinExceptionClauses_whenBoundedProcessRuns_thenBothCausesRemainObservable() {
    return Stream.of("try { class Local {} } finally {}", "try (input) {} catch (Exception failure) { class Local {} }")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
          Files.writeString(sourceRoot.resolve("Example.java"), "class Example { void subject(AutoCloseable input) throws Exception { " + statement + " } }");
          // Act
          var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
          // Assert
          assertTrue(result.completed(), result.error());
          assertEquals(1, result.exitCode());
          assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
          assertEquals(List.of("ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: TRY",
              "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: CLASS"), result.error().lines().toList());
        }));
  }

  @Test
  void givenPackagedResourceAndFinallySource_whenBoundedProcessRuns_thenHistoricalTryDiagnosticRemainsObservable() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Resources.java"), "class Resources { void value(AutoCloseable input) throws Exception { try (input) {} finally {} } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of("ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: TRY"), result.error().lines().toList());
  }

  @Test
  void givenPackagedMultiCatchSource_whenBoundedProcessRuns_thenHistoricalUnionDiagnosticRemainsObservable() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Catching.java"), "class Catching { void value() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of("ERROR FRONTEND frontend.unsupported-tree-kind.union-type: Java compiler tree kind UNION_TYPE is outside the supported semantic graph profile"), result.error().lines().toList());
  }

  @Test
  void givenPackagedNestedTriesAndLoop_whenBoundedProcessRuns_thenHistoricalNestedDiagnosticsRetainTheirOrder() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Cleanup.java"), "class Cleanup { void value(boolean flag) { try { try { while (flag) {} } finally {} } finally {} } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.while-loop: Java compiler tree kind WHILE_LOOP is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: TRY",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: TRY",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: WHILE_LOOP"), result.error().lines().toList());
  }

  @Test
  void givenPackagedSwitchStatement_whenBoundedProcessRuns_thenHistoricalSwitchDiagnosticsAndRefusalRemainObservable()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Selection.java"), "class Selection { void value(int input) { switch (input) { default -> {} } } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.switch: Java compiler tree kind SWITCH is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: SWITCH"), result.error().lines().toList());
  }

  @Test
  void givenPackagedConstantSwitchLabel_whenBoundedProcessRuns_thenHistoricalCaseRefusalRemainsObservable()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Selection.java"), "class Selection { int value(int input) { return switch (input) { case 1 + 2 -> 3; default -> 4; }; } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of("ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: CASE"), result.error().lines().toList());
  }

  @Test
  void givenPackagedPatternSource_whenBoundedProcessRuns_thenHistoricalDiagnosticsAndRefusalRemainObservable() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Patterns.java"), "class Patterns { boolean value(Object input) { return input instanceof String text; } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.instance-of: Java compiler tree kind INSTANCE_OF is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-tree-kind.binding-pattern: Java compiler tree kind BINDING_PATTERN is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: INSTANCE_OF"), result.error().lines().toList());
  }

  @Test
  void givenPackagedReferenceSource_whenBoundedProcessRuns_thenHistoricalReferenceRefusalRemainsObservable() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("References.java"), "class References { java.util.function.IntFunction<int[]> value() { return int[]::new; } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.member-reference: Java compiler tree kind MEMBER_REFERENCE is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: MEMBER_REFERENCE"), result.error().lines().toList());
  }

  @Test
  void givenPackagedLambdaWithNestedExecution_whenBoundedProcessRuns_thenHistoricalLambdaRefusalRemainsObservable()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Functions.java"),
        "class Functions { int field; Runnable value() { return () -> { field = 7; return; }; } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.lambda-expression: Java compiler tree kind LAMBDA_EXPRESSION is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: LAMBDA_EXPRESSION"), result.error().lines().toList());
  }

  @Test
  void givenPackagedAssertionsAndMonitors_whenBoundedProcessRuns_thenHistoricalRefusalAndDiagnosticsRemainObservable()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Statements.java"),
        "class Statements { void value(Object lock, boolean flag) { assert flag; synchronized (lock) {} } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertEquals(List.of(
        "ERROR FRONTEND frontend.unsupported-tree-kind.assert: Java compiler tree kind ASSERT is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-tree-kind.synchronized: Java compiler tree kind SYNCHRONIZED is outside the supported semantic graph profile",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: ASSERT",
        "ERROR FRONTEND frontend.unsupported-syntax: Unsupported Java construct in method body: SYNCHRONIZED"), result.error().lines().toList());
  }

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
