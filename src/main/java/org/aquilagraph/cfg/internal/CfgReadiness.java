package org.aquilagraph.cfg.internal;

/** Consumer classification of prerequisites for the existing control-flow lowering profile. */
public enum CfgReadiness {
  /** Every retained construct and required compiler fact is available for lowering. */
  LOWERABLE,
  /** At least one complete source construct lies outside the accepted profile. */
  UNSUPPORTED,
  /** A required compiler element, declaration identity, or type is unavailable. */
  UNRESOLVED_SEMANTICS,
  /** javac supplied an erroneous type or reference rather than valid semantics. */
  ERRONEOUS_SEMANTICS
}
