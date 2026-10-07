package org.aquilagraph.application.port;

/** Acquires completed-run storage only after publication eligibility is established. */
@FunctionalInterface
public interface AnalysisRunRepositoryFactory {
  /**
   * Transfers ownership of an acquired repository to its caller.
   *
   * @return owned repository
   */
  AnalysisRunRepository open();
}
