package org.aquilagraph.application;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.DiagnosticSeverity;

/** Application-owned combination of independently established context outcomes. */
final class AnalysisQualification {
  private AnalysisQualification() {
  }

  static AnalysisCompletion assess(AstOutcome ast, Optional<CfgOutcome> cfg, List<AnalysisDiagnostic> diagnostics) {
    if (ast.result().isEmpty() || cfg.filter(value -> value.state() == CfgState.REJECTED).isPresent()) {
      return AnalysisCompletion.REJECTED;
    }
    if (cfg.stream().flatMap(value -> value.methods().stream()).anyMatch(value -> value.state() == MethodCfgState.FAILED)
        || cfg.stream().flatMap(value -> value.methods().stream()).filter(value -> value.state() == MethodCfgState.AVAILABLE)
            .flatMap(value -> value.diagnostics().stream()).anyMatch(AnalysisDiagnostic::isError)
        || diagnostics.stream().anyMatch(value -> value.severity() == DiagnosticSeverity.ERROR)) {
      return AnalysisCompletion.FAILED;
    }
    if (ast.completion() == AstCompletion.INCOMPLETE || cfg.filter(value -> value.state() == CfgState.PARTIAL).isPresent()) {
      return AnalysisCompletion.PARTIAL;
    }
    return AnalysisCompletion.COMPLETE;
  }
}
