package org.aquilagraph.application.port;

import org.aquilagraph.model.AnalysisRun;

/** Stores completed analysis runs without exposing a persistence technology. */
public interface AnalysisRunRepository extends AutoCloseable {
  /**
   * Stores one completed analysis run.
   *
   * @param run
   *          completed analysis run
   * @throws IllegalArgumentException
   *           if the run is not eligible for storage
   * @throws RepositoryException
   *           if the repository cannot store the run
   */
  void save(AnalysisRun run);

  /** Closes resources owned by this repository. */
  @Override
  void close();
}
