package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisRun;

public record AnalysisExecution(
    AnalysisRun run,
    PersistenceStatus persistenceStatus,
    Optional<PersistenceReceipt> persistenceReceipt) {
  public AnalysisExecution {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(persistenceStatus, "persistenceStatus");
    persistenceReceipt = Objects.requireNonNull(persistenceReceipt, "persistenceReceipt");
    if (persistenceStatus == PersistenceStatus.COMPLETED) {
      var receipt =
          persistenceReceipt.orElseThrow(
              () -> new IllegalArgumentException("Completed persistence requires a receipt"));
      if (!run.isComplete()) {
        throw new IllegalArgumentException("Only a completed analysis run can be persisted");
      }
      if (!run.runId().equals(receipt.runId())) {
        throw new IllegalArgumentException("Persistence receipt must identify the analysis run");
      }
    } else if (persistenceReceipt.isPresent()) {
      throw new IllegalArgumentException("A persistence receipt requires completed persistence");
    }
    if (persistenceStatus == PersistenceStatus.NOT_REQUESTED && !run.isComplete()) {
      throw new IllegalArgumentException("An incomplete run cannot report persistence as not requested");
    }
    if (persistenceStatus == PersistenceStatus.NOT_ATTEMPTED && run.isComplete()) {
      throw new IllegalArgumentException("A completed run must report a persistence decision");
    }
  }

  public static AnalysisExecution notEligibleForPersistence(AnalysisRun run) {
    return new AnalysisExecution(run, PersistenceStatus.NOT_ATTEMPTED, Optional.empty());
  }

  public static AnalysisExecution withoutPersistenceRequest(AnalysisRun run) {
    return new AnalysisExecution(run, PersistenceStatus.NOT_REQUESTED, Optional.empty());
  }

  public static AnalysisExecution persisted(AnalysisRun run, PersistenceReceipt receipt) {
    return new AnalysisExecution(run, PersistenceStatus.COMPLETED, Optional.of(receipt));
  }
}
