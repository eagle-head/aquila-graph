package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable output of parsing and translating one source snapshot.
 *
 * @param snapshot
 *          analyzed source snapshot
 * @param environmentSnapshot
 *          captured compiler environment
 * @param program
 *          translated Java program
 * @param diagnostics
 *          frontend diagnostics
 */
public record FrontendResult(InputSnapshot snapshot, AnalysisEnvironmentSnapshot environmentSnapshot, JavaProgram program,
    List<AnalysisDiagnostic> diagnostics) {
  /** Validates and defensively copies frontend data. */
  public FrontendResult {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    Objects.requireNonNull(program, "program");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }

  /**
   * Returns the compiler configuration used by the frontend.
   *
   * @return compiler configuration
   */
  public CompilerConfiguration compilerConfiguration() {
    return environmentSnapshot.compilerConfiguration();
  }
}
