package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxKind;
import org.aquilagraph.model.SyntaxNode;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;

final class ExpressionGraphLowerer {
  private static final String NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";

  private final MethodGraphLowerer context;

  ExpressionGraphLowerer(MethodGraphLowerer context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  GraphFragment lower(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case LITERAL -> buildLiteral(node);
      case VARIABLE_ACCESS -> buildRead(node);
      case MEMBER_ACCESS -> buildUnaryLike(node, OperationKind.MEMBER_ACCESS, exceptions(NULL_POINTER_EXCEPTION));
      case ARRAY_LENGTH -> buildUnaryLike(node, OperationKind.ARRAY_LENGTH, exceptions(NULL_POINTER_EXCEPTION));
      case ARRAY_ACCESS ->
        buildNary(node, OperationKind.ARRAY_ACCESS, exceptions(NULL_POINTER_EXCEPTION, "java.lang.ArrayIndexOutOfBoundsException"));
      case INVOCATION ->
        buildNary(node, OperationKind.INVOKE, exceptions(NULL_POINTER_EXCEPTION, MethodGraphLowerer.UNKNOWN_INVOCATION_EXCEPTION));
      case OBJECT_CREATION -> buildNary(node, OperationKind.NEW_OBJECT, exceptions(MethodGraphLowerer.UNKNOWN_CONSTRUCTOR_EXCEPTION));
      case UNARY -> buildUnaryLike(node, OperationKind.UNARY, List.of());
      case BINARY -> buildBinary(node);
      case CONDITIONAL_AND -> buildShortCircuit(node, true);
      case CONDITIONAL_OR -> buildShortCircuit(node, false);
      case ASSIGNMENT -> buildAssignment(node);
      case PARENTHESIZED -> lower(node.children().getFirst());
      case TYPE_CAST -> buildUnaryLike(node, OperationKind.CAST, exceptions("java.lang.ClassCastException"));
      case CONDITIONAL_EXPRESSION -> buildConditionalExpression(node);
      case SWITCH_EXPRESSION -> buildSwitchExpression(node);
      default -> context.unsupported(node, "Unsupported expression during CFG construction");
    };
  }

  private GraphFragment buildLiteral(SyntaxNode node) {
    var operationNodeId = context.operationNodeId(node, "constant");
    var value = context.value(StableId.sha256("value", operationNodeId), ValueKind.CONSTANT, Optional.empty(), Optional.of(node.detail()),
        context.staticType(node));
    var operation = context.addOperationWithId(operationNodeId, node, OperationKind.CONSTANT, node.detail(), List.of(), Optional.of(value),
        List.of());
    return GraphFragment.value(operation.nodeId(), value);
  }

  private GraphFragment buildRead(SyntaxNode node) {
    var source = context.variable(node.detail());
    if (source == null) {
      source = context.value(StableId.sha256("value", context.method().methodKey(), node.nodeId(), "symbol"), ValueKind.SYMBOL,
          Optional.of(node.detail()), Optional.empty(), context.staticType(node));
    }
    var operationNodeId = context.operationNodeId(node, "read");
    var result = context.temporary(operationNodeId, node.detail(), context.staticType(node));
    var operation = context.addOperationWithId(operationNodeId, node, OperationKind.READ, node.detail(), List.of(source),
        Optional.of(result), List.of());
    return GraphFragment.value(operation.nodeId(), result);
  }

  private GraphFragment buildUnaryLike(SyntaxNode node, OperationKind kind, List<String> possibleExceptions) {
    var input = lower(node.children().getFirst());
    var operationNodeId = context.operationNodeId(node, kind.name().toLowerCase(Locale.ROOT));
    var result = context.temporary(operationNodeId, node.detail(), context.staticType(node));
    var operation = context.addOperationWithId(operationNodeId, node, kind, node.detail(), input.value().map(List::of).orElseGet(List::of),
        Optional.of(result), possibleExceptions);
    context.connectExits(input, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(input.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()), Optional.of(result));
  }

  private GraphFragment buildNary(SyntaxNode node, OperationKind kind, List<String> possibleExceptions) {
    var evaluation = GraphFragment.empty();
    var operands = new ArrayList<FlowValue>();
    for (var child : node.children()) {
      var childGraphFragment = lower(child);
      evaluation = context.sequence(evaluation, childGraphFragment);
      childGraphFragment.value().ifPresent(operands::add);
    }
    var operationNodeId = context.operationNodeId(node, kind.name().toLowerCase(Locale.ROOT));
    var result = context.temporary(operationNodeId, node.detail(), context.staticType(node));
    var operation = context.addOperationWithId(operationNodeId, node, kind, node.detail(), operands, Optional.of(result),
        possibleExceptions);
    context.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildBinary(SyntaxNode node) {
    var exceptions = "DIVIDE".equals(node.detail()) || "REMAINDER".equals(node.detail())
        ? exceptions("java.lang.ArithmeticException")
        : List.<String>of();
    return buildNary(node, OperationKind.BINARY, exceptions);
  }

  private GraphFragment buildShortCircuit(SyntaxNode node, boolean and) {
    var left = lower(node.children().get(0));
    var decision = context.addOperation(node, and ? "and-decision" : "or-decision", OperationKind.DECISION, node.detail(),
        left.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    context.connectExits(left, decision.nodeId(), TransferKind.NORMAL, Optional.empty());

    var right = lower(node.children().get(1));
    var shortCircuitLiteral = and ? "false" : "true";
    var constantId = context.operationNodeId(node, and ? "and-false" : "or-true");
    var constantValue = context.value(StableId.sha256("value", constantId), ValueKind.CONSTANT, Optional.empty(),
        Optional.of(shortCircuitLiteral), Optional.of("boolean"));
    var constant = context.addOperationWithId(constantId, node, OperationKind.CONSTANT, shortCircuitLiteral, List.of(),
        Optional.of(constantValue), List.of());
    if (and) {
      context.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(right.entry().orElseThrow()),
          TransferKind.TRUE_BRANCH, Optional.empty());
      context.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(constant.nodeId()), TransferKind.FALSE_BRANCH,
          Optional.empty());
    } else {
      context.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(constant.nodeId()), TransferKind.TRUE_BRANCH,
          Optional.empty());
      context.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(right.entry().orElseThrow()),
          TransferKind.FALSE_BRANCH, Optional.empty());
    }
    var mergeId = context.operationNodeId(node, and ? "and-merge" : "or-merge");
    var result = context.temporary(mergeId, node.detail(), Optional.of("boolean"));
    var merge = context.addOperationWithId(mergeId, node, OperationKind.MERGE, node.detail(),
        List.of(constantValue, right.value().orElseThrow()), Optional.of(result), List.of());
    context.addTransfer(CfgEndpoint.operation(constant.nodeId()), CfgEndpoint.operation(merge.nodeId()), TransferKind.NORMAL,
        Optional.empty());
    context.connectExits(right, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(left.entry().or(() -> Optional.of(decision.nodeId())), List.of(merge.nodeId()), Optional.of(result));
  }

  private GraphFragment buildAssignment(SyntaxNode node) {
    var target = lower(node.children().get(0));
    var value = lower(node.children().get(1));
    var evaluation = context.sequence(target, value);
    var operationNodeId = context.operationNodeId(node, "assignment");
    var result = context.temporary(operationNodeId, "assignment", context.staticType(node));
    var operands = new ArrayList<FlowValue>();
    target.value().ifPresent(operands::add);
    value.value().ifPresent(operands::add);
    var operation = context.addOperationWithId(operationNodeId, node, OperationKind.WRITE, node.detail(), operands, Optional.of(result),
        List.of());
    context.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildConditionalExpression(SyntaxNode node) {
    var condition = lower(node.children().get(0));
    var decision = context.addOperation(node, "conditional-decision", OperationKind.DECISION, "?:",
        condition.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    context.connectExits(condition, decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    var whenTrue = lower(node.children().get(1));
    var whenFalse = lower(node.children().get(2));
    context.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(whenTrue.entry().orElseThrow()),
        TransferKind.TRUE_BRANCH, Optional.empty());
    context.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(whenFalse.entry().orElseThrow()),
        TransferKind.FALSE_BRANCH, Optional.empty());
    var mergeId = context.operationNodeId(node, "conditional-merge");
    var result = context.temporary(mergeId, "?:", context.staticType(node));
    var merge = context.addOperationWithId(mergeId, node, OperationKind.MERGE, "?:",
        List.of(whenTrue.value().orElseThrow(), whenFalse.value().orElseThrow()), Optional.of(result), List.of());
    context.connectExits(whenTrue, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    context.connectExits(whenFalse, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(condition.entry().or(() -> Optional.of(decision.nodeId())), List.of(merge.nodeId()), Optional.of(result));
  }

  private GraphFragment buildSwitchExpression(SyntaxNode node) {
    var selector = lower(node.children().getFirst());
    var switchOperation = context.addOperation(node, "switch-selector", OperationKind.SWITCH_SELECTOR, "switch",
        selector.value().map(List::of).orElseGet(List::of), Optional.empty(),
        exceptions(NULL_POINTER_EXCEPTION, "java.lang.MatchException"));
    context.connectExits(selector, switchOperation.nodeId(), TransferKind.NORMAL, Optional.empty());
    var cases = new ArrayList<GraphFragment>();
    var caseValues = new ArrayList<FlowValue>();
    for (var index = 1; index < node.children().size(); index++) {
      var caseNode = node.children().get(index);
      var caseGraphFragment = caseNode.children().isEmpty() ? GraphFragment.empty() : buildCaseBody(caseNode.children().getFirst());
      cases.add(caseGraphFragment);
      caseGraphFragment.value().ifPresent(caseValues::add);
      if (caseGraphFragment.entry().isPresent()) {
        context.addTransfer(CfgEndpoint.operation(switchOperation.nodeId()), CfgEndpoint.operation(caseGraphFragment.entry().orElseThrow()),
            TransferKind.SWITCH_ALTERNATIVE, Optional.of(caseNode.detail()));
      }
    }
    var mergeId = context.operationNodeId(node, "switch-merge");
    var result = context.temporary(mergeId, "switch", context.staticType(node));
    var merge = context.addOperationWithId(mergeId, node, OperationKind.MERGE, "switch", caseValues, Optional.of(result), List.of());
    for (var index = 0; index < cases.size(); index++) {
      var caseGraphFragment = cases.get(index);
      if (caseGraphFragment.entry().isEmpty()) {
        context.addTransfer(CfgEndpoint.operation(switchOperation.nodeId()), CfgEndpoint.operation(merge.nodeId()),
            TransferKind.SWITCH_ALTERNATIVE, Optional.of(node.children().get(index + 1).detail()));
      } else {
        context.connectExits(caseGraphFragment, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
      }
    }
    return new GraphFragment(selector.entry().or(() -> Optional.of(switchOperation.nodeId())), List.of(merge.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildCaseBody(SyntaxNode node) {
    if (node.syntaxKind() == SyntaxKind.YIELD) {
      return lower(node.children().getFirst());
    }
    return switch (node.syntaxKind()) {
      case BLOCK, LOCAL_DECLARATION, EXPRESSION_STATEMENT, RETURN, THROW, IF, TRY, EMPTY -> context.lowerStatement(node);
      default -> lower(node);
    };
  }

  private static List<String> exceptions(String... values) {
    return List.of(values);
  }
}
