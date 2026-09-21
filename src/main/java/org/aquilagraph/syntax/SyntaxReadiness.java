package org.aquilagraph.syntax;

/** Derived readiness of transient method syntax for direct semantic graph lowering. */
public enum SyntaxReadiness {
  /** Every retained construct and required compiler fact is available for lowering. */
  LOWERABLE,
  /** At least one complete source construct lies outside the accepted profile. */
  UNSUPPORTED,
  /** A required compiler element, declaration identity, or type is unavailable. */
  UNRESOLVED_SEMANTICS,
  /** javac supplied an erroneous type or reference rather than valid semantics. */
  ERRONEOUS_SEMANTICS
}
