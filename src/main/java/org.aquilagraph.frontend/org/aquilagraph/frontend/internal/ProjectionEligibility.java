package org.aquilagraph.frontend.internal;

/** A declaration operation's permission, independent of phase completion and diagnostics. */
enum ProjectionEligibility {
  ELIGIBLE, INELIGIBLE;

  ProjectionEligibility and(ProjectionEligibility other) {
    return this == ELIGIBLE && other == ELIGIBLE ? ELIGIBLE : INELIGIBLE;
  }
}
