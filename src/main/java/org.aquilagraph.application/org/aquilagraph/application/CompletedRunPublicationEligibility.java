package org.aquilagraph.application;

import java.util.ArrayList;
import java.util.HashSet;
import org.aquilagraph.application.PublicationEligibility.Impediment;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaMethod;

/** Completed-run publication requires every body to have its exact qualified graph. */
final class CompletedRunPublicationEligibility {
  private CompletedRunPublicationEligibility() {
  }

  static PublicationEligibility assess(AnalysisRun run) {
    var impediments = new ArrayList<Impediment>();
    var source = run.representation().ast();
    var ast = source.result();
    var completeCfg = run.representation().cfg().filter(value -> value.state() == CfgState.COMPLETE);
    if (run.completion() != AnalysisCompletion.COMPLETE) {
      impediments.add(Impediment.RUN_NOT_COMPLETE);
    }
    if (ast.isEmpty() || source.capturedEnvironment().isEmpty()) {
      impediments.add(Impediment.SOURCE_UNAVAILABLE);
    }
    if (completeCfg.isEmpty()) {
      impediments.add(Impediment.CFG_NOT_COMPLETE);
    }
    if (run.diagnostics().values().stream().anyMatch(AnalysisDiagnostic::isError)) {
      impediments.add(Impediment.ERROR_DIAGNOSTICS);
    }
    if (ast.isPresent()) {
      var result = ast.orElseThrow();
      if (completeCfg.isPresent() && !coversBodies(result, completeCfg.orElseThrow())) {
        impediments.add(Impediment.BODY_COVERAGE_INCOMPLETE);
      }
      if (result.coverage().observations().stream().anyMatch(value -> value.classification().blocksCompletion())) {
        impediments.add(Impediment.SOURCE_COVERAGE_BLOCKED);
      }
    }
    return new PublicationEligibility(impediments);
  }

  private static boolean coversBodies(AstResult ast, CfgOutcome cfg) {
    var required = new HashSet<>(ast.program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList());
    var graphs = cfg.methods().stream().flatMap(value -> value.graph().stream()).toList();
    return required.equals(new HashSet<>(graphs.stream().map(value -> value.methodKey()).toList())) && graphs.stream()
        .allMatch(value -> value.identity().astIdentity().equals(ast.identity()) && value.body().astIdentity().equals(ast.identity()));
  }
}
