package org.aquilagraph.application;

/** Conservative knowledge of replacement of the exact AST subject, excluding schema effects. */
public enum CommitKnowledge {
  /** No subject replacement has started. */
  NOT_STARTED,
  /** Whole-subject noncommit was authoritatively established. */
  KNOWN_NONCOMMIT,
  /** Replacement was attempted without conclusive whole-operation acknowledgement. */
  UNKNOWN,
  /** Whole-subject commit was acknowledged and survives later cleanup failure. */
  KNOWN_COMMIT
}
