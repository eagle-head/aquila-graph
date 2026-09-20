package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostic emitted during one analysis stage.
 *
 * @param diagnosticKey
 *          stable diagnostic identifier
 * @param code
 *          machine-readable diagnostic code
 * @param stage
 *          stage that produced the diagnostic
 * @param severity
 *          diagnostic severity
 * @param message
 *          human-readable diagnostic message
 * @param origins
 *          source locations associated with the diagnostic
 */
public record AnalysisDiagnostic(String diagnosticKey, String code, DiagnosticStage stage, DiagnosticSeverity severity, String message,
    List<SourceSpan> origins) {
  /** Validates and defensively copies diagnostic data. */
  public AnalysisDiagnostic {
    Objects.requireNonNull(diagnosticKey, "diagnosticKey");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
  }

  /**
   * Indicates whether this diagnostic prevents a complete result.
   *
   * @return {@code true} for error severity
   */
  public boolean isError() {
    return severity == DiagnosticSeverity.ERROR;
  }
}
