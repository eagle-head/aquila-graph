package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;

/**
 * Immutable explanation of completed-run publication eligibility.
 *
 * @param impediments
 *          applicable reasons, normalized to declaration order without duplicates
 */
public record PublicationEligibility(List<Impediment> impediments) {
  /**
   * Copies and orders the reasons independently of the caller's collection.
   *
   * @throws NullPointerException
   *           if the collection or any reason is null
   */
  public PublicationEligibility {
    impediments = List.copyOf(Objects.requireNonNull(impediments, "impediments").stream().distinct().sorted().toList());
  }

  /**
   * Returns whether no retained fact prevents completed-run publication.
   *
   * @return true exactly when the impediment list is empty
   */
  public boolean eligible() {
    return impediments.isEmpty();
  }

  /** Applicable impediments in their deterministic reporting order. */
  public enum Impediment {
    /** The overall analysis did not complete. */
    RUN_NOT_COMPLETE,
    /** No admitted source result and its environment are available. */
    SOURCE_UNAVAILABLE,
    /** Control flow is absent or its aggregate did not complete. */
    CFG_NOT_COMPLETE,
    /** The canonical diagnostic catalog contains an error. */
    ERROR_DIAGNOSTICS,
    /** Available source and complete control flow lack exact qualified body coverage. */
    BODY_COVERAGE_INCOMPLETE,
    /** An available source coverage observation blocks completion. */
    SOURCE_COVERAGE_BLOCKED
  }
}
