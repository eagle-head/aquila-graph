package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.aquilagraph.model.CfgEdge;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.OperationOperand;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxNode;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;

final class ControlFlowGraphConstruction {
  private static final String VALUE_ID_NAMESPACE = "value";

  private final JavaMethod method;
  private final String graphId;
  private final EntryNode entry;
  private final ExitNode exit;
  private final List<OperationNode> operationNodes = new ArrayList<>();
  private final Map<String, FlowValue> values = new LinkedHashMap<>();
  private final List<PendingEdge> pendingEdges = new ArrayList<>();
  private final Set<String> edgeIds = new HashSet<>();
  private final Deque<ExceptionTarget> exceptionTargets = new ArrayDeque<>();

  ControlFlowGraphConstruction(JavaMethod method) {
    this.method = Objects.requireNonNull(method, "method");
    graphId = StableId.sha256("cfg", method.methodKey());
    entry = new EntryNode(StableId.sha256("cfg-node", graphId, "entry"), graphId);
    exit = new ExitNode(StableId.sha256("cfg-node", graphId, "exit"), graphId);
    exceptionTargets.push(ExceptionTarget.methodExit());
  }

  FlowValue parameterValue(JavaParameter parameter) {
    Objects.requireNonNull(parameter, "parameter");
    return value(StableId.sha256(VALUE_ID_NAMESPACE, parameter.parameterKey()), ValueKind.PARAMETER, Optional.of(parameter.name()),
        Optional.empty(), Optional.of(parameter.staticType()));
  }

  FlowValue localValue(SyntaxNode node, String name, Optional<String> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, method.methodKey(), node.nodeId(), "local", name), ValueKind.LOCAL, Optional.of(name),
        Optional.empty(), staticType);
  }

  FlowValue symbolValue(SyntaxNode node, String name, Optional<String> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, method.methodKey(), node.nodeId(), "symbol"), ValueKind.SYMBOL, Optional.of(name),
        Optional.empty(), staticType);
  }

  FlowValue exceptionValue(String catchOperationId, String name, Optional<String> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, catchOperationId), ValueKind.EXCEPTION, Optional.of(name), Optional.empty(),
        staticType);
  }

  FlowValue constantValue(String operationNodeId, String literal, Optional<String> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, operationNodeId), ValueKind.CONSTANT, Optional.empty(), Optional.of(literal),
        staticType);
  }

  FlowValue temporaryValue(String operationNodeId, String displayName, Optional<String> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, operationNodeId), ValueKind.TEMPORARY, Optional.of(displayName), Optional.empty(),
        staticType);
  }

  String operationNodeId(SyntaxNode node, String role) {
    return StableId.sha256("cfg-node", graphId, "operation", StableId.sha256("operation", method.methodKey(), node.nodeId(), role));
  }

  OperationNode addOperation(SyntaxNode node, String role, OperationKind kind, String detail, List<FlowValue> operands,
      Optional<FlowValue> definedValue, List<ExceptionType> possibleExceptions) {
    return addOperationWithId(operationNodeId(node, role), node, kind, detail, operands, definedValue, possibleExceptions);
  }

  OperationNode addOperationWithId(String operationNodeId, SyntaxNode node, OperationKind kind, String detail, List<FlowValue> operands,
      Optional<FlowValue> definedValue, List<ExceptionType> possibleExceptions) {
    definedValue.ifPresent(this::addValue);
    var orderedOperands = new ArrayList<OperationOperand>();
    for (var index = 0; index < operands.size(); index++) {
      addValue(operands.get(index));
      orderedOperands.add(new OperationOperand(index, operands.get(index)));
    }
    var exceptionTypeNames = possibleExceptions.stream().map(ExceptionType::typeName).toList();
    var operation = new OperationNode(operationNodeId, graphId, kind, detail, orderedOperands, definedValue, node.semanticReferences(),
        exceptionTypeNames, node.origins());
    operationNodes.add(operation);
    addExceptionTransfers(operation, possibleExceptions);
    return operation;
  }

  void addTransfer(CfgEndpoint source, CfgEndpoint target, TransferKind kind, Optional<String> discriminator) {
    var edgeIdentity = StableId.sha256("transfer", method.methodKey(), source.canonicalForm(), target.canonicalForm(), kind.name(),
        discriminator.orElse(""));
    if (edgeIds.add(edgeIdentity)) {
      pendingEdges.add(new PendingEdge(edgeIdentity, source, target, kind, discriminator));
    }
  }

  void connectExits(GraphFragment fragment, String target, TransferKind kind, Optional<String> discriminator) {
    for (var source : fragment.normalExits()) {
      addTransfer(CfgEndpoint.operation(source), CfgEndpoint.operation(target), kind, discriminator);
    }
  }

  boolean connectNormalToMerge(GraphFragment fragment, String merge) {
    connectExits(fragment, merge, TransferKind.NORMAL, Optional.empty());
    return fragment.entry().isEmpty() || !fragment.normalExits().isEmpty();
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

  GraphFragment withExceptionHandlers(List<ExceptionHandler> handlers, Supplier<GraphFragment> action) {
    Objects.requireNonNull(handlers, "handlers");
    Objects.requireNonNull(action, "action");
    exceptionTargets.push(exceptionTargets.getFirst().nested(handlers));
    try {
      return action.get();
    } finally {
      exceptionTargets.pop();
    }
  }

  ControlFlowGraph complete(GraphFragment fragment) {
    Objects.requireNonNull(fragment, "fragment");
    if (fragment.entry().isPresent()) {
      addTransfer(CfgEndpoint.entry(), CfgEndpoint.operation(fragment.entry().orElseThrow()), TransferKind.NORMAL, Optional.empty());
      for (var source : fragment.normalExits()) {
        addTransfer(CfgEndpoint.operation(source), CfgEndpoint.exit(), TransferKind.NORMAL, Optional.empty());
      }
    } else {
      addTransfer(CfgEndpoint.entry(), CfgEndpoint.exit(), TransferKind.NORMAL, Optional.empty());
    }
    var edges = pendingEdges.stream().map(edge -> new CfgEdge(StableId.sha256("cfg-edge", graphId, edge.edgeIdentity()), graphId,
        nodeId(edge.source()), nodeId(edge.target()), edge.kind(), edge.discriminator())).toList();
    return new ControlFlowGraph(graphId, method.methodKey(), entry, operationNodes, new ArrayList<>(values.values()), exit, edges);
  }

  private FlowValue value(String valueId, ValueKind kind, Optional<String> displayName, Optional<String> literal,
      Optional<String> staticType) {
    var value = new FlowValue(valueId, graphId, kind, displayName, literal, staticType);
    addValue(value);
    return value;
  }

  private void addValue(FlowValue value) {
    Objects.requireNonNull(value, "value");
    if (!value.graphId().equals(graphId)) {
      throw new IllegalArgumentException("A flow value belongs to another CFG");
    }
    values.putIfAbsent(value.valueId(), value);
  }

  private void addExceptionTransfers(OperationNode operation, List<ExceptionType> possibleExceptions) {
    for (var exception : possibleExceptions) {
      var route = exceptionTargets.getFirst().route(exception);
      for (var handlerNodeId : route.handlerOperationNodeIds()) {
        addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.operation(handlerNodeId), TransferKind.EXCEPTIONAL,
            Optional.of(exception.typeName()));
      }
      if (route.exitsMethod()) {
        addTransfer(CfgEndpoint.operation(operation.nodeId()), CfgEndpoint.exit(), TransferKind.EXCEPTIONAL,
            Optional.of(exception.typeName()));
      }
    }
  }

  private String nodeId(CfgEndpoint endpoint) {
    return switch (endpoint.kind()) {
      case ENTRY -> entry.nodeId();
      case OPERATION -> endpoint.operationNodeId().orElseThrow();
      case EXIT -> exit.nodeId();
    };
  }

  private record PendingEdge(String edgeIdentity, CfgEndpoint source, CfgEndpoint target, TransferKind kind,
      Optional<String> discriminator) {
    private PendingEdge {
      Objects.requireNonNull(edgeIdentity, "edgeIdentity");
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(discriminator, "discriminator");
      if (source.kind() == CfgEndpoint.Kind.EXIT || target.kind() == CfgEndpoint.Kind.ENTRY) {
        throw new IllegalArgumentException("CFG edges must move from entry or operations toward operations or exit");
      }
    }
  }
}
