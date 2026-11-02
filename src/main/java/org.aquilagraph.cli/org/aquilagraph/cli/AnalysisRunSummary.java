package org.aquilagraph.cli;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.ProgramRepresentation;
import org.aquilagraph.identity.api.SoftwareVersion;

/** Immutable command-line projection of an analysis run. */
record AnalysisRunSummary(AnalysisRun run, String softwareVersionSignature, Optional<ProgramSummary> program,
    DiagnosticOccurrences occurrences) {
  AnalysisRunSummary {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(softwareVersionSignature, "softwareVersionSignature");
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(occurrences, "occurrences");
  }

  static AnalysisRunSummary from(AnalysisRun run) {
    Objects.requireNonNull(run, "run");
    var ast = run.representation().ast();
    var software = ast.capturedInput()
        .flatMap(snapshot -> ast.capturedEnvironment().map(environment -> SoftwareVersion.from(snapshot, environment)));
    return new AnalysisRunSummary(run, software.map(SoftwareVersion::signature).orElse("unavailable"),
        ast.result().map(ignored -> ProgramSummary.from(run.representation())), DiagnosticOccurrences.from(run));
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
