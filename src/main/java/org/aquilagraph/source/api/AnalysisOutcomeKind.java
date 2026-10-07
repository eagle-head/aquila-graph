package org.aquilagraph.source.api;

/** Compiler-neutral root-cause categories exposed by analysis outcomes. */
public enum AnalysisOutcomeKind {
  /** A language feature is retained at its declared project-owned boundary. */
  REPRESENTED_LANGUAGE_FEATURE,

  /** A language feature is deliberately outside the semantic contract. */
  IRRELEVANT_LANGUAGE_FEATURE,

  /** A valid language feature is not represented and has an exact capability diagnostic. */
  UNSUPPORTED_LANGUAGE_FEATURE,

  /** The Java compiler rejected source and retained its precise diagnostic identity. */
  COMPILER_ERROR,

  /** The compiler exposed an erroneous tree or attributed semantic state. */
  ERRONEOUS_COMPILER_STATE,

  /** Method-level control-flow analysis could not produce a graph. */
  CONTROL_FLOW_UNAVAILABLE,

  /** Analysis input was rejected before a program representation was accepted. */
  INPUT_REJECTION,

  /** Repository publication failed after analysis. */
  PERSISTENCE_FAILURE,

  /** An analysis error does not belong to a more specific public root-cause category. */
  OTHER_ANALYSIS_ERROR
}
