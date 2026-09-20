package org.aquilagraph.application;

/** Describes the persistence decision or result for an analysis execution. */
public enum PersistenceStatus {
  /** Persistence was not requested. */
  NOT_REQUESTED,
  /** Persistence was requested but the analysis was not eligible. */
  NOT_ATTEMPTED,
  /** Persistence completed successfully. */
  COMPLETED
}
