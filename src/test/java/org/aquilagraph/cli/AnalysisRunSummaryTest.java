package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CfgEdge;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
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

  @Test
  void givenMultipleGraphsAndBodylessDeclaration_whenSummaryIsCreated_thenCountsIncludeAllGraphsAndExcludeValuesFromNodes() {
    // Arrange
    var original = CliTestFixtures.completedRun();
    var typeKey = "type:SummarySample";
    var first = new JavaMethod("method:first", typeKey, "first", "first()", "void", List.of(), Set.of(), false, true, true, List.of());
    var second = new JavaMethod("method:second", typeKey, "second", "second()", "void", List.of(), Set.of(), false, true, true, List.of());
    var bodyless = new JavaMethod("method:bodyless", typeKey, "bodyless", "bodyless()", "void", List.of(), Set.of(), false, true, false,
        List.of());
    var type = new JavaType(typeKey, "SummarySample", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        List.of(), false, List.of(), List.of(), List.of(first, bodyless, second), List.of(), List.of());
    var firstGraph = countGraph(first.methodKey(), 1);
    var secondGraph = countGraph(second.methodKey(), 2);
    var representation = new ProgramRepresentation(new JavaProgram(List.of(type)),
        List.of(new MethodAnalysis("analysis:first", first, firstGraph), new MethodAnalysis("analysis:second", second, secondGraph)));
    var run = new AnalysisRun(original.runId(), original.inputSnapshot(), original.environmentSnapshot(), Optional.of(representation),
        List.of(), AnalysisCompletion.COMPLETED);

    // Act
    var summary = AnalysisRunSummary.from(run).program().orElseThrow();

    // Assert
    assertEquals(1, summary.typeCount());
    assertEquals(3, summary.methodCount());
    assertEquals(2, summary.analyzedMethodBodyCount());
    assertEquals(3, summary.controlFlowOperationCount());
    assertEquals(7, summary.controlFlowNodeCount());
    assertEquals(5, summary.controlFlowEdgeCount());
  }

  private static ControlFlowGraph countGraph(String methodKey, int operationCount) {
    var graphId = "cfg:" + methodKey;
    var entry = new EntryNode(graphId + ":entry", graphId);
    var exit = new ExitNode(graphId + ":exit", graphId);
    var operations = new ArrayList<OperationNode>();
    var edges = new ArrayList<CfgEdge>();
    var predecessor = entry.nodeId();
    for (var position = 0; position < operationCount; position++) {
      var operation = new OperationNode(graphId + ":operation:" + position, graphId, OperationKind.MERGE, "merge", List.of(),
          Optional.empty(), Optional.empty(), List.of(), List.of(), List.of());
      operations.add(operation);
      edges.add(new CfgEdge(graphId + ":edge:" + position, graphId, predecessor, operation.nodeId(), TransferKind.NORMAL, Optional.empty()));
      predecessor = operation.nodeId();
    }
    edges.add(new CfgEdge(graphId + ":exit-edge", graphId, predecessor, exit.nodeId(), TransferKind.NORMAL, Optional.empty()));
    var unusedValues = List.of(
        new FlowValue(graphId + ":value:first", graphId, ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty()),
        new FlowValue(graphId + ":value:second", graphId, ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty()));
    return new ControlFlowGraph(graphId, methodKey, entry, operations, unusedValues, exit, edges);
  }
}
