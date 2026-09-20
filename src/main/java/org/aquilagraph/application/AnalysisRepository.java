package org.aquilagraph.application;

import org.aquilagraph.model.AnalysisRun;

public interface AnalysisRepository extends AutoCloseable {
  PersistenceReceipt persist(AnalysisRun run);

  @Override
  void close();
}
