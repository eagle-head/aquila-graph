package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxNode;

final class SyntaxGraphLowerer {
  private final MethodSyntax methodSyntax;
  private final ControlFlowGraphConstruction graph;
  private final StatementGraphLowerer statements;
  private final ExpressionGraphLowerer expressions;

  SyntaxGraphLowerer(JavaMethod method, MethodSyntax methodSyntax) {
    Objects.requireNonNull(method, "method");
    this.methodSyntax = Objects.requireNonNull(methodSyntax, "methodSyntax");
    graph = new ControlFlowGraphConstruction(method);
    var values = new FlowValueScope();
    for (var parameter : method.parameters()) {
      values.bind(parameter.parameterKey(), graph.parameterValue(parameter));
    }
    statements = new StatementGraphLowerer(this, graph, values);
    expressions = new ExpressionGraphLowerer(this, graph, values);
  }

  ControlFlowGraphResult lower() {
    var fragment = lower(methodSyntax.root());
    return new ControlFlowGraphResult(Optional.of(graph.complete(fragment)), List.of());
  }

  GraphFragment lower(SyntaxNode node) {
    Objects.requireNonNull(node, "node");
    return switch (node.syntaxKind()) {
      case BLOCK, LOCAL_DECLARATION, EXPRESSION_STATEMENT, RETURN, THROW, IF, TRY, YIELD, EMPTY -> statements.lower(node);
      case LITERAL, VARIABLE_ACCESS, FIELD_ACCESS, RECEIVER, ARRAY_LENGTH, ARRAY_ACCESS, INVOCATION, OBJECT_CREATION, UNARY, BINARY,
          CONDITIONAL_AND, CONDITIONAL_OR, ASSIGNMENT, PARENTHESIZED, TYPE_CAST, CONDITIONAL_EXPRESSION, SWITCH_EXPRESSION ->
        expressions.lower(node);
      default -> throw new IllegalStateException("Syntax reached graph lowering without a semantic contract: " + node.syntaxKind());
    };
  }
}
