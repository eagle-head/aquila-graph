package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticStage;
/** Qualifies native source diagnostic occurrences by their creating stage. */
public final class SourceDiagnosticAdmission {
  private SourceDiagnosticAdmission() {
  }

  /**
   * Rejects diagnostics that do not belong to the creating domain stage.
   *
   * @param diagnostic
   *          diagnostic whose stage is checked
   */
  public static void requireCreatingStage(AnalysisDiagnostic diagnostic) {
    if (diagnostic.stage() != DiagnosticStage.INPUT && diagnostic.stage() != DiagnosticStage.FRONTEND) {
      throw new IllegalArgumentException("Source diagnostics require the INPUT or FRONTEND creating stage");
    }
  }
}
