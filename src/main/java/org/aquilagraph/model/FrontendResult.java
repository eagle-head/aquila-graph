package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

public record FrontendResult(
    InputSnapshot snapshot,
    AnalysisEnvironmentSnapshot environmentSnapshot,
    JavaProgram program,
    List<AnalysisDiagnostic> diagnostics) {
  public FrontendResult {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    Objects.requireNonNull(program, "program");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }

  public CompilerConfiguration compilerConfiguration() {
    return environmentSnapshot.compilerConfiguration();
  }
}
