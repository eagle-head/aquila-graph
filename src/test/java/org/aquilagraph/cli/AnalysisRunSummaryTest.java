package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.junit.jupiter.api.Test;

class AnalysisRunSummaryTest {
  @Test
  void givenCompletedRun_whenSummaryIsCreated_thenProgramAndRunMetricsAreProjected() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();

    // Act
    var summary = AnalysisRunSummary.from(run);

    // Assert
    assertEquals(run.identity().key(), summary.run().identity().key());
    assertEquals(SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature(), summary.softwareVersionSignature());
    assertSame(AnalysisCompletion.COMPLETE, summary.run().completion());
    assertEquals(25, summary.run().representation().ast().requestedConfiguration().orElseThrow().release().feature());
    assertEquals(1, summary.run().representation().ast().capturedInput().orElseThrow().sourceFileCount());
    assertEquals(List.of(CliTestFixtures.warningDiagnostic()), summary.occurrences().values());
    var program = summary.program().orElseThrow();
    assertEquals(1, program.typeCount());
    assertEquals(1, program.methodCount());
    assertEquals(1, program.analyzedMethodBodyCount());
    assertEquals(0, program.controlFlowOperationCount());
    assertEquals(2, program.controlFlowNodeCount());
    assertEquals(1, program.controlFlowEdgeCount());
  }

  @Test
  void givenMultipleGraphsAndBodylessDeclaration_whenSummaryIsCreated_thenCountsIncludeAllGraphsAndExcludeValuesFromNodes() {
    // Arrange
    var run = CliTestFixtures.analyze("interface Example { static void first() {} void bodyless(); static int second() { return 1; } }");
    var graphs = run.representation().cfg().orElseThrow().methods().stream().flatMap(method -> method.graph().stream()).toList();
    // Act
    var summary = AnalysisRunSummary.from(run).program().orElseThrow();

    // Assert
    assertEquals(1, summary.typeCount());
    assertEquals(3, summary.methodCount());
    assertEquals(2, summary.analyzedMethodBodyCount());
    assertEquals(2, summary.controlFlowOperationCount());
    assertEquals(6, summary.controlFlowNodeCount());
    assertEquals(4, summary.controlFlowEdgeCount());
    assertFalse(graphs.stream().flatMap(graph -> graph.values().stream()).toList().isEmpty());
  }

}
