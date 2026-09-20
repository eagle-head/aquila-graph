package org.aquilagraph.application;

import java.util.Objects;

/**
 * Summarizes the graph elements published for an analysis run.
 *
 * @param runId
 *          persisted analysis run identifier
 * @param nodeCount
 *          number of graph nodes written
 * @param relationshipCount
 *          number of graph relationships written
 */
public record PersistenceReceipt(String runId, int nodeCount, int relationshipCount) {
  /** Validates persisted identifiers and element counts. */
  public PersistenceReceipt {
    Objects.requireNonNull(runId, "runId");
    if (nodeCount < 0 || relationshipCount < 0) {
      throw new IllegalArgumentException("Persisted counts cannot be negative");
    }
  }
}
