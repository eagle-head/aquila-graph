package org.aquilagraph.application.port;

/** Opens repositories only when a completed analysis run must be stored. */
@FunctionalInterface
public interface AnalysisRunRepositoryFactory {
  /**
   * Opens a repository.
   *
   * @return open analysis-run repository
   * @throws RepositoryException
   *           if the repository cannot be opened
   */
  AnalysisRunRepository open();
}
