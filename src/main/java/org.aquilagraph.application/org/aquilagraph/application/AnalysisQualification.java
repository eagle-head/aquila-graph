package org.aquilagraph.application;

import java.util.List;
import java.util.ArrayList;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.DiagnosticSeverity;

/** Application-owned combination of independently established context outcomes. */
final class AnalysisQualification {
  private AnalysisQualification() {
  }

  static RequestQualification assess(ProgramRepresentation representation, List<AnalysisDiagnostic> diagnostics) {
    var ast = representation.ast();
    var cfg = representation.cfg();
    var reasons = new ArrayList<RequestQualificationReason>();
    if (ast.completion() != AstCompletion.COMPLETE) {
      reasons.add(new RequestQualificationReason.Source(ast));
    }
    cfg.ifPresent(outcome -> {
      if (outcome.state() == CfgState.REJECTED) {
        reasons.add(new RequestQualificationReason.Selection(outcome));
      }
      for (var method : outcome.methods()) {
        if (method.state() != MethodCfgState.AVAILABLE && method.state() != MethodCfgState.NOT_APPLICABLE) {
          reasons.add(new RequestQualificationReason.Method(method));
        }
        if (method.state() == MethodCfgState.AVAILABLE || method.state() == MethodCfgState.NOT_APPLICABLE) {
          for (var position = 0; position < method.diagnostics().size(); position++) {
            if (method.diagnostics().get(position).isError()) {
              reasons.add(new RequestQualificationReason.MethodDiagnostic(method, position));
            }
          }
        }
      }
    });
    for (var position = 0; position < diagnostics.size(); position++) {
      if (diagnostics.get(position).severity() == DiagnosticSeverity.ERROR) {
        reasons.add(new RequestQualificationReason.ApplicationDiagnostic(diagnostics, position));
      }
    }
    var completion = AnalysisCompletion.COMPLETE;
    if (ast.completion() == AstCompletion.INCOMPLETE || cfg.filter(value -> value.state() == CfgState.PARTIAL).isPresent()) {
      completion = AnalysisCompletion.PARTIAL;
    }
    if (reasons.stream()
        .anyMatch(reason -> reason instanceof RequestQualificationReason.ApplicationDiagnostic
            || (reason instanceof RequestQualificationReason.MethodDiagnostic diagnostic
                && diagnostic.outcome().state() == MethodCfgState.AVAILABLE)
            || (reason instanceof RequestQualificationReason.Method method && method.outcome().state() == MethodCfgState.FAILED))) {
      completion = AnalysisCompletion.FAILED;
    }
    if (ast.result().isEmpty() || cfg.filter(value -> value.state() == CfgState.REJECTED).isPresent()) {
      completion = AnalysisCompletion.REJECTED;
    }
    return new RequestQualification(representation, diagnostics, completion, reasons);
  }
}
