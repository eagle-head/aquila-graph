package org.aquilagraph.source.api;

/** Identifies how a retained method body relates to captured written source. */
public enum MethodBodyOrigin {
  /** The body root is the identical occurrence owned by the source AST. */
  SOURCE_AST,
  /** An actual attributed compiler body belongs to a declaration without a written body occurrence. */
  COMPILER_ATTRIBUTED_BODY,
  /** A written body cannot be published as a complete source subtree because a position is unavailable. */
  UNAVAILABLE_SOURCE_POSITION
}
