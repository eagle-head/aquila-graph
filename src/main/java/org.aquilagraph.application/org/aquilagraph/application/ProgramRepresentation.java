package org.aquilagraph.application;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
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
  /** Requires established source for derivation and matching identity and body facts for accepted outcomes. */
  public ProgramRepresentation {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(cfg, "cfg");
    cfg.ifPresent(outcome -> {
      var result = ast.result();
      if (result.isEmpty()
          || (outcome.state() != CfgState.REJECTED && !outcome.request().astIdentity().equals(result.orElseThrow().identity()))) {
        throw new IllegalArgumentException("Accepted CFG outcomes require their exact established AST result");
      }
      if (outcome.methods().isEmpty()) {
        return;
      }
      var methods = new HashMap<String, MethodCfgOutcome>();
      outcome.methods().forEach(method -> methods.put(method.methodKey(), method));
      // Diagnostic composition owns rejection of unknown method owners.
      for (var declaration : result.orElseThrow().program().methods()) {
        var method = methods.get(declaration.methodKey());
        if (method != null) {
          MethodOutcomeAdmission.requireCompatibleBody(declaration, method);
        }
      }
    });
  }
}
