package org.aquilagraph.source.api;

/** Availability of one independently retained source fact slot. */
public enum EvidenceState {
  /** A copied fact value is available. */
  KNOWN,
  /** The applicable fact could not be established. */
  MISSING,
  /** Attribution established an erroneous semantic state. */
  ERRONEOUS,
  /** This fact slot does not apply to its source context. */
  NOT_APPLICABLE
}
