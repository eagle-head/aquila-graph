package org.aquilagraph.application;

/** Application coverage of a canonical declaration and its explicit selection. */
public enum MethodCoverageState {
  /** The canonical declaration was not selected for derivation. */
  NOT_REQUESTED,
  /** The selected request was rejected before per-method derivation. */
  REQUEST_REJECTED,
  /** The selected canonical declaration has no body. */
  NOT_APPLICABLE,
  /** A validated graph is available. */
  AVAILABLE,
  /** Required source body facts are unavailable. */
  SOURCE_UNAVAILABLE,
  /** The represented construct is outside the selected graph profile. */
  UNSUPPORTED,
  /** Graph-required facts are erroneous. */
  ERRONEOUS_EVIDENCE,
  /** Graph-required facts are unavailable. */
  INSUFFICIENT_EVIDENCE,
  /** Admitted construction failed through its declared recoverable family. */
  FAILED
}
