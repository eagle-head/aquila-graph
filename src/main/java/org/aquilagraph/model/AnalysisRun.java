package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AnalysisRun(
    String runId,
    InputSnapshot inputSnapshot,
    AnalysisEnvironmentSnapshot environmentSnapshot,
    Optional<ProgramRepresentation> representation,
    List<AnalysisDiagnostic> diagnostics,
    AnalysisCompletion completion,
    String analyzerVersion,
    String modelVersion) {
  public static final String CURRENT_ANALYZER_VERSION = "0.2.0";
  public static final String CURRENT_MODEL_VERSION = "3";

  public AnalysisRun {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(inputSnapshot, "inputSnapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    representation = Objects.requireNonNull(representation, "representation");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    Objects.requireNonNull(completion, "completion");
    Objects.requireNonNull(analyzerVersion, "analyzerVersion");
    Objects.requireNonNull(modelVersion, "modelVersion");
    if (completion == AnalysisCompletion.COMPLETED && representation.isEmpty()) {
      throw new IllegalArgumentException("A completed run requires a program representation");
    }
    if (completion == AnalysisCompletion.REJECTED && representation.isPresent()) {
      throw new IllegalArgumentException(
          "A rejected run cannot contain an accepted representation");
    }
  }

  public boolean isComplete() {
    return completion == AnalysisCompletion.COMPLETED;
  }

  public CompilerConfiguration compilerConfiguration() {
    return environmentSnapshot.compilerConfiguration();
  }
}
