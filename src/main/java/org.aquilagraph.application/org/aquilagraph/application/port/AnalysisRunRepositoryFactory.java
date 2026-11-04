package org.aquilagraph.application.port;

import org.aquilagraph.application.RepositoryOperationContext;

/** Acquires completed-run storage only after publication eligibility is established. */
@FunctionalInterface
public interface AnalysisRunRepositoryFactory {
  /**
   * Transfers ownership of an acquired repository to its caller.
   *
   * @param context
   *          exact completed-run acquisition context
   * @return owned repository
   * @throws RepositoryOperationException
   *           acquisition failure preserving partial cleanup evidence
   */
  AnalysisRunRepository open(RepositoryOperationContext context) throws RepositoryOperationException;
}
