package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Immutable diagnostic emitted during one analysis stage.
 *
 * The producer assigns the root-cause outcome while it still owns the semantic context that created the diagnostic.
 */
public final class AnalysisDiagnostic {
  private final String diagnosticIdentity;
  private final String machineCode;
  private final DiagnosticStage producingStage;
  private final DiagnosticSeverity diagnosticSeverity;
  private final String diagnosticMessage;
  private final List<SourceSpan> sourceOrigins;
  private final AnalysisOutcomeKind rootCause;

  /**
   * Creates a diagnostic with the root cause assigned by its producer.
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
   * @param outcomeKind
   *          typed root cause known by the producer
   */
  public AnalysisDiagnostic(String diagnosticKey, String code, DiagnosticStage stage, DiagnosticSeverity severity, String message,
      List<SourceSpan> origins, AnalysisOutcomeKind outcomeKind) {
    diagnosticIdentity = Objects.requireNonNull(diagnosticKey, "diagnosticKey");
    machineCode = Objects.requireNonNull(code, "code");
    producingStage = Objects.requireNonNull(stage, "stage");
    diagnosticSeverity = Objects.requireNonNull(severity, "severity");
    diagnosticMessage = Objects.requireNonNull(message, "message");
    sourceOrigins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    rootCause = Objects.requireNonNull(outcomeKind, "outcomeKind");
    validateOutcome();
  }

  /**
   * Returns the stable diagnostic identifier.
   *
   * @return diagnostic identity
   */
  public String diagnosticKey() {
    return diagnosticIdentity;
  }

  /**
   * Returns the machine-readable diagnostic code.
   *
   * @return diagnostic code
   */
  public String code() {
    return machineCode;
  }

  /**
   * Returns the stage that produced this diagnostic.
   *
   * @return producing stage
   */
  public DiagnosticStage stage() {
    return producingStage;
  }

  /**
   * Returns the diagnostic severity.
   *
   * @return severity
   */
  public DiagnosticSeverity severity() {
    return diagnosticSeverity;
  }

  /**
   * Returns the human-readable diagnostic message.
   *
   * @return message
   */
  public String message() {
    return diagnosticMessage;
  }

  /**
   * Returns the immutable source locations associated with this diagnostic.
   *
   * @return source locations
   */
  public List<SourceSpan> origins() {
    return sourceOrigins;
  }

  /**
   * Indicates whether this diagnostic prevents a complete result.
   *
   * @return {@code true} for error severity
   */
  public boolean isError() {
    return diagnosticSeverity == DiagnosticSeverity.ERROR;
  }

  /**
   * Returns the typed public root cause without requiring consumers to interpret diagnostic text.
   *
   * @return compiler-neutral outcome kind
   */
  public AnalysisOutcomeKind outcomeKind() {
    return rootCause;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof AnalysisDiagnostic diagnostic && diagnosticIdentity.equals(diagnostic.diagnosticIdentity)
        && machineCode.equals(diagnostic.machineCode) && producingStage == diagnostic.producingStage
        && diagnosticSeverity == diagnostic.diagnosticSeverity && diagnosticMessage.equals(diagnostic.diagnosticMessage)
        && sourceOrigins.equals(diagnostic.sourceOrigins) && rootCause == diagnostic.rootCause);
  }

  @Override
  public int hashCode() {
    return Objects.hash(diagnosticIdentity, machineCode, producingStage, diagnosticSeverity, diagnosticMessage, sourceOrigins, rootCause);
  }

  @Override
  public String toString() {
    return "AnalysisDiagnostic[diagnosticKey=" + diagnosticIdentity + ", code=" + machineCode + ", stage=" + producingStage + ", severity="
        + diagnosticSeverity + ", message=" + diagnosticMessage + ", origins=" + sourceOrigins + ", outcomeKind=" + rootCause + ']';
  }

  private void validateOutcome() {
    if (rootCause == AnalysisOutcomeKind.REPRESENTED_LANGUAGE_FEATURE || rootCause == AnalysisOutcomeKind.IRRELEVANT_LANGUAGE_FEATURE) {
      throw new IllegalArgumentException("Non-error language-feature outcomes cannot be attached to diagnostics");
    }
    if (rootCause == AnalysisOutcomeKind.COMPILER_ERROR && (producingStage != DiagnosticStage.FRONTEND || !isError())) {
      throw new IllegalArgumentException("Compiler-error outcomes must be frontend errors");
    }
    if (rootCause == AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE && (producingStage != DiagnosticStage.FRONTEND || !isError())) {
      throw new IllegalArgumentException("Unsupported-language outcomes must be frontend errors");
    }
    if (rootCause == AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE && (producingStage != DiagnosticStage.CONTROL_FLOW || !isError())) {
      throw new IllegalArgumentException("Control-flow-unavailable outcomes must be control-flow errors");
    }
  }
}
