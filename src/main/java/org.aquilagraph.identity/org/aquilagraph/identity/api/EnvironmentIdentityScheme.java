package org.aquilagraph.identity.api;

/** Closed versions of captured compiler-environment identity. */
public enum EnvironmentIdentityScheme {
  /** Original physical-content identity with no observed module semantics. */
  HISTORICAL,
  /** Physical content together with compiler-effective module observations. */
  MODULE_SEMANTICS;

  /**
   * Returns the explicit semantic version of this scheme.
   *
   * @return environment identity scheme
   */
  public String version() {
    return this == HISTORICAL ? "analysis-environment/1" : "analysis-environment/2";
  }
}
