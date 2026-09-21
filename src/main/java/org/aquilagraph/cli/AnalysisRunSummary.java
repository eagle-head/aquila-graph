package org.aquilagraph.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.ProgramRepresentation;

/** Immutable command-line projection of an analysis run. */
record AnalysisRunSummary(String runId, String softwareVersionSignature, AnalysisCompletion completion, int javaRelease,
    int sourceFileCount, Optional<ProgramSummary> program, List<AnalysisDiagnostic> diagnostics) {
  AnalysisRunSummary {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(softwareVersionSignature, "softwareVersionSignature");
    Objects.requireNonNull(completion, "completion");
    Objects.requireNonNull(program, "program");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }

  static AnalysisRunSummary from(AnalysisRun run) {
    Objects.requireNonNull(run, "run");
    return new AnalysisRunSummary(run.runId(), run.softwareVersion().signature(), run.completion(),
        run.compilerConfiguration().release().feature(), run.inputSnapshot().sourceFileCount(),
        run.representation().map(ProgramSummary::from), run.diagnostics());
  }

  record ProgramSummary(int typeCount, int methodCount, int analyzedMethodBodyCount, int controlFlowOperationCount,
      int controlFlowNodeCount, int controlFlowEdgeCount) {
    static ProgramSummary from(ProgramRepresentation representation) {
      var operationCount = 0;
      var nodeCount = 0;
      var edgeCount = 0;
      for (var methodAnalysis : representation.methodAnalyses()) {
        var graph = methodAnalysis.controlFlowGraph();
        operationCount += graph.operationNodes().size();
        nodeCount += graph.nodeCount();
        edgeCount += graph.edges().size();
      }
      return new ProgramSummary(representation.program().types().size(), representation.program().methodCount(),
          representation.methodAnalyses().size(), operationCount, nodeCount, edgeCount);
    }
  }
}
