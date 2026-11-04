package org.aquilagraph.application;

import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AstOutcome;

/** Derives control flow from an established source outcome. */
@FunctionalInterface
public interface ExistingSourceDerivation {
  /**
   * Retains the exact source and caller-bound request, including rejected selections.
   *
   * @param ast
   *          established source outcome
   * @param request
   *          exact caller-bound graph request
   * @return qualified result with any derived CFG
   * @throws AnalysisDerivationException
   *           if derivation reports an abrupt failure for the request
   */
  AnalysisRun deriveExisting(AstOutcome ast, CfgRequest request);
}
