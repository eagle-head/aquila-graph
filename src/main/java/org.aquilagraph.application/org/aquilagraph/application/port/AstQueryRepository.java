package org.aquilagraph.application.port;

import org.aquilagraph.application.AstLookup;
import org.aquilagraph.identity.api.AstIdentity;

/** Caller-owned capability for one exact AST lookup, independent of graphs. */
public interface AstQueryRepository extends AutoCloseable {
  /**
   * Finds the exact source identity acquired for query. An attempt consumes the operation even if subject validation fails.
   *
   * @param identity
   *          exact requested source identity
   * @return normal supported, absent, or incompatible lookup after read-session closure
   * @throws RepositoryOperationException
   *           exact query failure with its effective primary
   */
  AstLookup find(AstIdentity identity) throws RepositoryOperationException;

  @Override
  void close() throws RepositoryOperationException;
}
