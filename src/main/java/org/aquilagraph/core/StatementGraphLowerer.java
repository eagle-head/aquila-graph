package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SyntaxKind;
import org.aquilagraph.model.SyntaxNode;
import org.aquilagraph.model.TransferKind;

final class StatementGraphLowerer {
  private static final Pattern NON_WHITESPACE = Pattern.compile("\\S+");

  private final SyntaxGraphLowerer syntax;
  private final ControlFlowGraphConstruction graph;
  private final FlowValueScope values;

  StatementGraphLowerer(SyntaxGraphLowerer syntax, ControlFlowGraphConstruction graph, FlowValueScope values) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.graph = Objects.requireNonNull(graph, "graph");
    this.values = Objects.requireNonNull(values, "values");
  }

  GraphFragment lower(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case BLOCK -> buildBlock(node.children());
      case LOCAL_DECLARATION -> buildLocal(node);
      case EXPRESSION_STATEMENT ->
        node.children().isEmpty() ? GraphFragment.empty() : syntax.lower(node.children().getFirst()).withoutValue();
      case RETURN -> buildReturn(node);
      case THROW -> buildThrow(node);
      case IF -> buildIf(node);
      case TRY -> buildTry(node);
      case EMPTY -> GraphFragment.empty();
      default -> throw new IllegalArgumentException("Expected statement syntax but found " + node.syntaxKind());
    };
  }

  private GraphFragment buildBlock(List<SyntaxNode> statements) {
    return values.inChildScope(() -> {
      var result = GraphFragment.empty();
      for (var statement : statements) {
        result = graph.sequence(result, syntax.lower(statement));
      }
      return result;
    });
  }

  private GraphFragment buildLocal(SyntaxNode node) {
    var parts = words(node.detail());
    var name = parts.isEmpty() ? node.detail() : parts.getLast();
    var staticType = parts.size() <= 1 ? Optional.<String>empty() : Optional.of(parts.getFirst());
    var local = graph.localValue(node, name, staticType);
    values.bind(name, local);
    var initializer = node.children().isEmpty() ? GraphFragment.empty() : syntax.lower(node.children().getFirst());
    var operands = initializer.value().map(List::of).orElseGet(List::of);
    var write = graph.addOperation(node, "declare-" + name, OperationKind.WRITE, name, operands, Optional.of(local), List.of());
    graph.connectExits(initializer, write.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(initializer.entry().or(() -> Optional.of(write.nodeId())), List.of(write.nodeId()), Optional.of(local));
  }

  private GraphFragment buildReturn(SyntaxNode node) {
    var value = node.children().isEmpty() ? GraphFragment.empty() : syntax.lower(node.children().getFirst());
    var operation = graph.addOperation(node, "return", OperationKind.RETURN, "return", value.value().map(List::of).orElseGet(List::of),
        Optional.empty(), List.of());
    graph.connectExits(value, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    graph.addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.exit(), TransferKind.RETURN, Optional.empty());
    return new GraphFragment(value.entry().or(() -> Optional.of(operation.nodeId())), List.of(), Optional.empty());
  }

  private GraphFragment buildThrow(SyntaxNode node) {
    var expression = node.children().getFirst();
    var value = syntax.lower(expression);
    var exceptionType = value.value().flatMap(FlowValue::staticType).orElse("java.lang.Throwable");
    var operation = graph.addOperation(node, "throw", OperationKind.THROW, "throw", value.value().map(List::of).orElseGet(List::of),
        Optional.empty(), List.of(ExceptionType.fromSyntax(expression, exceptionType)));
    graph.connectExits(value, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(value.entry().or(() -> Optional.of(operation.nodeId())), List.of(), Optional.empty());
  }

  private GraphFragment buildIf(SyntaxNode node) {
    var condition = syntax.lower(node.children().get(0));
    var decision = graph.addOperation(node, "if-decision", OperationKind.DECISION, "if",
        condition.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(condition, decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    var thenGraphFragment = syntax.lower(node.children().get(1));
    var elseGraphFragment = node.children().size() > 2 ? syntax.lower(node.children().get(2)) : GraphFragment.empty();
    var merge = graph.addOperation(node, "if-merge", OperationKind.MERGE, "if", List.of(), Optional.empty(), List.of());
    var thenReachesMerge = connectBranch(decision.nodeId(), thenGraphFragment, merge.nodeId(), true);
    var elseReachesMerge = connectBranch(decision.nodeId(), elseGraphFragment, merge.nodeId(), false);
    return new GraphFragment(condition.entry().or(() -> Optional.of(decision.nodeId())),
        thenReachesMerge || elseReachesMerge ? List.of(merge.nodeId()) : List.of(), Optional.empty());
  }

  private boolean connectBranch(String decisionId, GraphFragment branch, String mergeId, boolean trueBranch) {
    var transferKind = trueBranch ? TransferKind.TRUE_BRANCH : TransferKind.FALSE_BRANCH;
    if (branch.entry().isEmpty()) {
      graph.addTransfer(CfgEndpoint.operation(decisionId), CfgEndpoint.operation(mergeId), transferKind, Optional.empty());
      return true;
    }
    graph.addTransfer(CfgEndpoint.operation(decisionId), CfgEndpoint.operation(branch.entry().orElseThrow()), transferKind,
        Optional.empty());
    for (var exit : branch.normalExits()) {
      graph.addTransfer(CfgEndpoint.operation(exit), CfgEndpoint.operation(mergeId), TransferKind.NORMAL, Optional.empty());
    }
    return !branch.normalExits().isEmpty();
  }

  private GraphFragment buildTry(SyntaxNode node) {
    var tryBlock = node.children().stream().filter(child -> child.syntaxKind() == SyntaxKind.BLOCK).findFirst().orElseThrow();
    var catches = node.children().stream().filter(child -> child.syntaxKind() == SyntaxKind.CATCH).toList();
    var handlerDescriptors = catches.stream()
        .map(catchNode -> new ExceptionHandler(catchTypeNames(catchNode), graph.operationNodeId(catchNode, "catch-entry"))).toList();
    var body = graph.withExceptionHandlers(handlerDescriptors, () -> syntax.lower(tryBlock));

    var catchGraphFragments = new ArrayList<GraphFragment>();
    for (var index = 0; index < catches.size(); index++) {
      catchGraphFragments.add(buildCatch(catches.get(index), handlerDescriptors.get(index).operationNodeId()));
    }
    var merge = graph.addOperation(node, "try-merge", OperationKind.MERGE, "try", List.of(), Optional.empty(), List.of());
    var reachesMerge = graph.connectNormalToMerge(body, merge.nodeId());
    for (var catchGraphFragment : catchGraphFragments) {
      reachesMerge |= graph.connectNormalToMerge(catchGraphFragment, merge.nodeId());
    }
    var entry = body.entry().or(() -> catchGraphFragments.stream().map(GraphFragment::entry).flatMap(Optional::stream).findFirst());
    return new GraphFragment(entry.or(() -> Optional.of(merge.nodeId())),
        reachesMerge || entry.isEmpty() ? List.of(merge.nodeId()) : List.of(), Optional.empty());
  }

  private GraphFragment buildCatch(SyntaxNode node, String catchOperationId) {
    return values.inChildScope(() -> {
      var declaration = node.children().getFirst();
      var parts = words(declaration.detail());
      var name = parts.getLast();
      var type = declaration.semanticReferences().stream().map(SemanticReference::typeName).flatMap(Optional::stream).findFirst();
      var exceptionValue = graph.exceptionValue(catchOperationId, name, type);
      values.bind(name, exceptionValue);
      var operation = graph.addOperationWithId(catchOperationId, node, OperationKind.CATCH, type.orElse("unresolved"), List.of(),
          Optional.of(exceptionValue), List.of());
      var body = node.children().stream().filter(child -> child.syntaxKind() == SyntaxKind.BLOCK).findFirst().map(syntax::lower)
          .orElseGet(GraphFragment::empty);
      if (body.entry().isPresent()) {
        graph.addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.operation(body.entry().orElseThrow()), TransferKind.NORMAL,
            Optional.empty());
      }
      return new GraphFragment(Optional.of(operation.nodeId()), body.entry().isEmpty() ? List.of(operation.nodeId()) : body.normalExits(),
          Optional.empty());
    });
  }

  private static List<String> catchTypeNames(SyntaxNode catchNode) {
    var declaration = catchNode.children().getFirst();
    return declaration.semanticReferences().stream().map(SemanticReference::typeAlternativeNames).flatMap(List::stream).distinct().toList();
  }

  private static List<String> words(String value) {
    return NON_WHITESPACE.matcher(value).results().map(result -> result.group()).toList();
  }
}
