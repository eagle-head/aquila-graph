package org.aquilagraph.application;

/** Exact independently retained source operation. */
public enum RepositoryOperationKind {
  /** Publish one intrinsically complete AST result. */
  SAVE_AST,
  /** Query one exact AST identity without loading graphs. */
  QUERY_AST
}
