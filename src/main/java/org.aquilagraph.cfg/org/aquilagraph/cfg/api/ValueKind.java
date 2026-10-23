package org.aquilagraph.cfg.api;

/** Origin or role of a value in a control-flow graph. */
public enum ValueKind {
  /** Method parameter value. */
  PARAMETER,
  /** Compiler-supplied incoming construction value with no source parameter declaration. */
  IMPLICIT_PARAMETER,
  /** Local variable value. */
  LOCAL,
  /** Intermediate operation result. */
  TEMPORARY,
  /** Literal constant value. */
  CONSTANT,
  /** Exception handler value. */
  EXCEPTION,
  /** Implicit or explicit Java receiver value. */
  RECEIVER,
  /** Referenced symbol without a local binding. */
  SYMBOL
}
