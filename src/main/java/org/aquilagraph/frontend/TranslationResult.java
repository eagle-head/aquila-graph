package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.AnalysisDiagnostic;

record TranslationResult<T>(T value, List<AnalysisDiagnostic> diagnostics) {
  TranslationResult {
    Objects.requireNonNull(value, "value");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }
}
