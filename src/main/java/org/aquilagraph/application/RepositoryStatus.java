package org.aquilagraph.application;

import org.aquilagraph.model.AnalysisRun;

/** Describes the repository decision or result for an analysis execution. */
public enum RepositoryStatus {
  /** Repository storage was not requested. */
  NOT_REQUESTED("Repository storage can be not requested only for a completed run with no-save policy") {
    @Override
    boolean isCompatibleWith(AnalysisRun run, RepositorySavePolicy policy) {
      return run.isComplete() && policy == RepositorySavePolicy.DO_NOT_SAVE;
    }
  },
  /** The analysis result was not eligible for repository storage. */
  NOT_ELIGIBLE("A completed analysis run is eligible for a repository decision") {
    @Override
    boolean isCompatibleWith(AnalysisRun run, RepositorySavePolicy policy) {
      return !run.isComplete();
    }
  },
  /** The completed analysis run was saved. */
  SAVED("Repository storage requires a completed run with save-completed policy") {
    @Override
    boolean isCompatibleWith(AnalysisRun run, RepositorySavePolicy policy) {
      return run.isComplete() && policy == RepositorySavePolicy.SAVE_COMPLETED;
    }
  };

  private final String validationFailureMessage;

  RepositoryStatus(String incompatibleStateMessage) {
    validationFailureMessage = incompatibleStateMessage;
  }

  abstract boolean isCompatibleWith(AnalysisRun run, RepositorySavePolicy policy);

  String incompatibleStateMessage() {
    return validationFailureMessage;
  }
}
