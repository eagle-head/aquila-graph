package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.AnalysisCompletion;
import org.junit.jupiter.api.Test;

class AnalysisRunSummaryTest {
  @Test
  void givenCompletedRun_whenSummaryIsCreated_thenProgramAndRunMetricsAreProjected() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();

    // Act
    var summary = AnalysisRunSummary.from(run);

    // Assert
    assertEquals(run.runId(), summary.runId());
    assertEquals(run.softwareVersion().signature(), summary.softwareVersionSignature());
    assertSame(AnalysisCompletion.COMPLETED, summary.completion());
    assertEquals(25, summary.javaRelease());
    assertEquals(1, summary.sourceFileCount());
    assertEquals(run.diagnostics(), summary.diagnostics());
    var program = summary.program().orElseThrow();
    assertEquals(1, program.typeCount());
    assertEquals(1, program.methodCount());
    assertEquals(1, program.analyzedMethodBodyCount());
    assertEquals(0, program.controlFlowOperationCount());
    assertEquals(2, program.controlFlowNodeCount());
    assertEquals(1, program.controlFlowEdgeCount());
  }

  @Test
  void givenMutableDiagnosticList_whenSummaryIsCreated_thenDiagnosticsAreDefensivelyCopied() {
    // Arrange
    var diagnostic = CliTestFixtures.warningDiagnostic();
    var diagnostics = new ArrayList<>(List.of(diagnostic));

    // Act
    var summary = new AnalysisRunSummary("run", "version", AnalysisCompletion.INCOMPLETE, 25, 1, Optional.empty(), diagnostics);
    diagnostics.clear();

    // Assert
    assertEquals(List.of(diagnostic), summary.diagnostics());
    assertFalse(summary.program().isPresent());
  }
}
