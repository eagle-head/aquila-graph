package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.TransferKind;
import org.junit.jupiter.api.Test;

class CalculatorAnalysisTest {
  private static final Path CALCULATOR_SOURCES =
      Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();

  @Test
  void givenCalculatorSources_whenAnalyzedEndToEnd_thenEveryConcreteBodyHasCfg()
      throws IOException {
    // Arrange
    var classFilesBefore = classFilesUnder(CALCULATOR_SOURCES);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run =
        new AnalysisEngine()
            .analyze(frontend.analyze(CALCULATOR_SOURCES, CompilerConfiguration.standard(JavaRelease.JAVA_25)));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, run.completion());
    assertEquals(3, run.inputSnapshot().sourceFileCount());
    var representation = run.representation().orElseThrow();
    assertEquals(3, representation.program().types().size());
    assertEquals(
        representation.program().methods().stream().filter(method -> method.hasBody()).count(),
        representation.methodAnalyses().size());
    assertTrue(
        representation.methodAnalyses().stream()
            .allMatch(
                analysis ->
                    !analysis.controlFlowGraph().operationNodes().isEmpty()
                        && !analysis.controlFlowGraph().edges().isEmpty()));
    assertEquals(classFilesBefore, classFilesUnder(CALCULATOR_SOURCES));
  }

  @Test
  void givenCalculatorSwitchAndTry_whenAnalyzed_thenAlternativeAndCatchFlowsAreExplicit()
      throws IOException {
    // Arrange
    var run =
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(CALCULATOR_SOURCES, CompilerConfiguration.standard(JavaRelease.JAVA_25)));
    var analyses = run.representation().orElseThrow().methodAnalyses();

    // Act
    var calculate =
        analyses.stream()
            .filter(analysis -> analysis.sourceMethod().name().equals("calculate"))
            .findFirst()
            .orElseThrow();
    var applicationRun =
        analyses.stream()
            .filter(
                analysis ->
                    analysis.sourceMethod().name().equals("run")
                        && analysis.sourceMethod().ownerTypeKey().contains("CalculatorApplication"))
            .findFirst()
            .orElseThrow();

    // Assert
    assertTrue(
        calculate.controlFlowGraph().operationNodes().stream()
            .anyMatch(node -> node.kind() == OperationKind.SWITCH_SELECTOR));
    assertTrue(
        calculate.controlFlowGraph().edges().stream()
                .filter(edge -> edge.transferKind() == TransferKind.SWITCH_ALTERNATIVE)
                .count()
            >= 4);
    assertTrue(
        applicationRun.controlFlowGraph().operationNodes().stream()
            .anyMatch(node -> node.kind() == OperationKind.CATCH));
    assertTrue(
        applicationRun.controlFlowGraph().edges().stream()
            .anyMatch(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  private static Set<Path> classFilesUnder(Path root) throws IOException {
    try (var files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".class"))
          .map(path -> path.toAbsolutePath().normalize())
          .collect(Collectors.toUnmodifiableSet());
    }
  }
}
