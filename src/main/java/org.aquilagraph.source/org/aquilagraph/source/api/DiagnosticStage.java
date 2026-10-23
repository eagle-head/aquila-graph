package org.aquilagraph.source.api;

/** Processing stage that produced an analysis diagnostic. */
public enum DiagnosticStage {
  /** Input discovery and snapshot capture. */
  INPUT,
  /** Java compiler parsing and semantic translation. */
  FRONTEND,
  /** Control-flow graph construction. */
  CONTROL_FLOW,
  /** Publication of a completed run. */
  PERSISTENCE
}
