package org.aquilagraph.cfg.api;

import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticStage;

/** Qualifies native CFG diagnostic occurrences by their creating stage. */
final class CfgDiagnosticAdmission {
  private CfgDiagnosticAdmission() {
  }

  static void requireCreatingStage(AnalysisDiagnostic diagnostic) {
    if (diagnostic.stage() != DiagnosticStage.CONTROL_FLOW) {
      throw new IllegalArgumentException("CFG diagnostics require the CONTROL_FLOW creating stage");
    }
  }
}
