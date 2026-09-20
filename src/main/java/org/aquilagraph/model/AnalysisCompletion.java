package org.aquilagraph.model;

/** Classifies whether an analysis produced its complete representation. */
public enum AnalysisCompletion {
  /** All accepted method bodies were analyzed without error diagnostics. */
  COMPLETED,
  /** Some accepted program content could not be analyzed. */
  INCOMPLETE,
  /** The input could not produce an accepted program. */
  REJECTED
}
