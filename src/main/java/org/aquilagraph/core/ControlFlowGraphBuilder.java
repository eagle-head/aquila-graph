package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxReadiness;

/** Lowers one translated Java method into its control-flow graph. */
public final class ControlFlowGraphBuilder {
  /** Creates a stateless control-flow graph builder. */
  public ControlFlowGraphBuilder() {
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
    if (syntax.readiness() != SyntaxReadiness.LOWERABLE) {
      var readiness = syntax.readiness();
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
