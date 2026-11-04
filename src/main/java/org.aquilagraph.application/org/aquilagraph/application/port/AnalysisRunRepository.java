package org.aquilagraph.application.port;

import org.aquilagraph.application.AnalysisRun;

/** Owns storage of completed native source and control-flow analyses, one save at a time. */
public interface AnalysisRunRepository extends AutoCloseable {
  /**
   * Replaces the exact established run. Normal return acknowledges replacement and completion of the save's internal cleanup.
   *
   * @param run
   *          intrinsically qualified complete analysis
   * @throws RepositoryOperationException
   *           exact save failure with observed replacement knowledge and effective primary
   */
  void save(AnalysisRun run) throws RepositoryOperationException;

  /**
   * Closes the owned repository resources.
   *
   * @throws RepositoryOperationException
   *           closure failure with the latest save observation, or the acquisition context before any save
   */
  @Override
  void close() throws RepositoryOperationException;
}
