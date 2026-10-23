package org.aquilagraph.source.api;

/** Source qualification independent of requested graph derivation. */
public enum AstCompletion {
  /** All required source contexts and evidence are available. */
  COMPLETE,
  /** A valid source aggregate retains explicit source or evidence gaps. */
  INCOMPLETE,
  /** No valid source result was established. */
  UNAVAILABLE
}
