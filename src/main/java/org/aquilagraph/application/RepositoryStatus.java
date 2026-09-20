package org.aquilagraph.application;

/** Describes the repository decision or result for an analysis execution. */
public enum RepositoryStatus {
  /** Repository storage was not requested. */
  NOT_REQUESTED,
  /** Repository storage was requested but the analysis was not eligible. */
  NOT_ATTEMPTED,
  /** The completed analysis run was saved. */
  SAVED
}
