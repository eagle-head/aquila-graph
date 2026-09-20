package org.aquilagraph.application;

/** Declares whether a completed analysis run should be stored. */
public enum RepositorySavePolicy {
  /** Complete the analysis without storing its result. */
  DO_NOT_SAVE,
  /** Store the result only when the analysis completes successfully. */
  SAVE_COMPLETED
}
