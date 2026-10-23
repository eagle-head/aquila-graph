package org.aquilagraph.source.api;

/** Compiler-independent categories used to identify Java value types in analysis results. */
public enum JavaTypeCategory {
  /** Primitive boolean type. */
  BOOLEAN,
  /** Primitive byte type. */
  BYTE,
  /** Primitive short type. */
  SHORT,
  /** Primitive int type. */
  INT,
  /** Primitive long type. */
  LONG,
  /** Primitive char type. */
  CHAR,
  /** Primitive float type. */
  FLOAT,
  /** Primitive double type. */
  DOUBLE,
  /** Void type. */
  VOID,
  /** Null type. */
  NULL,
  /** Array type. */
  ARRAY,
  /** Declared class, interface, enum, annotation, or record type. */
  DECLARED,
  /** Compiler error type that still retains an identity. */
  ERROR,
  /** Type-variable type. */
  TYPE_VARIABLE,
  /** Wildcard type. */
  WILDCARD,
  /** Executable type. */
  EXECUTABLE,
  /** Union type. */
  UNION,
  /** Intersection type. */
  INTERSECTION,
  /** Compiler type outside the explicitly classified categories. */
  OTHER
}
