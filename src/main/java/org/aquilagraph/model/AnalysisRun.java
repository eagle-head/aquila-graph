package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Versioned result of analyzing one immutable input snapshot.
 *
 * @param runId
 *          stable run identifier
 * @param inputSnapshot
 *          analyzed source snapshot
 * @param environmentSnapshot
 *          compiler environment snapshot
 * @param representation
 *          program representation when one was produced
 * @param diagnostics
 *          ordered analysis diagnostics
 * @param completion
 *          completion classification
 * @param analyzerVersion
 *          analyzer implementation version
 * @param modelVersion
 *          stored model version
 */
public record AnalysisRun(String runId, InputSnapshot inputSnapshot, AnalysisEnvironmentSnapshot environmentSnapshot,
    Optional<ProgramRepresentation> representation, List<AnalysisDiagnostic> diagnostics, AnalysisCompletion completion,
    String analyzerVersion, String modelVersion) {
  /** Analyzer version written into newly created runs. */
  public static final String CURRENT_ANALYZER_VERSION = "0.5.0";
  /** Model version written into newly created runs. */
  public static final String CURRENT_MODEL_VERSION = "7";

  /** Validates run invariants and defensively copies diagnostics. */
  public AnalysisRun {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(inputSnapshot, "inputSnapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    Objects.requireNonNull(representation, "representation");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    Objects.requireNonNull(completion, "completion");
    Objects.requireNonNull(analyzerVersion, "analyzerVersion");
    Objects.requireNonNull(modelVersion, "modelVersion");
    if (completion == AnalysisCompletion.COMPLETED && representation.isEmpty()) {
      throw new IllegalArgumentException("A completed run requires a program representation");
    }
    if (completion == AnalysisCompletion.REJECTED && representation.isPresent()) {
      throw new IllegalArgumentException("A rejected run cannot contain an accepted representation");
    }
  }

  /**
   * Indicates whether the run is complete and eligible for repository storage.
   *
   * @return {@code true} only for completed runs
   */
  public boolean isComplete() {
    return completion == AnalysisCompletion.COMPLETED;
  }

  /**
   * Returns the compiler configuration captured for the run.
   *
   * @return compiler configuration
   */
  public CompilerConfiguration compilerConfiguration() {
    return environmentSnapshot.compilerConfiguration();
  }

  /**
   * Returns the content-addressed version of the captured software target.
   *
   * @return complete software-version signature and its component identities
   */
  public SoftwareVersion softwareVersion() {
    return SoftwareVersion.from(inputSnapshot, environmentSnapshot);
  }
}
