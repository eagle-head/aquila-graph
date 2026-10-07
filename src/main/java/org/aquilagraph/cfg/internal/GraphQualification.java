package org.aquilagraph.cfg.internal;

import java.util.Map;
import java.util.Objects;
import org.aquilagraph.cfg.api.ControlFlowGraph;
import org.aquilagraph.cfg.api.GraphValueAdmission;
import org.aquilagraph.cfg.api.LoweringPlan;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxNode;

/** Exact source and interpretation bound to private graph construction. */
record GraphQualification(AstResult ast, LoweringPlan plan) {
  GraphQualification {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(plan, "plan");
    if (!ast.identity().equals(plan.body().astIdentity())
        || !ast.bodyRef(plan.body().methodKey()).filter(plan.body()::equals).isPresent()) {
      throw new IllegalArgumentException("Graph qualification requires the exact admitted source body");
    }
  }

  ControlFlowGraph qualify(ControlFlowGraph draft, Map<String, SyntaxNode> creatingNodes, Map<String, String> generatedRoles) {
    return GraphValueAdmission.admit(ast, plan, draft, creatingNodes, generatedRoles);
  }
}
