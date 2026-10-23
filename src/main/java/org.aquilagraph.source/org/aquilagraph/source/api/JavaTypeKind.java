package org.aquilagraph.source.api;

/** Supported Java type declaration categories. */
public enum JavaTypeKind {
  /** Class declaration. */
  CLASS,
  /** Interface declaration. */
  INTERFACE,
  /** Enum declaration. */
  ENUM,
  /** Record declaration. */
  RECORD,
  /** Annotation interface declaration. */
  ANNOTATION
}
