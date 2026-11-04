package org.aquilagraph.application;

/** Repository operation on an exact publication subject. */
public enum RepositoryOperationKind {
  /** Publish one intrinsically complete AST result. */
  SAVE_AST,
  /** Query one exact AST identity without loading graphs. */
  QUERY_AST,
  /** Publish one completed coordinated analysis run. */
  SAVE_COMPLETED_RUN
}
