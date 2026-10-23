package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.source.api.JavaMethod;

/**
 * Selection coverage referencing the canonical declaration and independently owned CFG outcome.
 *
 * @param declaration
 *          canonical source declaration
 * @param state
 *          explicit requested or unrequested qualification
 * @param cfg
 *          optional established per-method derivation
 */
public record MethodCoverage(JavaMethod declaration, MethodCoverageState state, Optional<MethodCfgOutcome> cfg) {
  /** Prevents a coverage query from attaching another declaration's result. */
  public MethodCoverage {
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(cfg, "cfg");
    var absent = state == MethodCoverageState.NOT_REQUESTED || state == MethodCoverageState.REQUEST_REJECTED;
    if (absent == cfg.isPresent() || cfg
        .filter(value -> !value.methodKey().equals(declaration.methodKey()) || !value.state().name().equals(state.name())).isPresent()) {
      throw new IllegalArgumentException("Method coverage must reflect its canonical declaration's actual selected outcome");
    }
  }
}
