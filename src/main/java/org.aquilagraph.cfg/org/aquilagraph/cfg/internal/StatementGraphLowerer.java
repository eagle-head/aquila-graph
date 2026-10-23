package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.internal.ExceptionHandler.Alternative;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

final class StatementGraphLowerer {
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
      case BLOCK -> buildBlock(node.children(SyntaxRole.STATEMENT));
      case LOCAL_DECLARATION -> buildLocal(node);
      case EXPRESSION_STATEMENT -> syntax.lower(node.requiredChild(SyntaxRole.EXPRESSION)).withoutValue();
      case RETURN -> buildReturn(node);
      case THROW -> buildThrow(node);
      case IF -> buildIf(node);
      case TRY -> buildTry(node);
      case YIELD -> buildYield(node);
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
    var declaration = node.value(SyntaxValue.LocalDeclaration.class);
    var name = declaration.name();
    var declarationKey = node.requiredReference().symbolKey().orElseThrow();
    var local = graph.localValue(declarationKey, name, node.resolvedType());
    var initializer = node.optionalChild(SyntaxRole.INITIALIZER).map(ignored -> syntax.lowerUse(node, SyntaxRole.INITIALIZER))
        .orElseGet(GraphFragment::empty);
    var operands = initializer.value().map(List::of).orElseGet(List::of);
    var write = graph.addOperation(node, "declare-" + name, OperationKind.WRITE, name, operands, Optional.of(local), List.of());
    graph.connectExits(initializer, write.nodeId(), TransferKind.NORMAL, Optional.empty());
    values.bind(declarationKey, local);
    return new GraphFragment(initializer.entry().or(() -> Optional.of(write.nodeId())), List.of(write.nodeId()), Optional.of(local));
  }

  private GraphFragment buildReturn(SyntaxNode node) {
    var value = node.optionalChild(SyntaxRole.EXPRESSION).map(ignored -> syntax.lowerUse(node, SyntaxRole.EXPRESSION))
        .orElseGet(GraphFragment::empty);
    var operation = graph.addOperation(node, "return", OperationKind.RETURN, "return", value.value().map(List::of).orElseGet(List::of),
        Optional.empty(), List.of());
    graph.connectExits(value, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    graph.addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.exit(), TransferKind.RETURN, Optional.empty());
    return new GraphFragment(value.entry().or(() -> Optional.of(operation.nodeId())), List.of(), Optional.empty());
  }

  private GraphFragment buildThrow(SyntaxNode node) {
    var expression = node.requiredChild(SyntaxRole.EXPRESSION);
    var value = syntax.lower(expression);
    var exceptionType = expression.resolvedType()
        .orElseThrow(() -> new IllegalStateException("An admitted throw requires its attributed reference type")).erasedName();
    var operation = graph.addOperation(node, "throw", OperationKind.THROW, "throw", value.value().map(List::of).orElseGet(List::of),
        Optional.empty(), List.of(ExceptionType.fromSyntax(expression, exceptionType)));
    graph.connectExits(value, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(value.entry().or(() -> Optional.of(operation.nodeId())), List.of(), Optional.empty());
  }

  private GraphFragment buildYield(SyntaxNode node) {
    var target = syntax.yieldTarget();
    var value = syntax.lowerUse(node, SyntaxRole.VALUE);
    var yields = new ArrayList<>(value.yieldExits());
    for (var source : value.normalExits()) {
      yields.add(new YieldExit(target, source, value.value().orElseThrow()));
    }
    return new GraphFragment(value.entry(), List.of(), Optional.empty(), yields);
  }

  private GraphFragment buildIf(SyntaxNode node) {
    var condition = syntax.lowerUse(node, SyntaxRole.CONDITION);
    var decision = graph.addOperation(node, "if-decision", OperationKind.DECISION, "if",
        condition.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(condition, decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    var thenGraphFragment = syntax.lower(node.requiredChild(SyntaxRole.THEN_BRANCH));
    var elseGraphFragment = node.optionalChild(SyntaxRole.ELSE_BRANCH).map(syntax::lower).orElseGet(GraphFragment::empty);
    var merge = graph.addOperation(node, "if-merge", OperationKind.MERGE, "if", List.of(), Optional.empty(), List.of());
    var thenReachesMerge = connectBranch(decision.nodeId(), thenGraphFragment, merge.nodeId(), true);
    var elseReachesMerge = connectBranch(decision.nodeId(), elseGraphFragment, merge.nodeId(), false);
    var yields = new ArrayList<>(thenGraphFragment.yieldExits());
    yields.addAll(elseGraphFragment.yieldExits());
    return new GraphFragment(condition.entry().or(() -> Optional.of(decision.nodeId())),
        thenReachesMerge || elseReachesMerge ? List.of(merge.nodeId()) : List.of(), Optional.empty(), yields);
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
    var tryBlock = node.requiredChild(SyntaxRole.TRY_BLOCK);
    var catches = node.children(SyntaxRole.CATCH_CLAUSE);
    var handlerDescriptors = catches.stream().map(catchNode -> catchHandler(catchNode, graph.operationNodeId(catchNode, "catch-entry")))
        .toList();
    var body = graph.withExceptionHandlers(handlerDescriptors, () -> syntax.lower(tryBlock));

    var catchGraphFragments = new ArrayList<GraphFragment>();
    for (var index = 0; index < catches.size(); index++) {
      catchGraphFragments.add(buildCatch(catches.get(index), handlerDescriptors.get(index).operationNodeId()));
    }
    var merge = graph.addOperation(node, "try-merge", OperationKind.MERGE, "try", List.of(), Optional.empty(), List.of());
    var reachesMerge = graph.connectNormalToMerge(body, merge.nodeId());
    var yields = new ArrayList<>(body.yieldExits());
    for (var catchGraphFragment : catchGraphFragments) {
      reachesMerge |= graph.connectNormalToMerge(catchGraphFragment, merge.nodeId());
      yields.addAll(catchGraphFragment.yieldExits());
    }
    var entry = body.entry().or(() -> Optional.of(merge.nodeId()));
    return new GraphFragment(entry, reachesMerge ? List.of(merge.nodeId()) : List.of(), Optional.empty(), yields);
  }

  private GraphFragment buildCatch(SyntaxNode node, String catchOperationId) {
    return values.inChildScope(() -> {
      var declaration = node.requiredChild(SyntaxRole.PARAMETER);
      var name = declaration.value(SyntaxValue.LocalDeclaration.class).name();
      var declarationKey = declaration.requiredReference().symbolKey().orElseThrow();
      var type = declaration.resolvedType();
      var exceptionValue = graph.exceptionValue(catchOperationId, name, type);
      values.bind(declarationKey, exceptionValue);
      var operation = graph.addOperationWithId(catchOperationId, node, OperationKind.CATCH,
          type.map(JavaTypeIdentity::displayName).orElse("unresolved"), List.of(), Optional.of(exceptionValue), List.of());
      var body = syntax.lower(node.requiredChild(SyntaxRole.BODY));
      if (body.entry().isPresent()) {
        graph.addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.operation(body.entry().orElseThrow()), TransferKind.NORMAL,
            Optional.empty());
      }
      return new GraphFragment(Optional.of(operation.nodeId()), body.entry().isEmpty() ? List.of(operation.nodeId()) : body.normalExits(),
          Optional.empty(), body.yieldExits());
    });
  }

  private static List<String> catchTypeNames(SyntaxNode catchNode) {
    var declaration = catchNode.requiredChild(SyntaxRole.PARAMETER);
    var reference = declaration.semanticReference()
        .orElseThrow(() -> new IllegalStateException("An admitted catch requires resolved qualified reference evidence"));
    var names = reference.typeAlternativeNames();
    if (declaration.resolvedType().isEmpty() || reference.status() != ResolutionStatus.RESOLVED || names.isEmpty()
        || names.stream().anyMatch(String::isBlank)) {
      throw new IllegalStateException("An admitted catch requires resolved qualified alternatives");
    }
    return names;
  }

  private static ExceptionHandler catchHandler(SyntaxNode catchNode, String operationNodeId) {
    var names = catchTypeNames(catchNode);
    var reference = catchNode.requiredChild(SyntaxRole.PARAMETER).requiredReference();
    var alternatives = names.stream().map(name -> new Alternative(name, names.size() == 1 ? reference.assignableToTypeNames() : List.of()))
        .toList();
    return new ExceptionHandler(names, operationNodeId, alternatives);
  }
}
