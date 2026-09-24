package org.aquilagraph.syntax;

/** Java declaration modifier reported by the compiler for source and attributed declarations. */
public enum JavaDeclarationModifier {
  /** Public access. */
  PUBLIC,
  /** Protected access. */
  PROTECTED,
  /** Private access. */
  PRIVATE,
  /** Abstract declaration. */
  ABSTRACT,
  /** Default interface method. */
  DEFAULT,
  /** Static declaration. */
  STATIC,
  /** Sealed declaration. */
  SEALED,
  /** Explicitly non-sealed declaration. */
  NON_SEALED,
  /** Final declaration. */
  FINAL,
  /** Transient field. */
  TRANSIENT,
  /** Volatile field. */
  VOLATILE,
  /** Synchronized method. */
  SYNCHRONIZED,
  /** Native method. */
  NATIVE,
  /** Strict floating-point declaration. */
  STRICTFP
}
