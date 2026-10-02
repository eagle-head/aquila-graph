package org.aquilagraph.syntax;

/** Selected static member access responsibility. */
public enum StaticAccessKind {
  /** Static field value read. */
  FIELD_READ,
  /** Static field value write. */
  FIELD_WRITE,
  /** Static method invocation. */
  METHOD_INVOKE
}
