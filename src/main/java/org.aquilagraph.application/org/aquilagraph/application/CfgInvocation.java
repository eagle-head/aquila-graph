package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AstOutcome;

/** Checks the CFG port's binding and retains established source on declared abrupt failure. */
final class CfgInvocation {
  private final CfgDeriver deriver;

  CfgInvocation(CfgDeriver deriver) {
    this.deriver = Objects.requireNonNull(deriver, "deriver");
  }

  CfgOutcome derive(AstOutcome ast, CfgRequest request) {
    try {
      var outcome = Objects.requireNonNull(deriver.derive(ast.result().orElseThrow(), request), "derived CFG outcome");
      if (!request.equals(outcome.request())) {
        throw new IllegalStateException("CFG port returned a different requested identity or selection");
      }
      return outcome;
    } catch (CfgDerivationException exception) {
      if (!request.equals(exception.request())) {
        throw new IllegalStateException("CFG port returned a different requested identity or selection", exception);
      }
      throw new AnalysisDerivationException(ast, exception);
    }
  }
}
