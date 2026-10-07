package org.aquilagraph.cfg.internal;

import java.util.Objects;
import org.aquilagraph.cfg.api.CfgConstructionException;
import org.aquilagraph.cfg.api.CfgDeriver;

/** Injects a declared construction failure while retaining native admission and unaffected graph construction. */
public final class RecoverableCfgFailures {
  private RecoverableCfgFailures() {
  }

  public static CfgDeriver atMethod(String methodKey, RuntimeException cause) {
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(cause, "cause");
    var builder = new ControlFlowGraphBuilder();
    return new JavaCfgDeriver((source, plan) -> {
      if (plan.body().methodKey().equals(methodKey)) {
        throw new CfgConstructionException(CfgConstructionException.Mode.RECOVERABLE, cause);
      }
      return builder.build(source, plan);
    });
  }
}
