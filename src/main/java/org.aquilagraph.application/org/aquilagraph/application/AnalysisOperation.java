package org.aquilagraph.application;

/** Semantic operation represented by an application request identity. */
public enum AnalysisOperation {
  /** Produce and inspect source without requesting control flow. */
  AST_ONLY,
  /** Produce source and bind the preserved graph selection afterward. */
  PRODUCTION_WITH_CFG,
  /** Derive a caller-bound selection from an already established source outcome. */
  DERIVE_EXISTING
}
