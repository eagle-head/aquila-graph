package org.aquilagraph.cfg.internal.values;

import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticStage;

/** Qualifies native CFG diagnostic occurrences by their creating stage. */
public final class CfgDiagnosticAdmission {
  private CfgDiagnosticAdmission() {
  }

  /**
   * Rejects diagnostics that do not belong to the creating domain stage.
   *
   * @param diagnostic
   *          diagnostic whose stage is checked
   */
  public static void requireCreatingStage(AnalysisDiagnostic diagnostic) {
    if (diagnostic.stage() != DiagnosticStage.CONTROL_FLOW) {
      throw new IllegalArgumentException("CFG diagnostics require the CONTROL_FLOW creating stage");
    }
  }
}
