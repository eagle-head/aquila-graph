package org.aquilagraph.application.port;

import org.aquilagraph.source.api.AstOutcome;

/** Caller-owned capability for one complete AST save. */
public interface AstRetentionRepository extends AutoCloseable {
  /**
   * Saves the exact intrinsically complete AST acquired for retention. An attempt consumes the operation even if subject validation fails.
   * Normal return acknowledges replacement and completion of the save's internal cleanup.
   *
   * @param outcome
   *          intrinsically complete source subject
   * @throws RepositoryOperationException
   *           exact operation failure with its effective primary
   */
  void saveComplete(AstOutcome outcome) throws RepositoryOperationException;

  @Override
  void close() throws RepositoryOperationException;
}
