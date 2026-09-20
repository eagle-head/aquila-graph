package org.aquilagraph.model;

/** Severity assigned to an analysis diagnostic. */
public enum DiagnosticSeverity {
  /** The condition prevents a complete result. */
  ERROR,
  /** The condition is notable but does not prevent completion. */
  WARNING,
  /** The condition provides informational context. */
  INFO
}
