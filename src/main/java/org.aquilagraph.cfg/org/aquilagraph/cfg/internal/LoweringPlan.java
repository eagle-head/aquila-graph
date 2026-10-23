package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.api.CfgProfile;

import java.util.Objects;
import org.aquilagraph.identity.api.BodyRef;

/**
 * Immutable association of a selected admitted body and its graph interpretation.
 *
 * @param body
 *          exact admitted source body
 * @param profile
 *          selected graph interpretation
 */
public record LoweringPlan(BodyRef body, CfgProfile profile) {
  /** Requires an exact body and interpretation. */
  public LoweringPlan {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(profile, "profile");
  }
}
