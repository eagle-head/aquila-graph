package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.model.AnalysisRun;

/**
 * Combines an analysis result with the outcome of its optional repository step.
 *
 * @param run
 *          completed, incomplete, or rejected analysis run
 * @param repositorySavePolicy
 *          repository intent retained from the request
 * @param repositoryStatus
 *          repository decision or outcome
 */
public record AnalysisExecution(AnalysisRun run, RepositorySavePolicy repositorySavePolicy, RepositoryStatus repositoryStatus) {
  /** Validates consistency among analysis completion, repository intent, and repository outcome. */
  public AnalysisExecution {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(repositorySavePolicy, "repositorySavePolicy");
    Objects.requireNonNull(repositoryStatus, "repositoryStatus");
    validateRepositoryState(run, repositorySavePolicy, repositoryStatus);
  }

  /**
   * Creates an execution whose run is not eligible for repository storage.
   *
   * @param run
   *          incomplete or rejected run
   * @param repositorySavePolicy
   *          repository intent retained from the request
   * @return execution without a repository attempt
   */
  public static AnalysisExecution notEligibleForRepository(AnalysisRun run, RepositorySavePolicy repositorySavePolicy) {
    return new AnalysisExecution(run, repositorySavePolicy, RepositoryStatus.NOT_ELIGIBLE);
  }

  /**
   * Creates a completed execution for which repository storage was not requested.
   *
   * @param run
   *          completed run
   * @return execution without a repository attempt
   */
  public static AnalysisExecution withoutRepositoryRequest(AnalysisRun run) {
    return new AnalysisExecution(run, RepositorySavePolicy.DO_NOT_SAVE, RepositoryStatus.NOT_REQUESTED);
  }

  /**
   * Creates an execution whose completed run was saved.
   *
   * @param run
   *          completed run
   * @return saved execution
   */
  public static AnalysisExecution saved(AnalysisRun run) {
    return new AnalysisExecution(run, RepositorySavePolicy.SAVE_COMPLETED, RepositoryStatus.SAVED);
  }

  private static void validateRepositoryState(AnalysisRun run, RepositorySavePolicy policy, RepositoryStatus status) {
    if (!status.isCompatibleWith(run, policy)) {
      throw new IllegalArgumentException(status.incompatibleStateMessage());
    }
  }
}
