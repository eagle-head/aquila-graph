package org.aquilagraph.application;

import java.util.Objects;

public record PersistenceReceipt(String runId, int nodeCount, int relationshipCount) {
  public PersistenceReceipt {
    Objects.requireNonNull(runId, "runId");
    if (nodeCount < 0 || relationshipCount < 0) {
      throw new IllegalArgumentException("Persisted counts cannot be negative");
    }
  }
}
