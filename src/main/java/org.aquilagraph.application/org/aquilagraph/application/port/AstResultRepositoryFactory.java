package org.aquilagraph.application.port;

import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationException;

/** Lazy acquisition transferring one exact operation's repository ownership. */
@FunctionalInterface
public interface AstResultRepositoryFactory {
  /**
   * Acquires one operation and cleans up partial resources before a failed transfer.
   *
   * @param context
   *          exact subject and single operation
   * @return acquired repository whose enclosing closure belongs to the caller
   * @throws RepositoryOperationException
   *           acquisition failure preserving partial cleanup evidence
   */
  AstResultRepository open(RepositoryOperationContext context) throws RepositoryOperationException;
}
