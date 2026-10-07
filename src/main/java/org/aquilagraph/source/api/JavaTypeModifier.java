package org.aquilagraph.source.api;

/** Compiler-derived modifier applicable to a Java type declaration. */
public enum JavaTypeModifier {
  /** Public access. */
  PUBLIC,
  /** Protected member access. */
  PROTECTED,
  /** Private member access. */
  PRIVATE,
  /** Abstract declaration. */
  ABSTRACT,
  /** Static member declaration. */
  STATIC,
  /** Final declaration. */
  FINAL,
  /** Sealed declaration. */
  SEALED,
  /** Explicitly non-sealed declaration. */
  NON_SEALED,
  /** Strict floating-point declaration retained from source. */
  STRICTFP
}
