package org.aquilagraph.source.api;

/** Qualifies native source diagnostic occurrences by their creating stage. */
final class SourceDiagnosticAdmission {
  private SourceDiagnosticAdmission() {
  }

  static void requireCreatingStage(AnalysisDiagnostic diagnostic) {
    if (diagnostic.stage() != DiagnosticStage.INPUT && diagnostic.stage() != DiagnosticStage.FRONTEND) {
      throw new IllegalArgumentException("Source diagnostics require the INPUT or FRONTEND creating stage");
    }
  }
}
