package org.aquilagraph.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.TransferKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisEngineTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenSupportedBranchingSource_whenAnalysisRuns_thenCfgPreservesTypedFlow()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Example.java"),
        """
        class Example {
          int choose(boolean enabled, int left, int right) {
            if (enabled && left > 0) {
              return left / right;
            }
            return 0;
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var engine = new AnalysisEngine();

    // Act
    var run =
        engine.analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.java25()));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, run.completion());
    var methodAnalysis =
        run.representation().orElseThrow().methodAnalyses().stream()
            .filter(analysis -> analysis.sourceMethod().name().equals("choose"))
            .findFirst()
            .orElseThrow();
    assertTrue(
        methodAnalysis.controlFlowGraph().operationNodes().stream()
            .anyMatch(node -> node.kind() == OperationKind.DECISION));
    assertTrue(
        methodAnalysis.controlFlowGraph().edges().stream()
            .anyMatch(edge -> edge.transferKind() == TransferKind.TRUE_BRANCH));
    assertTrue(
        methodAnalysis.controlFlowGraph().edges().stream()
            .anyMatch(edge -> edge.transferKind() == TransferKind.FALSE_BRANCH));
    assertTrue(
        methodAnalysis.controlFlowGraph().edges().stream()
            .anyMatch(
                edge ->
                    edge.transferKind() == TransferKind.EXCEPTIONAL
                        && edge.discriminator().orElse("").equals("java.lang.ArithmeticException")));
    assertFalse(methodAnalysis.controlFlowGraph().nodes().isEmpty());
  }

  @Test
  void givenSyntaxError_whenAnalysisRuns_thenRunIsIncompleteAndDiagnosticIsRetained()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Broken.java"), "class Broken { void x( }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run =
        new AnalysisEngine()
            .analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.java25()));

    // Assert
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
    assertTrue(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
  }

  @Test
  void givenEmptySourceRoot_whenAnalysisRuns_thenRunIsRejected() throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run =
        new AnalysisEngine()
            .analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.java25()));

    // Assert
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertTrue(run.representation().isEmpty());
  }

  @Test
  void givenDependencyContentChangesAtSamePath_whenAnalysisRuns_thenRunIdentityChanges()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Example.java"),
        "class Example { int value() { return 1; } }");
    var dependency = Files.createDirectory(temporaryDirectory.resolve("dependency"));
    Files.writeString(dependency.resolve("marker.txt"), "first");
    var configuration =
        new CompilerConfiguration(25, List.of(dependency), List.of(), List.of());
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var engine = new AnalysisEngine();

    // Act
    var firstRun = engine.analyze(frontend.analyze(temporaryDirectory, configuration));
    Files.writeString(dependency.resolve("marker.txt"), "second");
    var secondRun = engine.analyze(frontend.analyze(temporaryDirectory, configuration));

    // Assert
    assertNotEquals(firstRun.runId(), secondRun.runId());
  }

  @Test
  void givenEquivalentDependencyContentAtDifferentPaths_whenAnalysisRuns_thenRunIdentityIsStable()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Example.java"),
        "class Example { int value() { return 1; } }");
    var firstDependency = Files.createDirectory(temporaryDirectory.resolve("first-dependency"));
    var secondDependency = Files.createDirectory(temporaryDirectory.resolve("second-dependency"));
    Files.writeString(firstDependency.resolve("marker.txt"), "same");
    Files.writeString(secondDependency.resolve("marker.txt"), "same");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var engine = new AnalysisEngine();

    // Act
    var firstRun =
        engine.analyze(
            frontend.analyze(
                temporaryDirectory,
                new CompilerConfiguration(
                    25, List.of(firstDependency), List.of(), List.of())));
    var secondRun =
        engine.analyze(
            frontend.analyze(
                temporaryDirectory,
                new CompilerConfiguration(
                    25, List.of(secondDependency), List.of(), List.of())));

    // Assert
    assertEquals(firstRun.runId(), secondRun.runId());
  }
}
