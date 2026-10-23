package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AstResult;

/** Lowers one translated Java method into its control-flow graph. */
public final class ControlFlowGraphBuilder {
  /** Creates a stateless control-flow graph builder. */
  public ControlFlowGraphBuilder() {
  }

  /**
   * Constructs only the exact body named by a compatible admitted plan.
   *
   * @param ast
   *          immutable source result
   * @param plan
   *          exact body and graph interpretation
   * @return validated available graph
   */
  public MethodCfgOutcome build(AstResult ast, LoweringPlan plan) {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(plan, "plan");
    if (!ast.identity().equals(plan.body().astIdentity())
        || !ast.bodyRef(plan.body().methodKey()).filter(plan.body()::equals).isPresent()) {
      throw new IllegalArgumentException("Lowering plan belongs to another AST result or body");
    }
    var admission = CfgAdmission.assess(ast, plan.body().methodKey(), plan.profile());
    if (!admission.plan().filter(plan::equals).isPresent()) {
      throw new IllegalArgumentException("Lowering plan requires an admitted body");
    }
    var method = ast.method(plan.body().methodKey()).orElseThrow();
    var syntax = ast.body(method.methodKey()).orElseThrow();
    var draft = new SyntaxGraphLowerer(method, syntax).lower();
    var graph = new GraphAdmission(ast, plan).admit(draft.content(), draft.creatingNodes(), draft.generatedRoles(), draft.diagnostics());
    return new MethodCfgOutcome(ast.identity(), method.methodKey(), MethodCfgState.AVAILABLE, Optional.of(plan.body()), Optional.of(graph),
        List.of(), Optional.empty(), draft.diagnostics());
  }

}
