package org.aquilagraph.frontend;

import java.util.List;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticEmission;

/** Retains original creating-site addresses while diagnostic values move between translators. */
interface DiagnosticEmissionTransport extends List<AnalysisDiagnostic> {
  List<DiagnosticEmission> emissions();

  List<AnalysisDiagnostic> immutableCopy();

  boolean emitDiagnostic(String creatingSite, AnalysisDiagnostic diagnostic);
}
