package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.model.AnalysisRun;

public final class AnalysisPersistenceException extends RuntimeException {
  private final AnalysisRun analysisRun;

  public AnalysisPersistenceException(AnalysisRun analysisRun, RuntimeException cause) {
    super(
        "Persistence failed for analysis run "
            + Objects.requireNonNull(analysisRun, "analysisRun").runId(),
        Objects.requireNonNull(cause, "cause"));
    this.analysisRun = analysisRun;
  }

  public AnalysisRun analysisRun() {
    return analysisRun;
  }
}
