package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxNode;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;

final class MethodGraphLowerer {
  static final String UNKNOWN_INVOCATION_EXCEPTION = "unchecked-invocation";
  static final String UNKNOWN_CONSTRUCTOR_EXCEPTION = "unchecked-constructor";

  private final JavaMethod method;
  private final ControlFlowGraphAssembly graph;
  private final Map<String, FlowValue> variables = new HashMap<>();
  private final List<AnalysisDiagnostic> diagnostics = new ArrayList<>();
  private final StatementGraphLowerer statements;
  private final ExpressionGraphLowerer expressions;
  private ExceptionTarget exceptionTarget = new ExceptionTarget(List.of(), true);

  MethodGraphLowerer(JavaMethod method) {
    this.method = Objects.requireNonNull(method, "method");
    graph = new ControlFlowGraphAssembly(method);
    statements = new StatementGraphLowerer(this);
    expressions = new ExpressionGraphLowerer(this);
    for (var parameter : method.parameters()) {
      var value =
          graph.value(
              StableId.sha256("value", parameter.parameterKey()),
              ValueKind.PARAMETER,
              Optional.of(parameter.name()),
              Optional.empty(),
              Optional.of(parameter.staticType()));
      variables.put(parameter.name(), value);
    }
  }

  ControlFlowGraphResult lower() {
    var fragment = lowerStatement(method.body().orElseThrow().syntaxRoot());
    finish(fragment);
    return new ControlFlowGraphResult(Optional.of(graph.toGraph()), diagnostics);
  }

  GraphFragment lowerStatement(SyntaxNode node) {
    return statements.lower(node);
  }

  GraphFragment lowerExpression(SyntaxNode node) {
    return expressions.lower(node);
  }

  JavaMethod method() {
    return method;
  }

  FlowValue variable(String name) {
    return variables.get(name);
  }

  void bindVariable(String name, FlowValue value) {
    variables.put(name, value);
  }

  ExceptionTarget exceptionTarget() {
    return exceptionTarget;
  }

  void setExceptionTarget(ExceptionTarget target) {
    exceptionTarget = Objects.requireNonNull(target, "target");
  }

  GraphFragment unsupported(SyntaxNode node, String message) {
    diagnostics.add(
        new AnalysisDiagnostic(
            StableId.sha256("diagnostic", method.methodKey(), node.nodeId(), message),
            "control-flow.unsupported-syntax",
            DiagnosticStage.CONTROL_FLOW,
            DiagnosticSeverity.ERROR,
            message + ": " + node.syntaxKind(),
            node.origins()));
    return GraphFragment.empty();
  }

  OperationNode addOperation(
      SyntaxNode node,
      String role,
      OperationKind kind,
      String detail,
      List<FlowValue> operands,
      Optional<FlowValue> definedValue,
      List<String> possibleExceptions) {
    return addOperationWithId(
        operationNodeId(node, role),
        node,
        kind,
        detail,
        operands,
        definedValue,
        possibleExceptions);
  }

  OperationNode addOperationWithId(
      String operationNodeId,
      SyntaxNode node,
      OperationKind kind,
      String detail,
      List<FlowValue> operands,
      Optional<FlowValue> definedValue,
      List<String> possibleExceptions) {
    var operation =
        graph.addOperation(
            operationNodeId,
            node,
            kind,
            detail,
            operands,
            definedValue,
            possibleExceptions);
    addExceptionTransfers(operation);
    return operation;
  }

  FlowValue temporary(
      String operationNodeId, String displayName, Optional<String> staticType) {
    return graph.temporary(operationNodeId, displayName, staticType);
  }

  FlowValue value(
      String valueId,
      ValueKind kind,
      Optional<String> displayName,
      Optional<String> literal,
      Optional<String> staticType) {
    return graph.value(valueId, kind, displayName, literal, staticType);
  }

  Optional<String> staticType(SyntaxNode node) {
    return node.semanticReferences().stream()
        .map(SemanticReference::typeName)
        .flatMap(Optional::stream)
        .findFirst();
  }

  String operationNodeId(SyntaxNode node, String role) {
    return graph.operationNodeId(node, role);
  }

  void addTransfer(
      CfgEndpoint source,
      CfgEndpoint target,
      TransferKind kind,
      Optional<String> discriminator) {
    graph.addEdge(source, target, kind, discriminator);
  }

  void connectExits(
      GraphFragment fragment,
      String target,
      TransferKind kind,
      Optional<String> discriminator) {
    for (var exit : fragment.normalExits()) {
      addTransfer(
          CfgEndpoint.operation(exit),
          CfgEndpoint.operation(target),
          kind,
          discriminator);
    }
  }

  boolean connectNormalToMerge(GraphFragment fragment, String merge) {
    connectExits(fragment, merge, TransferKind.NORMAL, Optional.empty());
    if (fragment.entry().isEmpty()) {
      return true;
    }
    return !fragment.normalExits().isEmpty();
  }

  GraphFragment sequence(GraphFragment first, GraphFragment second) {
    if (first.entry().isEmpty()) {
      return second;
    }
    if (second.entry().isEmpty()) {
      return first;
    }
    if (first.normalExits().isEmpty()) {
      return new GraphFragment(first.entry(), List.of(), second.value());
    }
    connectExits(first, second.entry().orElseThrow(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(first.entry(), second.normalExits(), second.value());
  }

  private void addExceptionTransfers(OperationNode operation) {
    for (var exception : operation.possibleExceptions()) {
      var matched = false;
      for (var handler : exceptionTarget.handlers()) {
        if (isUnknownException(exception) || catches(handler.exceptionType(), exception)) {
          addTransfer(
              CfgEndpoint.operation(operation.nodeId()),
              CfgEndpoint.operation(handler.operationNodeId()),
              TransferKind.EXCEPTIONAL,
              Optional.of(exception));
          matched = true;
        }
      }
      if (exceptionTarget.includeExit() && (!matched || isUnknownException(exception))) {
        addTransfer(
            CfgEndpoint.operation(operation.nodeId()),
            CfgEndpoint.exit(),
            TransferKind.EXCEPTIONAL,
            Optional.of(exception));
      }
    }
  }

  private void finish(GraphFragment fragment) {
    if (fragment.entry().isPresent()) {
      addTransfer(
          CfgEndpoint.entry(),
          CfgEndpoint.operation(fragment.entry().orElseThrow()),
          TransferKind.NORMAL,
          Optional.empty());
      for (var exit : fragment.normalExits()) {
        addTransfer(
            CfgEndpoint.operation(exit),
            CfgEndpoint.exit(),
            TransferKind.NORMAL,
            Optional.empty());
      }
    } else {
      addTransfer(
          CfgEndpoint.entry(),
          CfgEndpoint.exit(),
          TransferKind.NORMAL,
          Optional.empty());
    }
  }

  private static boolean catches(String catchType, String exceptionType) {
    var simpleCatch = simpleName(catchType);
    var simpleException = simpleName(exceptionType);
    if (simpleCatch.equals("Throwable")
        || simpleCatch.equals("Exception")
        || simpleCatch.equals("RuntimeException")
        || simpleCatch.equals(simpleException)) {
      return true;
    }
    return simpleCatch.equals("IllegalArgumentException")
        && simpleException.equals("NumberFormatException");
  }

  private static boolean isUnknownException(String exception) {
    return exception.equals(UNKNOWN_INVOCATION_EXCEPTION)
        || exception.equals(UNKNOWN_CONSTRUCTOR_EXCEPTION);
  }

  private static String simpleName(String type) {
    var genericStart = type.indexOf('<');
    var raw = genericStart < 0 ? type : type.substring(0, genericStart);
    var lastDot = raw.lastIndexOf('.');
    return lastDot < 0 ? raw : raw.substring(lastDot + 1);
  }
}

record ExceptionHandler(String exceptionType, String operationNodeId) {}

record ExceptionTarget(List<ExceptionHandler> handlers, boolean includeExit) {
  ExceptionTarget {
    handlers = List.copyOf(handlers);
  }
}

record GraphFragment(
    Optional<String> entry,
    List<String> normalExits,
    Optional<FlowValue> value) {
  GraphFragment {
    entry = Objects.requireNonNull(entry, "entry");
    normalExits = List.copyOf(Objects.requireNonNull(normalExits, "normalExits"));
    value = Objects.requireNonNull(value, "value");
  }

  static GraphFragment empty() {
    return new GraphFragment(Optional.empty(), List.of(), Optional.empty());
  }

  static GraphFragment value(String operationNodeId, FlowValue value) {
    return new GraphFragment(
        Optional.of(operationNodeId), List.of(operationNodeId), Optional.of(value));
  }

  GraphFragment withoutValue() {
    return new GraphFragment(entry, normalExits, Optional.empty());
  }
}
