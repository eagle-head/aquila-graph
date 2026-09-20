package org.aquilagraph.model;

/** Identifies the captured form of a compiler dependency entry. */
public enum DependencyKind {
  /** A regular dependency file such as a JAR. */
  REGULAR_FILE,
  /** A directory whose regular files were captured recursively. */
  DIRECTORY,
  /** A configured dependency location that did not exist. */
  MISSING
}
