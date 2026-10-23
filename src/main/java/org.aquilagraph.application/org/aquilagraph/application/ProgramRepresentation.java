package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.source.api.AstOutcome;

/**
 * References to independently owned source and optional control-flow outcomes.
 *
 * @param ast
 *          established source outcome, including unavailable production
 * @param cfg
 *          optional requested derivation outcome
 */
public record ProgramRepresentation(AstOutcome ast, Optional<CfgOutcome> cfg) {
  /** Requires a source result for derivation and exact identity for accepted graphs. */
  public ProgramRepresentation {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(cfg, "cfg");
    if (cfg.isPresent() && (ast.result().isEmpty() || (cfg.orElseThrow().state() != CfgState.REJECTED
        && !cfg.orElseThrow().request().astIdentity().equals(ast.result().orElseThrow().identity())))) {
      throw new IllegalArgumentException("Accepted CFG outcomes require their exact established AST result");
    }
  }
}
