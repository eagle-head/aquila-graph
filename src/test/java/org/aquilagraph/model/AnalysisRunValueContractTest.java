package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisRunValueContractTest {
  @TempDir
  Path root;

  @Test
  void givenCompletedRunWithoutRepresentation_whenConstructed_thenPresenceInvariantRejectsIt() {
    // Arrange
    var input = input();
    var environment = environment();

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new AnalysisRun("provided:run", input, environment, Optional.empty(), List.of(), AnalysisCompletion.COMPLETED));

    // Assert
    assertEquals("A completed run requires a program representation", failure.getMessage());
  }

  @Test
  void givenRejectedRunWithRepresentation_whenConstructed_thenAbsenceInvariantRejectsIt() {
    // Arrange
    var input = input();
    var environment = environment();
    var representation = new ProgramRepresentation(new JavaProgram(List.of()), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisRun("provided:run", input, environment,
        Optional.of(representation), List.of(), AnalysisCompletion.REJECTED));

    // Assert
    assertEquals("A rejected run cannot contain an accepted representation", failure.getMessage());
  }

  @Test
  void givenDirectCompletedRunWithErrorAndMissingGraph_whenConstructed_thenStrongerSemanticTruthRemainsCallerAssured() {
    // Arrange
    var method = new JavaMethod("method:required", "type:Provided", "required", "required()", "void", List.of(), Set.of(), false,
        true, true, List.of());
    var type = new JavaType("type:Provided", "Provided", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(),
        List.of(), List.of(), false, List.of(), List.of(), List.of(method), List.of(), List.of());
    var representation = new ProgramRepresentation(new JavaProgram(List.of(type)), List.of());
    var error = new AnalysisDiagnostic("diagnostic:provided", "provided.error", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR,
        "Provided error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var mutableDiagnostics = new ArrayList<>(List.of(error, error));
    var environment = environment();

    // Act
    var run = new AnalysisRun("provided:run", input(), environment, Optional.of(representation), mutableDiagnostics,
        AnalysisCompletion.COMPLETED);
    mutableDiagnostics.clear();

    // Assert
    assertEquals("provided:run", run.runId());
    assertTrue(run.isComplete());
    assertSame(representation, run.representation().orElseThrow());
    assertTrue(run.representation().orElseThrow().methodAnalyses().isEmpty());
    assertEquals(List.of(error, error), run.diagnostics());
    assertSame(environment.compilerConfiguration(), run.compilerConfiguration());
    assertEquals(SoftwareVersion.from(run.inputSnapshot(), environment), run.softwareVersion());
    assertThrows(UnsupportedOperationException.class, () -> run.diagnostics().clear());
  }

  @Test
  void givenIncompleteRunWithOrWithoutRepresentation_whenConstructed_thenBothPartialStatesRemainValid() {
    // Arrange
    var input = input();
    var environment = environment();
    var representation = new ProgramRepresentation(new JavaProgram(List.of()), List.of());

    // Act
    var absent = new AnalysisRun("provided:absent", input, environment, Optional.empty(), List.of(), AnalysisCompletion.INCOMPLETE);
    var present = new AnalysisRun("provided:present", input, environment, Optional.of(representation), List.of(),
        AnalysisCompletion.INCOMPLETE);

    // Assert
    assertFalse(absent.isComplete());
    assertFalse(present.isComplete());
    assertTrue(absent.representation().isEmpty());
    assertSame(representation, present.representation().orElseThrow());
  }

  @Test
  void givenMethodAndForeignGraph_whenAnalysisIsConstructed_thenMethodKeyMismatchRejectsIt() {
    // Arrange
    var method = new JavaMethod("method:required", "type:Provided", "required", "required()", "void", List.of(), Set.of(), false,
        true, true, List.of());
    var graph = new ControlFlowGraph("cfg:foreign", "method:foreign", new EntryNode("node:entry", "cfg:foreign"), List.of(), List.of(),
        new ExitNode("node:exit", "cfg:foreign"), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new MethodAnalysis("provided:analysis", method, graph));

    // Assert
    assertEquals("Method analysis artifacts must describe the same source method", failure.getMessage());
  }

  private InputSnapshot input() {
    return InputSnapshot.fromSources(root, List.of());
  }

  private static AnalysisEnvironmentSnapshot environment() {
    return new AnalysisEnvironmentSnapshot(CompilerConfiguration.forRelease(JavaRelease.JAVA_25), "provided-compiler", "provided-vendor",
        "25-provided", List.of("--release", "25"), List.of(), List.of());
  }
}
