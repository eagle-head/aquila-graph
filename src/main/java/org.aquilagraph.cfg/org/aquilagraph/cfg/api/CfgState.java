package org.aquilagraph.cfg.api;

/** Qualification of one exact control-flow request. */
public enum CfgState {
  /** Identity, compatibility, or selection is invalid. */
  REJECTED,
  /** Every selected declaration is available or bodyless. */
  COMPLETE,
  /** At least one selected body is refused or construction failed. */
  PARTIAL
}
