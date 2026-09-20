package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.model.AnalysisRun;

/**
 * Combines an analysis result with the outcome of its optional repository step.
 *
 * @param run
 *          completed, incomplete, or rejected analysis run
 * @param repositoryStatus
 *          repository decision or outcome
 */
public record AnalysisExecution(AnalysisRun run, RepositoryStatus repositoryStatus) {
  /** Validates consistency between analysis completion and repository status. */
  public AnalysisExecution {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(repositoryStatus, "repositoryStatus");
    if (repositoryStatus == RepositoryStatus.SAVED && !run.isComplete()) {
      throw new IllegalArgumentException("Only a completed analysis run can be saved");
    }
    if (repositoryStatus == RepositoryStatus.NOT_REQUESTED && !run.isComplete()) {
      throw new IllegalArgumentException("An incomplete run cannot report repository storage as not requested");
    }
    if (repositoryStatus == RepositoryStatus.NOT_ATTEMPTED && run.isComplete()) {
      throw new IllegalArgumentException("A completed run must report a repository decision");
    }
  }

  /**
   * Creates an execution whose run is not eligible for repository storage.
   *
   * @param run
   *          incomplete or rejected run
   * @return execution without a repository attempt
   */
  public static AnalysisExecution notEligibleForRepository(AnalysisRun run) {
    return new AnalysisExecution(run, RepositoryStatus.NOT_ATTEMPTED);
  }

  /**
   * Creates a completed execution for which repository storage was not requested.
   *
   * @param run
   *          completed run
   * @return execution without a repository attempt
   */
  public static AnalysisExecution withoutRepositoryRequest(AnalysisRun run) {
    return new AnalysisExecution(run, RepositoryStatus.NOT_REQUESTED);
  }

  /**
   * Creates an execution whose completed run was saved.
   *
   * @param run
   *          completed run
   * @return saved execution
   */
  public static AnalysisExecution saved(AnalysisRun run) {
    return new AnalysisExecution(run, RepositoryStatus.SAVED);
  }
}
