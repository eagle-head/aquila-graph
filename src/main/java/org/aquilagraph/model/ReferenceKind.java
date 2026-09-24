package org.aquilagraph.model;

/** Kind of Java symbol referenced by a syntax node. */
public enum ReferenceKind {
  /** Type reference. */
  TYPE,
  /** Constructor reference. */
  CONSTRUCTOR,
  /** Method reference. */
  METHOD,
  /** Field reference. */
  FIELD,
  /** Formal parameter reference. */
  PARAMETER,
  /** Local variable reference. */
  LOCAL,
  /** Enum constant reference. */
  ENUM_CONSTANT,
  /** Record component declaration reference. */
  RECORD_COMPONENT,
  /** Package reference. */
  PACKAGE
}
