package org.aquilagraph.application;

import org.aquilagraph.model.AnalysisRun;

/** Persistence port for publishing completed analysis runs. */
public interface AnalysisRepository extends AutoCloseable {
  /**
   * Persists one completed analysis run.
   *
   * @param run
   *          completed run
   * @return persisted node and relationship counts
   * @throws AnalysisRepositoryException
   *           if the adapter cannot persist the run
   */
  PersistenceReceipt persist(AnalysisRun run);

  @Override
  void close();
}
