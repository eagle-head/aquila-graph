package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

public record AnalysisDiagnostic(
    String diagnosticKey,
    String code,
    DiagnosticStage stage,
    DiagnosticSeverity severity,
    String message,
    List<SourceSpan> origins) {
  public AnalysisDiagnostic {
    Objects.requireNonNull(diagnosticKey, "diagnosticKey");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
  }

  public boolean isError() {
    return severity == DiagnosticSeverity.ERROR;
  }
}
