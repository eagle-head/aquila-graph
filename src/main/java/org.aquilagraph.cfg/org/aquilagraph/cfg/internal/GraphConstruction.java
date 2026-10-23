package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.source.api.AstResult;

/** Construction boundary used after consumer-owned admission. */
@FunctionalInterface
interface GraphConstruction {
  MethodCfgOutcome build(AstResult ast, LoweringPlan plan);
}
