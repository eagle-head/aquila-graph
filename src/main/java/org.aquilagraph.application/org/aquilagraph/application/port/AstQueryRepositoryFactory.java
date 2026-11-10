package org.aquilagraph.application.port;

import org.aquilagraph.application.RepositoryOperationContext;

/** Lazy acquisition transferring ownership of an exact AST lookup. */
@FunctionalInterface
public interface AstQueryRepositoryFactory {
  /**
   * Acquires a query operation and cleans up partial resources before a failed transfer.
   *
   * @param context
   *          QUERY_AST and its exact source subject
   * @return acquired repository whose enclosing closure belongs to the caller
   * @throws RepositoryOperationException
   *           acquisition failure preserving partial cleanup evidence
   */
  AstQueryRepository open(RepositoryOperationContext context) throws RepositoryOperationException;
}
