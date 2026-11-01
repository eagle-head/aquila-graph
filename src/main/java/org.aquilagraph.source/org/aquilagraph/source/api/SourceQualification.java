package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable source decision derived from the exact admitted profile and facts.
 *
 * @param completion
 *          source-only decision
 * @param profile
 *          original source profile
 * @param semanticRevision
 *          original production revision, absent for an empty capture without production
 * @param reasons
 *          blocking explanations in rule and source occurrence order
 */
public record SourceQualification(AstCompletion completion, AstProfile profile, Optional<String> semanticRevision,
    List<SourceQualificationReason> reasons) {
  /** Preserves the decision and its immutable explanations together. */
  public SourceQualification {
    Objects.requireNonNull(completion, "completion");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(semanticRevision, "semanticRevision");
    reasons = List.copyOf(reasons);
    if ((completion == AstCompletion.COMPLETE) != reasons.isEmpty()) {
      throw new IllegalArgumentException("Source completion must agree with its blocking reasons");
    }
  }
}
