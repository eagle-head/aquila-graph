package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxNode;

final class SyntaxGraphLowerer {
  private final JavaMethod method;
  private final ControlFlowGraphConstruction graph;
  private final List<AnalysisDiagnostic> diagnostics = new ArrayList<>();
  private final StatementGraphLowerer statements;
  private final ExpressionGraphLowerer expressions;

  SyntaxGraphLowerer(JavaMethod method) {
    this.method = Objects.requireNonNull(method, "method");
    graph = new ControlFlowGraphConstruction(method);
    var parameters = method.parameters().stream().map(graph::parameterValue).toList();
    var values = new FlowValueScope(parameters);
    statements = new StatementGraphLowerer(this, graph, values);
    expressions = new ExpressionGraphLowerer(this, graph, values);
  }

  ControlFlowGraphResult lower() {
    var fragment = lower(method.body().orElseThrow().syntaxRoot());
    return new ControlFlowGraphResult(Optional.of(graph.complete(fragment)), diagnostics);
  }

  GraphFragment lower(SyntaxNode node) {
    Objects.requireNonNull(node, "node");
    return switch (node.syntaxKind()) {
      case BLOCK, LOCAL_DECLARATION, EXPRESSION_STATEMENT, RETURN, THROW, IF, TRY, EMPTY -> statements.lower(node);
      case LITERAL, VARIABLE_ACCESS, MEMBER_ACCESS, ARRAY_LENGTH, ARRAY_ACCESS, INVOCATION, OBJECT_CREATION, UNARY, BINARY, CONDITIONAL_AND,
          CONDITIONAL_OR, ASSIGNMENT, PARENTHESIZED, TYPE_CAST, CONDITIONAL_EXPRESSION, SWITCH_EXPRESSION ->
        expressions.lower(node);
      default -> unsupported(node);
    };
  }

  private GraphFragment unsupported(SyntaxNode node) {
    var message = "Unsupported syntax during CFG construction";
    diagnostics.add(
        new AnalysisDiagnostic(StableId.sha256("diagnostic", method.methodKey(), node.nodeId(), message), "control-flow.unsupported-syntax",
            DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.ERROR, message + ": " + node.syntaxKind(), node.origins()));
    return GraphFragment.empty();
  }
}
