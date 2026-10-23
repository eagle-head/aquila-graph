package org.aquilagraph.frontend.internal;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;

record TranslationResult<T>(T value, DiagnosticEmissions emissions) {
  TranslationResult {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(emissions, "emissions");
  }

  List<AnalysisDiagnostic> diagnostics() {
    return emissions.diagnostics();
  }
}
