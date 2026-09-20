package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SyntaxKind;
import org.aquilagraph.model.SyntaxNode;
import org.aquilagraph.model.TransferKind;

final class ExpressionGraphLowerer {
  private static final String NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";

  private final SyntaxGraphLowerer syntax;
  private final ControlFlowGraphConstruction graph;
  private final FlowValueScope values;

  ExpressionGraphLowerer(SyntaxGraphLowerer syntax, ControlFlowGraphConstruction graph, FlowValueScope values) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.graph = Objects.requireNonNull(graph, "graph");
    this.values = Objects.requireNonNull(values, "values");
  }

  GraphFragment lower(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case LITERAL -> buildLiteral(node);
      case VARIABLE_ACCESS -> buildRead(node);
      case MEMBER_ACCESS -> buildUnaryLike(node, OperationKind.MEMBER_ACCESS, runtimeExceptions(NULL_POINTER_EXCEPTION));
      case ARRAY_LENGTH -> buildUnaryLike(node, OperationKind.ARRAY_LENGTH, runtimeExceptions(NULL_POINTER_EXCEPTION));
      case ARRAY_ACCESS ->
        buildNary(node, OperationKind.ARRAY_ACCESS, runtimeExceptions(NULL_POINTER_EXCEPTION, "java.lang.ArrayIndexOutOfBoundsException"));
      case INVOCATION -> buildNary(node, OperationKind.INVOKE,
          List.of(ExceptionType.runtime(NULL_POINTER_EXCEPTION), ExceptionType.unknown(ExceptionTarget.UNKNOWN_INVOCATION_EXCEPTION)));
      case OBJECT_CREATION ->
        buildNary(node, OperationKind.NEW_OBJECT, List.of(ExceptionType.unknown(ExceptionTarget.UNKNOWN_CONSTRUCTOR_EXCEPTION)));
      case UNARY -> buildUnaryLike(node, OperationKind.UNARY, List.of());
      case BINARY -> buildBinary(node);
      case CONDITIONAL_AND -> buildShortCircuit(node, true);
      case CONDITIONAL_OR -> buildShortCircuit(node, false);
      case ASSIGNMENT -> buildAssignment(node);
      case PARENTHESIZED -> syntax.lower(node.children().getFirst());
      case TYPE_CAST -> buildUnaryLike(node, OperationKind.CAST, runtimeExceptions("java.lang.ClassCastException"));
      case CONDITIONAL_EXPRESSION -> buildConditionalExpression(node);
      case SWITCH_EXPRESSION -> buildSwitchExpression(node);
      default -> throw new IllegalArgumentException("Expected expression syntax but found " + node.syntaxKind());
    };
  }

  private GraphFragment buildLiteral(SyntaxNode node) {
    var operationNodeId = graph.operationNodeId(node, "constant");
    var value = graph.constantValue(operationNodeId, node.detail(), staticType(node));
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.CONSTANT, node.detail(), List.of(), Optional.of(value),
        List.of());
    return GraphFragment.value(operation.nodeId(), value);
  }

  private GraphFragment buildRead(SyntaxNode node) {
    var source = values.resolve(node.detail()).orElseGet(() -> graph.symbolValue(node, node.detail(), staticType(node)));
    var operationNodeId = graph.operationNodeId(node, "read");
    var result = graph.temporaryValue(operationNodeId, node.detail(), staticType(node));
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.READ, node.detail(), List.of(source), Optional.of(result),
        List.of());
    return GraphFragment.value(operation.nodeId(), result);
  }

  private GraphFragment buildUnaryLike(SyntaxNode node, OperationKind kind, List<ExceptionType> possibleExceptions) {
    var input = syntax.lower(node.children().getFirst());
    var operationNodeId = graph.operationNodeId(node, kind.name().toLowerCase(Locale.ROOT));
    var result = graph.temporaryValue(operationNodeId, node.detail(), staticType(node));
    var operation = graph.addOperationWithId(operationNodeId, node, kind, node.detail(), input.value().map(List::of).orElseGet(List::of),
        Optional.of(result), possibleExceptions);
    graph.connectExits(input, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(input.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()), Optional.of(result));
  }

  private GraphFragment buildNary(SyntaxNode node, OperationKind kind, List<ExceptionType> possibleExceptions) {
    var evaluation = GraphFragment.empty();
    var operands = new ArrayList<FlowValue>();
    for (var child : node.children()) {
      var childGraphFragment = syntax.lower(child);
      evaluation = graph.sequence(evaluation, childGraphFragment);
      childGraphFragment.value().ifPresent(operands::add);
    }
    var operationNodeId = graph.operationNodeId(node, kind.name().toLowerCase(Locale.ROOT));
    var result = graph.temporaryValue(operationNodeId, node.detail(), staticType(node));
    var operation = graph.addOperationWithId(operationNodeId, node, kind, node.detail(), operands, Optional.of(result), possibleExceptions);
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildBinary(SyntaxNode node) {
    var exceptions = "DIVIDE".equals(node.detail()) || "REMAINDER".equals(node.detail())
        ? runtimeExceptions("java.lang.ArithmeticException")
        : List.<ExceptionType>of();
    return buildNary(node, OperationKind.BINARY, exceptions);
  }

  private GraphFragment buildShortCircuit(SyntaxNode node, boolean and) {
    var left = syntax.lower(node.children().get(0));
    var decision = graph.addOperation(node, and ? "and-decision" : "or-decision", OperationKind.DECISION, node.detail(),
        left.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(left, decision.nodeId(), TransferKind.NORMAL, Optional.empty());

    var right = syntax.lower(node.children().get(1));
    var shortCircuitLiteral = and ? "false" : "true";
    var constantId = graph.operationNodeId(node, and ? "and-false" : "or-true");
    var constantValue = graph.constantValue(constantId, shortCircuitLiteral, Optional.of("boolean"));
    var constant = graph.addOperationWithId(constantId, node, OperationKind.CONSTANT, shortCircuitLiteral, List.of(),
        Optional.of(constantValue), List.of());
    if (and) {
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(right.entry().orElseThrow()),
          TransferKind.TRUE_BRANCH, Optional.empty());
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(constant.nodeId()), TransferKind.FALSE_BRANCH,
          Optional.empty());
    } else {
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(constant.nodeId()), TransferKind.TRUE_BRANCH,
          Optional.empty());
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(right.entry().orElseThrow()),
          TransferKind.FALSE_BRANCH, Optional.empty());
    }
    var mergeId = graph.operationNodeId(node, and ? "and-merge" : "or-merge");
    var result = graph.temporaryValue(mergeId, node.detail(), Optional.of("boolean"));
    var merge = graph.addOperationWithId(mergeId, node, OperationKind.MERGE, node.detail(),
        List.of(constantValue, right.value().orElseThrow()), Optional.of(result), List.of());
    graph.addTransfer(CfgEndpoint.operation(constant.nodeId()), CfgEndpoint.operation(merge.nodeId()), TransferKind.NORMAL,
        Optional.empty());
    graph.connectExits(right, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(left.entry().or(() -> Optional.of(decision.nodeId())), List.of(merge.nodeId()), Optional.of(result));
  }

  private GraphFragment buildAssignment(SyntaxNode node) {
    var target = syntax.lower(node.children().get(0));
    var value = syntax.lower(node.children().get(1));
    var evaluation = graph.sequence(target, value);
    var operationNodeId = graph.operationNodeId(node, "assignment");
    var result = graph.temporaryValue(operationNodeId, "assignment", staticType(node));
    var operands = new ArrayList<FlowValue>();
    target.value().ifPresent(operands::add);
    value.value().ifPresent(operands::add);
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.WRITE, node.detail(), operands, Optional.of(result),
        List.of());
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildConditionalExpression(SyntaxNode node) {
    var condition = syntax.lower(node.children().get(0));
    var decision = graph.addOperation(node, "conditional-decision", OperationKind.DECISION, "?:",
        condition.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(condition, decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    var whenTrue = syntax.lower(node.children().get(1));
    var whenFalse = syntax.lower(node.children().get(2));
    graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(whenTrue.entry().orElseThrow()),
        TransferKind.TRUE_BRANCH, Optional.empty());
    graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(whenFalse.entry().orElseThrow()),
        TransferKind.FALSE_BRANCH, Optional.empty());
    var mergeId = graph.operationNodeId(node, "conditional-merge");
    var result = graph.temporaryValue(mergeId, "?:", staticType(node));
    var merge = graph.addOperationWithId(mergeId, node, OperationKind.MERGE, "?:",
        List.of(whenTrue.value().orElseThrow(), whenFalse.value().orElseThrow()), Optional.of(result), List.of());
    graph.connectExits(whenTrue, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    graph.connectExits(whenFalse, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(condition.entry().or(() -> Optional.of(decision.nodeId())), List.of(merge.nodeId()), Optional.of(result));
  }

  private GraphFragment buildSwitchExpression(SyntaxNode node) {
    var selector = syntax.lower(node.children().getFirst());
    var switchOperation = graph.addOperation(node, "switch-selector", OperationKind.SWITCH_SELECTOR, "switch",
        selector.value().map(List::of).orElseGet(List::of), Optional.empty(),
        runtimeExceptions(NULL_POINTER_EXCEPTION, "java.lang.MatchException"));
    graph.connectExits(selector, switchOperation.nodeId(), TransferKind.NORMAL, Optional.empty());
    var cases = new ArrayList<GraphFragment>();
    var caseValues = new ArrayList<FlowValue>();
    for (var index = 1; index < node.children().size(); index++) {
      var caseNode = node.children().get(index);
      var caseGraphFragment = caseNode.children().isEmpty() ? GraphFragment.empty() : buildCaseBody(caseNode.children().getFirst());
      cases.add(caseGraphFragment);
      caseGraphFragment.value().ifPresent(caseValues::add);
      if (caseGraphFragment.entry().isPresent()) {
        graph.addTransfer(CfgEndpoint.operation(switchOperation.nodeId()), CfgEndpoint.operation(caseGraphFragment.entry().orElseThrow()),
            TransferKind.SWITCH_ALTERNATIVE, Optional.of(caseNode.detail()));
      }
    }
    var mergeId = graph.operationNodeId(node, "switch-merge");
    var result = graph.temporaryValue(mergeId, "switch", staticType(node));
    var merge = graph.addOperationWithId(mergeId, node, OperationKind.MERGE, "switch", caseValues, Optional.of(result), List.of());
    for (var index = 0; index < cases.size(); index++) {
      var caseGraphFragment = cases.get(index);
      if (caseGraphFragment.entry().isEmpty()) {
        graph.addTransfer(CfgEndpoint.operation(switchOperation.nodeId()), CfgEndpoint.operation(merge.nodeId()),
            TransferKind.SWITCH_ALTERNATIVE, Optional.of(node.children().get(index + 1).detail()));
      } else {
        graph.connectExits(caseGraphFragment, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
      }
    }
    return new GraphFragment(selector.entry().or(() -> Optional.of(switchOperation.nodeId())), List.of(merge.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildCaseBody(SyntaxNode node) {
    if (node.syntaxKind() == SyntaxKind.YIELD) {
      return syntax.lower(node.children().getFirst());
    }
    return syntax.lower(node);
  }

  private static Optional<String> staticType(SyntaxNode node) {
    return node.semanticReferences().stream().map(SemanticReference::typeName).flatMap(Optional::stream).findFirst();
  }

  private static List<ExceptionType> runtimeExceptions(String... typeNames) {
    return java.util.Arrays.stream(typeNames).map(ExceptionType::runtime).toList();
  }
}
