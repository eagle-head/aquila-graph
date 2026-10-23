package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.MethodCfgState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.BodyRef;

/**
 * Consumer qualification and every observed blocker before construction.
 *
 * @param state
 *          body applicability or admission state
 * @param body
 *          retained body association when available
 * @param plan
 *          construction plan only when admitted
 * @param blockers
 *          ordered observed refusal causes
 */
public record CfgAdmissionResult(MethodCfgState state, Optional<BodyRef> body, Optional<LoweringPlan> plan, List<CfgBlocker> blockers) {
  /** Enforces the exclusive admitted, bodyless, and refused shapes. */
  public CfgAdmissionResult {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(plan, "plan");
    blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
    if (state == MethodCfgState.FAILED || (state == MethodCfgState.AVAILABLE) != plan.isPresent()
        || (state == MethodCfgState.AVAILABLE && body.isEmpty()) || (state == MethodCfgState.NOT_APPLICABLE && body.isPresent())
        || ((state == MethodCfgState.AVAILABLE || state == MethodCfgState.NOT_APPLICABLE) != blockers.isEmpty())) {
      throw new IllegalArgumentException("CFG admission fields contradict its state");
    }
    if (plan.isPresent() && !plan.orElseThrow().body().equals(body.orElseThrow())) {
      throw new IllegalArgumentException("CFG admission plan belongs to another body");
    }
    if (!blockers.isEmpty() && state != MethodCfgState.refusalSummary(blockers)) {
      throw new IllegalArgumentException("CFG admission refusal must retain its blocker summary");
    }
  }
}
