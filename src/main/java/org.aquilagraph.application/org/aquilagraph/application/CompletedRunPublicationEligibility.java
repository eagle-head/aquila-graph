package org.aquilagraph.application;

import java.util.HashSet;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.JavaMethod;

/** Completed-run publication requires every body to have its exact qualified graph. */
final class CompletedRunPublicationEligibility {
  private CompletedRunPublicationEligibility() {
  }

  static boolean accepts(AnalysisRun run) {
    if (run.completion() != AnalysisCompletion.COMPLETE || run.representation().ast().capturedEnvironment().isEmpty()
        || run.representation().cfg().filter(value -> value.state() == CfgState.COMPLETE).isEmpty()
        || run.diagnostics().values().stream().anyMatch(AnalysisDiagnostic::isError)) {
      return false;
    }
    var ast = run.representation().ast().result().orElseThrow();
    var required = new HashSet<>(ast.program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList());
    var graphs = run.representation().cfg().orElseThrow().methods().stream().flatMap(value -> value.graph().stream()).toList();
    return required.equals(new HashSet<>(graphs.stream().map(value -> value.methodKey()).toList()))
        && graphs.stream()
            .allMatch(value -> value.identity().astIdentity().equals(ast.identity()) && value.body().astIdentity().equals(ast.identity()))
        && ast.coverage().observations().stream().noneMatch(value -> value.classification().blocksCompletion());
  }
}
