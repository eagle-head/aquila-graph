package org.aquilagraph.application;

/** Creates persistence adapters at the point a completed run needs publication. */
@FunctionalInterface
public interface AnalysisRepositoryFactory {
  /**
   * Opens a repository adapter.
   *
   * @return open repository
   * @throws AnalysisRepositoryException
   *           if the adapter cannot be opened
   */
  AnalysisRepository open();
}
