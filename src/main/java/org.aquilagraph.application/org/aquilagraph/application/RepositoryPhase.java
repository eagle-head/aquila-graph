package org.aquilagraph.application;

/** Observable resource or work boundary of an exact AST repository operation. */
public enum RepositoryPhase {
  /** Factory acquisition before resource ownership transfers. */
  ACQUISITION,
  /** Intrinsic argument, completion, and exact operation validation. */
  VALIDATION,
  /** Complete immutable projection or codec preparation before database effects. */
  MAPPING,
  /** Constraint creation and exact named-definition inspection. */
  SCHEMA,
  /** Closing the schema session before subject replacement. */
  SCHEMA_SESSION_CLOSE,
  /** Managed replacement of the exact AST scope. */
  REPLACEMENT,
  /** Closing the publication session with retained replacement knowledge. */
  PUBLICATION_SESSION_CLOSE,
  /** Reading one exact source subject. */
  READ,
  /** Intrinsic decoding and exact identity/completion validation. */
  DECODE,
  /** Closing the read session before returning any lookup. */
  READ_SESSION_CLOSE,
  /** Closing the acquired repository after work. */
  REPOSITORY_CLOSE
}
