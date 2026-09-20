package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisRun;

/**
 * Combines an analysis result with the outcome of its optional persistence step.
 *
 * @param run
 *          completed, incomplete, or rejected analysis run
 * @param persistenceStatus
 *          persistence decision or outcome
 * @param persistenceReceipt
 *          receipt present only after successful persistence
 */
public record AnalysisExecution(AnalysisRun run, PersistenceStatus persistenceStatus, Optional<PersistenceReceipt> persistenceReceipt) {
  /** Validates the consistency between persistence status and receipt. */
  public AnalysisExecution {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(persistenceStatus, "persistenceStatus");
    Objects.requireNonNull(persistenceReceipt, "persistenceReceipt");
    if (persistenceStatus == PersistenceStatus.COMPLETED) {
      var receipt = persistenceReceipt.orElseThrow(() -> new IllegalArgumentException("Completed persistence requires a receipt"));
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

  /**
   * Creates an execution whose run is not eligible for persistence.
   *
   * @param run
   *          incomplete or rejected run
   * @return non-persisted execution
   */
  public static AnalysisExecution notEligibleForPersistence(AnalysisRun run) {
    return new AnalysisExecution(run, PersistenceStatus.NOT_ATTEMPTED, Optional.empty());
  }

  /**
   * Creates a completed execution for which persistence was not requested.
   *
   * @param run
   *          completed run
   * @return execution without a persistence attempt
   */
  public static AnalysisExecution withoutPersistenceRequest(AnalysisRun run) {
    return new AnalysisExecution(run, PersistenceStatus.NOT_REQUESTED, Optional.empty());
  }

  /**
   * Creates a successfully persisted execution.
   *
   * @param run
   *          completed run
   * @param receipt
   *          persistence receipt
   * @return persisted execution
   */
  public static AnalysisExecution persisted(AnalysisRun run, PersistenceReceipt receipt) {
    return new AnalysisExecution(run, PersistenceStatus.COMPLETED, Optional.of(receipt));
  }
}
