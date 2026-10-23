package org.aquilagraph.cfg.api;

import java.util.List;

/** Control-flow outcome of one selected canonical declaration. */
public enum MethodCfgState {
  /** The selected declaration has no body. */
  NOT_APPLICABLE,
  /** Construction produced a validated graph. */
  AVAILABLE,
  /** Required source body facts are unavailable. */
  SOURCE_UNAVAILABLE,
  /** The represented source construct is outside the graph profile. */
  UNSUPPORTED,
  /** A graph-required fact is erroneous. */
  ERRONEOUS_EVIDENCE,
  /** A graph-required fact is missing. */
  INSUFFICIENT_EVIDENCE,
  /** Admitted construction failed through its declared recoverable family. */
  FAILED;

  private static final List<MethodCfgState> REFUSAL_PRECEDENCE = List.of(SOURCE_UNAVAILABLE, UNSUPPORTED, ERRONEOUS_EVIDENCE,
      INSUFFICIENT_EVIDENCE);

  /**
   * Summarizes all refusal causes without changing their retained order.
   *
   * @param blockers
   *          nonempty ordered refusal causes
   * @return first present refusal kind in the declared precedence
   * @throws IllegalArgumentException
   *           when no refusal causes are supplied
   */
  public static MethodCfgState refusalSummary(List<CfgBlocker> blockers) {
    var causes = List.copyOf(blockers);
    for (var kind : REFUSAL_PRECEDENCE) {
      if (causes.stream().anyMatch(blocker -> blocker.kind() == kind)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("A CFG refusal summary requires at least one blocker");
  }
}
