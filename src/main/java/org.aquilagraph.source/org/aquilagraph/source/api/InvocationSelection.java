package org.aquilagraph.source.api;

/** Semantic operation selected by a method or constructor invocation. */
public enum InvocationSelection {
  /** A method or constructor with a named executable declaration. */
  NAMED_EXECUTABLE,
  /** The array clone operation, which has no named executable declaration. */
  ARRAY_CLONE
}
