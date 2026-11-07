package org.aquilagraph.cli;

import java.util.Objects;

/**
 * Detached adapter-specific observation, without exception or repository ownership.
 *
 * @param adapter
 *          raw adapter name
 * @param phase
 *          raw observed phase
 */
public record AdapterFailureDetail(String adapter, String phase) {
  /** Requires both descriptor components. */
  public AdapterFailureDetail {
    Objects.requireNonNull(adapter, "adapter");
    Objects.requireNonNull(phase, "phase");
  }
}
