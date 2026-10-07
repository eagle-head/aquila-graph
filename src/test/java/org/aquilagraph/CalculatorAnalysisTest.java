package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.junit.jupiter.api.Test;

class CalculatorAnalysisTest {
  private static final Path CALCULATOR_SOURCES =
      Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();

  @Test
  void givenCalculatorUnsupportedInitializers_whenAnalyzedEndToEnd_thenDiagnosticsPreventCompletionWithoutDroppingMethods()
      throws IOException {
    // Arrange
    var classFilesBefore = classFilesUnder(CALCULATOR_SOURCES);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run =
        new SourceAnalysisFixture()
            .analyze(frontend.analyze(CALCULATOR_SOURCES, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));

    // Assert
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertTrue(run.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-field-initializer")));
    assertTrue(run.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-enum-constant-initialization")));
    assertEquals(3, run.representation().ast().capturedInput().orElseThrow().sourceFileCount());
    var representation = run.representation();
    assertEquals(3, representation.ast().result().orElseThrow().program().types().size());
    assertEquals(
        representation.ast().result().orElseThrow().program().methods().stream().filter(method -> method.hasBody()).count(),
        representation.cfg().orElseThrow().methods().size());
    assertTrue(
        representation.cfg().orElseThrow().methods().stream()
            .allMatch(
                analysis ->
                    !analysis.graph().orElseThrow().operationNodes().isEmpty()
                        && !analysis.graph().orElseThrow().edges().isEmpty()));
    assertEquals(classFilesBefore, classFilesUnder(CALCULATOR_SOURCES));
  }

  @Test
  void givenCalculatorSwitchAndTry_whenAnalyzed_thenAlternativeAndCatchFlowsAreExplicit()
      throws IOException {
    // Arrange
    var run =
        new SourceAnalysisFixture()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(CALCULATOR_SOURCES, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));
    var analyses = run.representation().cfg().orElseThrow().methods();

    // Act
    var calculate =
        analyses.stream()
            .filter(analysis -> run.representation().ast().result().orElseThrow().method(analysis.methodKey()).orElseThrow().name().equals("calculate"))
            .findFirst()
            .orElseThrow();
    var applicationRun =
        analyses.stream()
            .filter(
                analysis ->
                    run.representation().ast().result().orElseThrow().method(analysis.methodKey()).orElseThrow().name().equals("run")
                        && run.representation().ast().result().orElseThrow().method(analysis.methodKey()).orElseThrow().ownerTypeKey().contains("CalculatorApplication"))
            .findFirst()
            .orElseThrow();

    // Assert
    assertTrue(
        calculate.graph().orElseThrow().operationNodes().stream()
            .anyMatch(node -> node.kind() == OperationKind.SWITCH_SELECTOR));
    assertTrue(
        calculate.graph().orElseThrow().edges().stream()
                .filter(edge -> edge.transferKind() == TransferKind.SWITCH_ALTERNATIVE)
                .count()
            >= 4);
    assertTrue(
        applicationRun.graph().orElseThrow().operationNodes().stream()
            .anyMatch(node -> node.kind() == OperationKind.CATCH));
    assertTrue(
        applicationRun.graph().orElseThrow().edges().stream()
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
