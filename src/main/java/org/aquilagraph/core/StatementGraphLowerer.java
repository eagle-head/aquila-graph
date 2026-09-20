package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxKind;
import org.aquilagraph.model.SyntaxNode;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;

final class StatementGraphLowerer {
  private static final Pattern NON_WHITESPACE = Pattern.compile("\\S+");

  private final MethodGraphLowerer context;

  StatementGraphLowerer(MethodGraphLowerer context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  GraphFragment lower(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case BLOCK -> buildBlock(node.children());
      case LOCAL_DECLARATION -> buildLocal(node);
      case EXPRESSION_STATEMENT ->
        node.children().isEmpty() ? GraphFragment.empty() : context.lowerExpression(node.children().getFirst()).withoutValue();
      case RETURN -> buildReturn(node);
      case THROW -> buildThrow(node);
      case IF -> buildIf(node);
      case TRY -> buildTry(node);
      case EMPTY -> GraphFragment.empty();
      default -> context.unsupported(node, "Unsupported statement during CFG construction");
    };
  }

  private GraphFragment buildBlock(List<SyntaxNode> statements) {
    var result = GraphFragment.empty();
    for (var statement : statements) {
      result = context.sequence(result, lower(statement));
    }
    return result;
  }

  private GraphFragment buildLocal(SyntaxNode node) {
    var parts = words(node.detail());
    var name = parts.isEmpty() ? node.detail() : parts.getLast();
    var staticType = parts.size() <= 1 ? Optional.<String>empty() : Optional.of(parts.getFirst());
    var local = context.value(StableId.sha256("value", context.method().methodKey(), node.nodeId(), "local", name), ValueKind.LOCAL,
        Optional.of(name), Optional.empty(), staticType);
    context.bindVariable(name, local);
    var initializer = node.children().isEmpty() ? GraphFragment.empty() : context.lowerExpression(node.children().getFirst());
    var operands = initializer.value().map(List::of).orElseGet(List::of);
    var write = context.addOperation(node, "declare-" + name, OperationKind.WRITE, name, operands, Optional.of(local), List.of());
    context.connectExits(initializer, write.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(initializer.entry().or(() -> Optional.of(write.nodeId())), List.of(write.nodeId()), Optional.of(local));
  }

  private GraphFragment buildReturn(SyntaxNode node) {
    var value = node.children().isEmpty() ? GraphFragment.empty() : context.lowerExpression(node.children().getFirst());
    var operation = context.addOperation(node, "return", OperationKind.RETURN, "return", value.value().map(List::of).orElseGet(List::of),
        Optional.empty(), List.of());
    context.connectExits(value, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    context.addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.exit(), TransferKind.RETURN, Optional.empty());
    return new GraphFragment(value.entry().or(() -> Optional.of(operation.nodeId())), List.of(), Optional.empty());
  }

  private GraphFragment buildThrow(SyntaxNode node) {
    var value = context.lowerExpression(node.children().getFirst());
    var exceptionType = value.value().flatMap(FlowValue::staticType).orElse("java.lang.Throwable");
    var operation = context.addOperation(node, "throw", OperationKind.THROW, "throw", value.value().map(List::of).orElseGet(List::of),
        Optional.empty(), List.of(exceptionType));
    context.connectExits(value, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(value.entry().or(() -> Optional.of(operation.nodeId())), List.of(), Optional.empty());
  }

  private GraphFragment buildIf(SyntaxNode node) {
    var condition = context.lowerExpression(node.children().get(0));
    var decision = context.addOperation(node, "if-decision", OperationKind.DECISION, "if",
        condition.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    context.connectExits(condition, decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    var thenGraphFragment = lower(node.children().get(1));
    var elseGraphFragment = node.children().size() > 2 ? lower(node.children().get(2)) : GraphFragment.empty();
    var merge = context.addOperation(node, "if-merge", OperationKind.MERGE, "if", List.of(), Optional.empty(), List.of());
    var thenReachesMerge = connectBranch(decision.nodeId(), thenGraphFragment, merge.nodeId(), true);
    var elseReachesMerge = connectBranch(decision.nodeId(), elseGraphFragment, merge.nodeId(), false);
    return new GraphFragment(condition.entry().or(() -> Optional.of(decision.nodeId())),
        thenReachesMerge || elseReachesMerge ? List.of(merge.nodeId()) : List.of(), Optional.empty());
  }

  private boolean connectBranch(String decisionId, GraphFragment branch, String mergeId, boolean trueBranch) {
    var transferKind = trueBranch ? TransferKind.TRUE_BRANCH : TransferKind.FALSE_BRANCH;
    if (branch.entry().isEmpty()) {
      context.addTransfer(CfgEndpoint.operation(decisionId), CfgEndpoint.operation(mergeId), transferKind, Optional.empty());
      return true;
    }
    context.addTransfer(CfgEndpoint.operation(decisionId), CfgEndpoint.operation(branch.entry().orElseThrow()), transferKind,
        Optional.empty());
    for (var exit : branch.normalExits()) {
      context.addTransfer(CfgEndpoint.operation(exit), CfgEndpoint.operation(mergeId), TransferKind.NORMAL, Optional.empty());
    }
    return !branch.normalExits().isEmpty();
  }

  private GraphFragment buildTry(SyntaxNode node) {
    var tryBlock = node.children().stream().filter(child -> child.syntaxKind() == SyntaxKind.BLOCK).findFirst().orElseThrow();
    var catches = node.children().stream().filter(child -> child.syntaxKind() == SyntaxKind.CATCH).toList();
    var handlerDescriptors = catches.stream()
        .map(catchNode -> new ExceptionHandler(catchType(catchNode), context.operationNodeId(catchNode, "catch-entry"))).toList();
    var outerTarget = context.exceptionTarget();
    context.setExceptionTarget(new ExceptionTarget(handlerDescriptors, outerTarget.includeExit()));
    var body = lower(tryBlock);
    context.setExceptionTarget(outerTarget);

    var catchGraphFragments = new ArrayList<GraphFragment>();
    for (var index = 0; index < catches.size(); index++) {
      catchGraphFragments.add(buildCatch(catches.get(index), handlerDescriptors.get(index).operationNodeId()));
    }
    var merge = context.addOperation(node, "try-merge", OperationKind.MERGE, "try", List.of(), Optional.empty(), List.of());
    var reachesMerge = context.connectNormalToMerge(body, merge.nodeId());
    for (var catchGraphFragment : catchGraphFragments) {
      reachesMerge |= context.connectNormalToMerge(catchGraphFragment, merge.nodeId());
    }
    var entry = body.entry().or(() -> catchGraphFragments.stream().map(GraphFragment::entry).flatMap(Optional::stream).findFirst());
    return new GraphFragment(entry.or(() -> Optional.of(merge.nodeId())),
        reachesMerge || entry.isEmpty() ? List.of(merge.nodeId()) : List.of(), Optional.empty());
  }

  private GraphFragment buildCatch(SyntaxNode node, String catchOperationId) {
    var declaration = node.children().getFirst();
    var parts = words(declaration.detail());
    var name = parts.getLast();
    var type = parts.size() > 1 ? parts.getFirst() : "java.lang.Throwable";
    var exceptionValue = context.value(StableId.sha256("value", catchOperationId), ValueKind.EXCEPTION, Optional.of(name), Optional.empty(),
        Optional.of(type));
    context.bindVariable(name, exceptionValue);
    var operation = context.addOperationWithId(catchOperationId, node, OperationKind.CATCH, type, List.of(), Optional.of(exceptionValue),
        List.of());
    var body = node.children().stream().filter(child -> child.syntaxKind() == SyntaxKind.BLOCK).findFirst().map(this::lower)
        .orElseGet(GraphFragment::empty);
    if (body.entry().isPresent()) {
      context.addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.operation(body.entry().orElseThrow()), TransferKind.NORMAL,
          Optional.empty());
    }
    return new GraphFragment(Optional.of(operation.nodeId()), body.entry().isEmpty() ? List.of(operation.nodeId()) : body.normalExits(),
        Optional.empty());
  }

  private static String catchType(SyntaxNode catchNode) {
    var parts = words(catchNode.detail());
    return parts.isEmpty() ? "java.lang.Throwable" : parts.getFirst();
  }

  private static List<String> words(String value) {
    return NON_WHITESPACE.matcher(value).results().map(result -> result.group()).toList();
  }
}
