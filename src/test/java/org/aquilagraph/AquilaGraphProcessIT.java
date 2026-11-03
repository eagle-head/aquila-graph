package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import java.util.Optional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.aquilagraph.testsupport.LiteralLabelFixture;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import javax.tools.ToolProvider;

@Tag("process")
class AquilaGraphProcessIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenCapturedCompilerEnvironment_whenPackagedAnalyzerSummarizes_thenSoftwareSignatureMatchesTheExplicitEnvironmentScheme() throws Exception {
    // Arrange
    var root = Files.createDirectories(temporaryDirectory.resolve("environment-source"));
    var source = "class Example { int value() { return 7; } }";
    Files.writeString(root.resolve("Example.java"), source);
    var snapshot = InputSnapshot.fromSources(root, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var environment = StableId.sha256("analysis-environment/2", "language-release", "25", "compiler-implementation",
        ToolProvider.getSystemJavaCompiler().getClass().getName(), "compiler-vendor", System.getProperty("java.vendor"),
        "compiler-version", System.getProperty("java.runtime.version"), "analysis-options", "5", "--release", "25", "-encoding", "UTF-8",
        "-proc:none", "class-path", "0", "module-path", "0", "analysis-environment/2", "0", "0");
    var software = StableId.sha256("software-version", snapshot.snapshotDigest(), environment);

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(root);

    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(0, result.exitCode(), result.error());
    assertTrue(result.output().contains("Software version: " + software), result.output());
    assertTrue(result.output().lines().toList().contains("Completion: COMPLETE"), result.output());
    assertTrue(result.output().contains("Repository: NOT_REQUESTED"), result.output());
    assertEquals("", result.error());
  }

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
    assertTrue(result.output().lines().toList().contains("Completion: COMPLETE"), result.output());
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
    assertTrue(result.output().lines().toList().contains("Completion: COMPLETE"), result.output());
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
          assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
          assertEquals(1, result.error().lines().filter(line -> line.startsWith(
              "  Code: \"compiler.err.invalid.repeatable.annotation.repeated.and.container.present\"")).count(), result.error());
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenPackagedUnsupportedDeclarationWithinExceptionClauses_whenBoundedProcessRuns_thenBothCausesRemainObservable() {
    return Stream.of("try { class Local {} } finally {}", "try (input) {} catch (Exception failure) { class Local {} }")
        .map(statement -> DynamicTest.dynamicTest("givenUnsupportedLocalDeclarationIn" + statement + "_whenPresented_thenNativeEventsAndReasonsRemainVisible", () -> {
          // Arrange
          var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
          Files.writeString(sourceRoot.resolve("Example.java"), "class Example { void subject(AutoCloseable input) throws Exception { " + statement + " } }");
          // Act
          var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
          // Assert
          assertTrue(result.completed(), result.error());
          assertEquals(1, result.exitCode());
          assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
          assertNativeReport(result, sourceRoot);
        }));
  }

  @Test
  void givenPackagedResourceAndFinallySource_whenBoundedProcessRuns_thenNativeQualificationAndCfgRefusalRemainObservable() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Resources.java"), "class Resources { void value(AutoCloseable input) throws Exception { try (input) {} finally {} } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedMultiCatchSource_whenBoundedProcessRuns_thenNativeQualificationExplainsUnionRestriction() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Catching.java"), "class Catching { void value() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedNestedTriesAndLoop_whenBoundedProcessRuns_thenNativeNestedDiagnosticsRetainTheirOrder() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Cleanup.java"), "class Cleanup { void value(boolean flag) { try { try { while (flag) {} } finally {} } finally {} } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedSwitchStatement_whenBoundedProcessRuns_thenNativeSwitchDiagnosticsAndRefusalRemainObservable()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Selection.java"), "class Selection { void value(int input) { switch (input) { default -> {} } } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedConstantSwitchLabel_whenBoundedProcessRuns_thenNativeCaseRefusalRemainsObservable()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Selection.java"), "class Selection { int value(int input) { return switch (input) { case 1 + 2 -> 3; default -> 4; }; } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedPatternSource_whenBoundedProcessRuns_thenNativeDiagnosticsAndRefusalRemainObservable() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Patterns.java"), "class Patterns { boolean value(Object input) { return input instanceof String text; } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedReferenceSource_whenBoundedProcessRuns_thenNativeReferenceRefusalRemainsObservable() throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("References.java"), "class References { java.util.function.IntFunction<int[]> value() { return int[]::new; } }");
    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(sourceRoot);
    // Assert
    assertTrue(result.completed(), result.error());
    assertEquals(1, result.exitCode());
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedLambdaWithNestedExecution_whenBoundedProcessRuns_thenNativeLambdaRefusalRemainsObservable()
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
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedAssertionsAndMonitors_whenBoundedProcessRuns_thenNativeRefusalAndDiagnosticsRemainObservable()
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
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertNativeReport(result, sourceRoot);
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
    assertTrue(result.output().lines().toList().contains("Completion: COMPLETE"), result.output());
    assertFalse(result.output().contains("frontend.unsupported-tree-kind.compilation-unit"), result.output());
    assertFalse(result.output().contains("frontend.unsupported-tree-kind.primitive-type"), result.output());
    assertTrue(result.output().contains("Java release: 25"), result.output());
    assertTrue(result.output().contains("Java source files: 1"), result.output());
    assertTrue(result.output().contains("Repository: NOT_REQUESTED"), result.output());
    assertEquals("", result.error());
  }

  @Test
  void givenPackagedJarAndRepeatedParenthesizedTargets_whenProcessRuns_thenNativeDiagnosticsRemainRepeated()
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
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_REQUESTED" + System.lineSeparator()), result.output());
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedJarAndParenthesizedTargetBeforeLoop_whenProcessRuns_thenCanonicalNativeEventsRemainInOrder()
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
    assertNativeReport(result, sourceRoot);
  }

  @Test
  void givenPackagedJarAndParenthesizedTargetAfterLoop_whenProcessRuns_thenCanonicalNativeEventsRemainInOrder()
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
    assertNativeReport(result, sourceRoot);
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
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
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
        "Usage: aquila-graph <source-root> [--release <feature>] [--persist]"), result.error().lines().toList());
  }

  @Test
  void givenPackagedJarAndMissingCompilerModule_whenProcessRuns_thenEnvironmentFailureHasNoRunSummary()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(sourceRoot.resolve("Supported.java"), "class Supported { int value() { return 1; } }");

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(List.of("--limit-modules", "java.se,org.aquilagraph.cli"), Map.of(), sourceRoot);

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
    assertTrue(result.output().lines().toList().contains("Completion: COMPLETE"), result.output());
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
    assertTrue(result.output().lines().toList().contains("Completion: COMPLETE"), result.output());
    assertTrue(result.output().endsWith("Repository: FAILED" + System.lineSeparator()), result.output());
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
    assertTrue(result.output().lines().toList().contains("Completion: PARTIAL"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_ELIGIBLE" + System.lineSeparator()), result.output());
    assertTrue(result.error().contains("Qualification: SOURCE PROFILE_RESTRICTION"), result.error());
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
    assertTrue(result.output().lines().toList().contains("Completion: REJECTED"), result.output());
    assertTrue(result.output().contains("Java source files: 0"), result.output());
    assertTrue(result.output().endsWith("Repository: NOT_ELIGIBLE" + System.lineSeparator()), result.output());
    assertFalse(result.output().contains("Java types:"), result.output());
    assertFalse(result.error().contains("Repository save failed:"), result.error());
  }

  private static void assertNativeReport(PackagedAnalyzerProcess.Result result, Path root) {
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var run = runner.analyze(new AnalysisRequest(new SourceInput.Root(root), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    assertEquals(run.diagnostics().values().stream().map(diagnostic -> "Diagnostic: \"" + diagnostic.diagnosticKey() + "\"").toList(),
        result.error().lines().filter(line -> line.startsWith("Diagnostic:")).toList(), result.error());
    assertEquals(run.diagnostics().values().stream().map(diagnostic -> "  Code: \"" + diagnostic.code() + "\"").toList(),
        result.error().lines().filter(line -> line.startsWith("  Code:")).toList(), result.error());
    assertEquals(run.diagnostics().values().stream().map(diagnostic -> "  Stage: " + diagnostic.stage()).toList(),
        result.error().lines().filter(line -> line.startsWith("  Stage:")).toList(), result.error());
    assertEquals(run.representation().ast().qualification().reasons().stream().map(reason -> "Qualification: SOURCE " + reason.category()).toList(),
        result.error().lines().filter(line -> line.startsWith("Qualification: SOURCE ")).toList(), result.error());
    assertTrue(result.output().contains("AST: " + run.representation().ast().completion()), result.output());
    assertTrue(result.output().contains("CFG: " + run.representation().cfg().orElseThrow().state()), result.output());
  }

}
