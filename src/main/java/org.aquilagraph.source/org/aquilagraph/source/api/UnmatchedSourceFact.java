package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * One diagnosed extra actual fact under a captured source owner.
 *
 * @param owner
 *          captured source context that owns the fact
 * @param fact
 *          exact retained actual occurrence
 * @param ordinal
 *          nonnegative actual occurrence position
 * @param reason
 *          reason the occurrence did not match an expected slot
 * @param emission
 *          creating-site diagnostic event for the unmatched occurrence
 */
public record UnmatchedSourceFact(SourceContext.Address owner, CoverageFactReference fact, int ordinal, Reason reason,
    DiagnosticEmission.Address emission) {
  /** Validates exact owner, actual occurrence and required event. */
  public UnmatchedSourceFact {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(fact, "fact");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(emission, "emission");
    if (ordinal < 0 || !owner.sourceFileKey().equals(fact.sourceFileKey())) {
      throw new IllegalArgumentException("Unmatched facts require captured source ownership and an actual ordinal");
    }
  }

  /** Reason the retained fact did not match an expected slot. */
  public enum Reason {
    /** No expected fact slot. */
    EXTRA,
    /** Additional actual instance. */
    DUPLICATE
  }
}
