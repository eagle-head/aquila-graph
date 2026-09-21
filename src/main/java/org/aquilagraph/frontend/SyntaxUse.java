package org.aquilagraph.frontend;

/** Context in which a javac tree must be classified at the frontend boundary. */
enum SyntaxUse {
  /** The tree is evaluated for its value or effects. */
  VALUE,
  /** The tree identifies a storage location for simple assignment. */
  ASSIGNMENT_LOCATION
}
