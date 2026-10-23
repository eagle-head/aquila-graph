package org.aquilagraph.application;

/** Explicit storage intent independent of source and graph qualification. */
public enum PublicationIntent {
  /** Compute the requested result without acquiring a repository. */
  DO_NOT_SAVE,
  /** Retain an intrinsically complete AST independently of graph completion. */
  SAVE_COMPLETE_AST,
  /** Publish complete source with every required body graph. */
  SAVE_COMPLETED
}
