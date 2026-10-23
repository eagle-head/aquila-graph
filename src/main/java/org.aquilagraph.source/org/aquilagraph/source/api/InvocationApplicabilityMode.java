package org.aquilagraph.source.api;

/** Compiler-selected invocation applicability, separate from graph dispatch mode. */
public enum InvocationApplicabilityMode {
  /** Each source argument supplies one selected formal, including an explicit array. */
  FIXED,
  /** Trailing source arguments supply elements of a synthetic variable-arity array. */
  VARIABLE
}
