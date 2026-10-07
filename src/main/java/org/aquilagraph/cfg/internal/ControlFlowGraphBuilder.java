package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.LoweringPlan;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.MethodSyntax;

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
    var result = new SyntaxGraphLowerer(method, syntax, Optional.of(new GraphQualification(ast, plan))).lower();
    return new MethodCfgOutcome(ast.identity(), method.methodKey(), MethodCfgState.AVAILABLE, Optional.of(plan.body()), result.graph(),
        List.of(), Optional.empty(), result.diagnostics());
  }

  /**
   * Builds the graph for a method with a translated body.
   *
   * @param method
   *          source method declaration
   * @param syntax
   *          transient syntax evidence for the method body
   * @return graph and construction diagnostics
   */
  public ControlFlowGraphResult build(JavaMethod method, MethodSyntax syntax) {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(syntax, "syntax");
    if (!method.hasBody() || !method.methodKey().equals(syntax.methodKey())) {
      throw new IllegalArgumentException("Control-flow syntax must belong to the declared method body");
    }
    var readiness = CfgAdmission.classify(syntax);
    if (readiness != CfgReadiness.LOWERABLE) {
      var code = switch (readiness) {
        case UNSUPPORTED -> "control-flow.unsupported-method";
        case UNRESOLVED_SEMANTICS -> "control-flow.unresolved-semantics";
        case ERRONEOUS_SEMANTICS -> "control-flow.erroneous-semantics";
        case LOWERABLE -> throw new IllegalStateException("Lowerable syntax must proceed to graph construction");
      };
      var message = switch (readiness) {
        case UNSUPPORTED -> "Method body contains a Java construct outside the supported subset";
        case UNRESOLVED_SEMANTICS -> "Method body lacks compiler semantics required for graph lowering";
        case ERRONEOUS_SEMANTICS -> "Method body contains erroneous compiler semantics";
        case LOWERABLE -> throw new IllegalStateException("Lowerable syntax must proceed to graph construction");
      };
      var diagnostic = new AnalysisDiagnostic(StableId.sha256("diagnostic", method.methodKey(), code), code, DiagnosticStage.CONTROL_FLOW,
          DiagnosticSeverity.ERROR, message, method.origins());
      return new ControlFlowGraphResult(Optional.empty(), List.of(diagnostic));
    }
    return new SyntaxGraphLowerer(method, syntax).lower();
  }
}
