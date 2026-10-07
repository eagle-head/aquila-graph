package org.aquilagraph.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.cli.AnalysisDiagnosticProjection.DiagnosticSummary;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.ProgramRepresentation;
import org.aquilagraph.identity.api.SoftwareVersion;

/** Immutable command-line projection of an analysis run. */
record AnalysisRunSummary(String runId, String softwareVersionSignature, AnalysisCompletion completion, int javaRelease,
    int sourceFileCount, Optional<ProgramSummary> program, List<DiagnosticSummary> diagnostics) {
  AnalysisRunSummary {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(softwareVersionSignature, "softwareVersionSignature");
    Objects.requireNonNull(completion, "completion");
    Objects.requireNonNull(program, "program");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }

  static AnalysisRunSummary from(AnalysisRun run) {
    Objects.requireNonNull(run, "run");
    var ast = run.representation().ast();
    var software = ast.capturedInput()
        .flatMap(snapshot -> ast.capturedEnvironment().map(environment -> SoftwareVersion.from(snapshot, environment)));
    return new AnalysisRunSummary(run.identity().key(), software.map(SoftwareVersion::signature).orElse("unavailable"), run.completion(),
        ast.requestedConfiguration().orElseThrow().release().feature(),
        ast.capturedInput().map(snapshot -> snapshot.sourceFileCount()).orElse(0),
        ast.result().map(ignored -> ProgramSummary.from(run.representation())), AnalysisDiagnosticProjection.from(run));
  }

  record ProgramSummary(int typeCount, int methodCount, int analyzedMethodBodyCount, int controlFlowOperationCount,
      int controlFlowNodeCount, int controlFlowEdgeCount) {
    static ProgramSummary from(ProgramRepresentation representation) {
      var operationCount = 0;
      var nodeCount = 0;
      var edgeCount = 0;
      var graphs = representation.cfg().stream().flatMap(cfg -> cfg.methods().stream()).flatMap(method -> method.graph().stream()).toList();
      for (var graph : graphs) {
        operationCount += graph.operationNodes().size();
        nodeCount += graph.nodeCount();
        edgeCount += graph.edges().size();
      }
      var program = representation.ast().result().orElseThrow().program();
      return new ProgramSummary(program.types().size(), program.methodCount(), graphs.size(), operationCount, nodeCount, edgeCount);
    }
  }
}
