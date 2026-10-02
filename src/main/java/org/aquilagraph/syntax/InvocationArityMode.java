package org.aquilagraph.syntax;

/** Compiler-selected invocation applicability, separate from graph dispatch mode. */
public enum InvocationArityMode {
  /** Each source argument supplies one selected formal, including an explicit array. */
  FIXED,
  /** Trailing source arguments supply elements of a synthetic variable-arity array. */
  VARIABLE
}
