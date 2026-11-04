package org.aquilagraph.application;

import org.aquilagraph.application.port.RepositoryOperationException;
import org.aquilagraph.identity.api.AstIdentity;

/** Queries retained source by exact identity. */
@FunctionalInterface
public interface AstQuery {
  /**
   * Queries without producing source or loading CFG and closes the repository before normal return.
   *
   * @param identity
   *          exact requested source identity
   * @return found, absent, or incompatible lookup
   * @throws RepositoryOperationException
   *           if acquisition, lookup, result validation, or closure fails
   */
  AstLookup findAst(AstIdentity identity);
}
