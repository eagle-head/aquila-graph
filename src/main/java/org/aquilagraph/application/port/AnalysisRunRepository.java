package org.aquilagraph.application.port;

import org.aquilagraph.application.AnalysisRun;

/** Owns storage of one completed native source and control-flow analysis. */
public interface AnalysisRunRepository extends AutoCloseable {
  /**
   * Stores the exact established run without producing or translating domain results.
   *
   * @param run
   *          intrinsically qualified complete analysis
   */
  void save(AnalysisRun run);

  /** Closes the owned repository resources. */
  @Override
  void close();
}
