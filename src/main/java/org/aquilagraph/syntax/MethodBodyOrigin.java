package org.aquilagraph.syntax;

/** Identifies how a core-facing body relates to captured source occurrences. */
public enum MethodBodyOrigin {
  /** The body root is the identical occurrence owned by the source AST. */
  SOURCE_AST,
  /** Core-facing execution projection differs from the written source occurrence tree. */
  EXECUTION_PROJECTION,
  /** A written body cannot be published as a complete source subtree because a position is unavailable. */
  UNAVAILABLE_SOURCE_POSITION
}
