package org.aquilagraph.application;

/** Qualification of source availability and the explicitly selected derivation. */
public enum AnalysisCompletion {
  /** The source is unavailable or the requested derivation is rejected. */
  REJECTED,
  /** A returned declared construction or application failure prevents completion. */
  FAILED,
  /** Source qualification is incomplete or at least one selected body is refused. */
  PARTIAL,
  /** Source and every requested derivation are complete. */
  COMPLETE
}
