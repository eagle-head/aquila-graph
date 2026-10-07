package org.aquilagraph.cfg.api;

import org.aquilagraph.source.api.AstResult;

/** Compiler-independent control-flow derivation over an immutable admitted AST. */
@FunctionalInterface
public interface CfgDeriver {
  /**
   * Derives the exact ordered selection while retaining every normal refusal.
   *
   * @param ast
   *          intrinsically admitted immutable source result
   * @param request
   *          exact source identity and graph interpretation
   * @return rejected, complete, or partial derivation
   */
  CfgOutcome derive(AstResult ast, CfgRequest request);

}
