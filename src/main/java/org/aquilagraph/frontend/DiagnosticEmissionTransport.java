package org.aquilagraph.frontend;

import java.util.List;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticEmission;

/** Retains original creating-site addresses while diagnostic values move between translators. */
interface DiagnosticEmissionTransport extends List<AnalysisDiagnostic> {
  List<DiagnosticEmission> emissions();

  List<AnalysisDiagnostic> immutableCopy();

  boolean emitDiagnostic(String creatingSite, AnalysisDiagnostic diagnostic);
}
