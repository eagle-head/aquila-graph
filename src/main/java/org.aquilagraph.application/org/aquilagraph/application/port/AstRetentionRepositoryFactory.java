package org.aquilagraph.application.port;

import org.aquilagraph.application.RepositoryOperationContext;

/** Lazy acquisition transferring ownership of a complete AST save. */
@FunctionalInterface
public interface AstRetentionRepositoryFactory {
  /**
   * Acquires a save operation and cleans up partial resources before a failed transfer.
   *
   * @param context
   *          SAVE_AST and its exact source subject
   * @return acquired repository whose enclosing closure belongs to the caller
   * @throws RepositoryOperationException
   *           acquisition failure preserving partial cleanup evidence
   */
  AstRetentionRepository open(RepositoryOperationContext context) throws RepositoryOperationException;
}
