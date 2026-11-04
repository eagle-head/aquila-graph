package org.aquilagraph.application.port;

import org.aquilagraph.application.AstLookup;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstOutcome;

/** Single-operation complete source retention and exact lookup, independent of graphs. */
public interface AstResultRepository extends AutoCloseable {
  /**
   * Saves one exact intrinsically complete source outcome under the acquired SAVE_AST context. Normal return acknowledges replacement and
   * completion of the save's internal cleanup.
   *
   * @param outcome
   *          intrinsically complete source subject
   * @throws RepositoryOperationException
   *           exact operation failure with its effective primary
   */
  void saveComplete(AstOutcome outcome) throws RepositoryOperationException;

  /**
   * Finds one exact source identity under the acquired QUERY_AST context.
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
