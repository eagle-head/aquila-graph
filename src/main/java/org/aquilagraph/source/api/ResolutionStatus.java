package org.aquilagraph.source.api;

/** Outcome of resolving a Java semantic reference. */
public enum ResolutionStatus {
  /** The compiler resolved the referenced symbol. */
  RESOLVED,
  /** No compiler symbol was available for the reference. */
  UNRESOLVED,
  /** Resolution encountered an erroneous compiler symbol. */
  ERROR
}
