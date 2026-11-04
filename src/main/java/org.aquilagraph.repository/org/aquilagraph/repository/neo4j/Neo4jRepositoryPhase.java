package org.aquilagraph.repository.neo4j;

/** Creating boundary of a Neo4j repository failure. */
public enum Neo4jRepositoryPhase {
  /** Settings, driver construction, or session configuration before transfer. */
  ACQUISITION,
  /** Argument, completion, or exact operation validation. */
  VALIDATION,
  /** Immutable projection preparation. */
  MAPPING,
  /** Schema session acquisition, constraint creation, or constraint inspection. */
  SCHEMA,
  /** Schema session closure before replacement. */
  SCHEMA_SESSION_CLOSE,
  /** Publication session acquisition or managed subject replacement. */
  REPLACEMENT,
  /** Publication session closure after a replacement attempt. */
  PUBLICATION_SESSION_CLOSE,
  /** Read session acquisition or exact lookup. */
  READ,
  /** Stored value decoding and admission. */
  DECODE,
  /** Read session closure before returning a lookup. */
  READ_SESSION_CLOSE,
  /** Driver closure before or after ownership transfer. */
  REPOSITORY_CLOSE
}
