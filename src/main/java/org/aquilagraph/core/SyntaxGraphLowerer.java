package org.aquilagraph.core;

import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.syntax.EnumConstructorContextDecision.Target;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.TypedUseDecision;
import org.aquilagraph.syntax.InvocationArityDecision;
import org.aquilagraph.syntax.StaticAccessDecision;
import org.aquilagraph.syntax.StringConcatenationDecision;

final class SyntaxGraphLowerer {
  private final MethodSyntax methodSyntax;
  private final ControlFlowGraphConstruction graph;
  private final StatementGraphLowerer statements;
  private final ExpressionGraphLowerer expressions;
  private final UseConversionLowerer conversions;
  private final Deque<String> switchTargets = new ArrayDeque<>();

  SyntaxGraphLowerer(JavaMethod method, MethodSyntax methodSyntax) {
    Objects.requireNonNull(method, "method");
    this.methodSyntax = Objects.requireNonNull(methodSyntax, "methodSyntax");
    graph = new ControlFlowGraphConstruction(method);
    conversions = new UseConversionLowerer(graph);
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

  GraphFragment lowerUse(SyntaxNode consumer, SyntaxRole role) {
    return lowerUse(consumer, role, 0);
  }

  GraphFragment lowerUse(SyntaxNode consumer, SyntaxRole role, int position) {
    var child = consumer.children().stream().filter(candidate -> candidate.role() == role && candidate.position() == position).findFirst()
        .orElseThrow(() -> new IllegalStateException("An admitted use requires its exact direct child"));
    return convertUse(consumer, role, position, lower(child.node()));
  }

  GraphFragment convertUse(SyntaxNode consumer, SyntaxRole role, int position, GraphFragment input) {
    return conversions.convert(consumer, useDecision(consumer, role, position), input);
  }

  TypedUseDecision useDecision(SyntaxNode consumer, SyntaxRole role, int position) {
    return methodSyntax.useDecision(consumer.nodeId(), role, position)
        .orElseThrow(() -> new IllegalStateException("An admitted child use requires its selected conversion decision"));
  }

  InvocationArityDecision invocationArity(SyntaxNode invocation) {
    return methodSyntax.invocationArity(invocation.nodeId())
        .orElseThrow(() -> new IllegalStateException("An admitted invocation requires its selected applicability"));
  }

  List<FlowValue> enumConstructorContextOperands(SyntaxNode invocation) {
    var context = methodSyntax.enumConstructorContext(invocation.nodeId());
    if (context.isEmpty()) {
      return List.of();
    }
    var binding = context.orElseThrow();
    var name = graph.enumContextValue(binding.enumOwnerKey(), "enum-name", binding.nameType());
    var ordinal = graph.enumContextValue(binding.enumOwnerKey(), "enum-ordinal", binding.ordinalType());
    if (binding.target() == Target.THIS_CONSTRUCTOR) {
      return List.of(graph.receiverValue(invocation, "this", Optional.empty()), name, ordinal);
    }
    return List.of(name, ordinal);
  }

  StaticAccessDecision staticAccess(SyntaxNode access) {
    return methodSyntax.staticAccess(access.nodeId())
        .orElseThrow(() -> new IllegalStateException("An admitted static access requires its selected member decision"));
  }

  StringConcatenationDecision stringConcatenation(SyntaxNode binary) {
    return methodSyntax.stringConcatenation(binary.nodeId())
        .orElseThrow(() -> new IllegalStateException("An admitted String PLUS requires its selected concatenation decision"));
  }

  String yieldTarget() {
    if (switchTargets.isEmpty()) {
      throw new IllegalStateException("A yield requires an enclosing switch expression");
    }
    return switchTargets.getFirst();
  }

  <T> T withSwitchTarget(String switchNodeId, Supplier<T> action) {
    Objects.requireNonNull(switchNodeId, "switchNodeId");
    Objects.requireNonNull(action, "action");
    switchTargets.push(switchNodeId);
    try {
      return action.get();
    } finally {
      switchTargets.pop();
    }
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
