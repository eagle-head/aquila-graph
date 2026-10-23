package org.aquilagraph.source.api;

/** Selected static member access responsibility. */
public enum StaticMemberKind {
  /** Static field value read. */
  FIELD_READ,
  /** Static field value write. */
  FIELD_WRITE,
  /** Static method invocation. */
  METHOD_INVOKE
}
