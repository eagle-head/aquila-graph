package org.aquilagraph.cfg.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.function.Supplier;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.cfg.api.CfgEdge;
import org.aquilagraph.cfg.api.ControlFlowGraph;
import org.aquilagraph.cfg.api.EntryNode;
import org.aquilagraph.cfg.api.ExitNode;
import org.aquilagraph.cfg.api.FlowValue;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaParameter;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.OperationNode;
import org.aquilagraph.cfg.api.OperationOperand;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.cfg.api.ValueKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.CompilerInsertedInvocation;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxNodeKey;

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
  private final Optional<GraphQualification> qualification;
  private final Map<String, SyntaxNode> creatingNodes = new LinkedHashMap<>();
  private final Map<String, String> generatedRoles = new LinkedHashMap<>();

  ControlFlowGraphConstruction(JavaMethod method) {
    this(method, Optional.empty());
  }

  ControlFlowGraphConstruction(JavaMethod method, Optional<GraphQualification> qualification) {
    this.method = Objects.requireNonNull(method, "method");
    this.qualification = Objects.requireNonNull(qualification, "qualification");
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

  FlowValue enumContextValue(String enumOwnerKey, String role, JavaTypeIdentity staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, method.methodKey(), enumOwnerKey, "enum-context", role), ValueKind.IMPLICIT_PARAMETER,
        Optional.of(role), Optional.empty(), Optional.of(staticType));
  }

  String insertedOperationId(CompilerInsertedInvocation insertion, String role) {
    var occurrence = SyntaxNodeKey.from(insertion.sourceFileKey(), insertion.compilerPath(), JavaSourceKind.METHOD_INVOCATION).value();
    return operationId(occurrence, role);
  }

  FlowValue insertedReceiver(CompilerInsertedInvocation insertion) {
    var occurrence = SyntaxNodeKey.from(insertion.sourceFileKey(), insertion.compilerPath(), JavaSourceKind.METHOD_INVOCATION).value();
    return value(StableId.sha256(VALUE_ID_NAMESPACE, method.methodKey(), occurrence, "receiver", "super"), ValueKind.RECEIVER,
        Optional.of("super"), Optional.empty(), Optional.empty());
  }

  OperationNode insertedOperation(String operationId, SyntaxNode body, String role, OperationKind kind, String detail,
      List<FlowValue> operands, Optional<FlowValue> definedValue, List<ExceptionType> exceptions, Optional<InvocationSemantics> semantics,
      List<SemanticReference> references) {
    var operation = addOperationWithSemantics(operationId, body, kind, detail, operands, definedValue, exceptions, semantics, references);
    var retained = new OperationNode(operation.nodeId(), operation.graphId(), operation.kind(), operation.detail(), operation.operands(),
        operation.definedValue(), operation.invocationSemantics(), operation.semanticReferences(), operation.possibleExceptions(),
        List.of());
    operationNodes.set(operationNodes.size() - 1, retained);
    generatedRoles.put(operationId, role);
    return retained;
  }

  FlowValue localValue(String declarationKey, String name, Optional<JavaTypeIdentity> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, declarationKey), ValueKind.LOCAL, Optional.of(name), Optional.empty(), staticType);
  }

  FlowValue symbolValue(String declarationKey, String name, Optional<JavaTypeIdentity> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, declarationKey), ValueKind.SYMBOL, Optional.of(name), Optional.empty(), staticType);
  }

  FlowValue receiverValue(SyntaxNode node, String name, Optional<JavaTypeIdentity> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, method.methodKey(), node.nodeId(), "receiver", name), ValueKind.RECEIVER,
        Optional.of(name), Optional.empty(), staticType);
  }

  FlowValue exceptionValue(String catchOperationId, String name, Optional<JavaTypeIdentity> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, catchOperationId), ValueKind.EXCEPTION, Optional.of(name), Optional.empty(),
        staticType);
  }

  FlowValue constantValue(String operationNodeId, String literal, Optional<JavaTypeIdentity> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, operationNodeId), ValueKind.CONSTANT, Optional.empty(), Optional.of(literal),
        staticType);
  }

  FlowValue temporaryValue(String operationNodeId, String displayName, Optional<JavaTypeIdentity> staticType) {
    return value(StableId.sha256(VALUE_ID_NAMESPACE, operationNodeId), ValueKind.TEMPORARY, Optional.of(displayName), Optional.empty(),
        staticType);
  }

  String operationNodeId(SyntaxNode node, String role) {
    return operationId(node.nodeId(), role);
  }

  private String operationId(String occurrenceKey, String role) {
    return StableId.sha256("cfg-node", graphId, "operation", StableId.sha256("operation", method.methodKey(), occurrenceKey, role));
  }

  OperationNode addOperation(SyntaxNode node, String role, OperationKind kind, String detail, List<FlowValue> operands,
      Optional<FlowValue> definedValue, List<ExceptionType> possibleExceptions) {
    return addOperationWithId(operationNodeId(node, role), node, kind, detail, operands, definedValue, possibleExceptions);
  }

  OperationNode addOperationWithId(String operationNodeId, SyntaxNode node, OperationKind kind, String detail, List<FlowValue> operands,
      Optional<FlowValue> definedValue, List<ExceptionType> possibleExceptions) {
    return addOperationWithId(operationNodeId, node, kind, detail, operands, definedValue, possibleExceptions,
        node.semanticReference().stream().toList());
  }

  OperationNode addOperationWithId(String operationNodeId, SyntaxNode node, OperationKind kind, String detail, List<FlowValue> operands,
      Optional<FlowValue> definedValue, List<ExceptionType> possibleExceptions, List<SemanticReference> semanticReferences) {
    return addOperationWithSemantics(operationNodeId, node, kind, detail, operands, definedValue, possibleExceptions, Optional.empty(),
        semanticReferences);
  }

  OperationNode addInvocationOperationWithId(String operationNodeId, SyntaxNode node, OperationKind kind, String detail,
      List<FlowValue> operands, Optional<FlowValue> definedValue, List<ExceptionType> possibleExceptions,
      InvocationSemantics invocationSemantics) {
    return addOperationWithSemantics(operationNodeId, node, kind, detail, operands, definedValue, possibleExceptions,
        Optional.of(Objects.requireNonNull(invocationSemantics, "invocationSemantics")), node.semanticReference().stream().toList());
  }

  GraphFragment classInitialization(SyntaxNode site, JavaTypeReference target) {
    if (target.identity().category() != JavaTypeCategory.DECLARED || target.declarationKey().isEmpty()) {
      throw new IllegalStateException("An admitted initialization target requires its selected declaring type identity");
    }
    var reference = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, target.declarationKey(),
        Optional.of(target.identity().erasedName()), List.of(), List.of());
    var operationId = operationNodeId(site, "class-initialization");
    var operation = addOperationWithId(operationId, site, OperationKind.CLASS_INITIALIZATION, target.identity().displayName(), List.of(),
        Optional.empty(), List.of(ExceptionType.unknown("unchecked-class-initialization")), List.of(reference));
    return new GraphFragment(Optional.of(operation.nodeId()), List.of(operation.nodeId()), Optional.empty());
  }

  private OperationNode addOperationWithSemantics(String operationNodeId, SyntaxNode node, OperationKind kind, String detail,
      List<FlowValue> operands, Optional<FlowValue> definedValue, List<ExceptionType> possibleExceptions,
      Optional<InvocationSemantics> invocationSemantics, List<SemanticReference> semanticReferences) {
    definedValue.ifPresent(this::addValue);
    var orderedOperands = new ArrayList<OperationOperand>();
    for (var index = 0; index < operands.size(); index++) {
      addValue(operands.get(index));
      orderedOperands.add(new OperationOperand(index, operands.get(index)));
    }
    var exceptionOutcomes = possibleExceptions.stream().flatMap(exception -> exception.outcomes().stream()).toList();
    var exceptionTypeNames = exceptionOutcomes.stream().map(ExceptionType::typeName).distinct().toList();
    var operation = new OperationNode(operationNodeId, graphId, kind, detail, orderedOperands, definedValue, invocationSemantics,
        semanticReferences, exceptionTypeNames, node.origin().stream().toList());
    operationNodes.add(operation);
    if (creatingNodes.putIfAbsent(operation.nodeId(), node) != null) {
      throw new IllegalArgumentException("One operation key cannot have two creating occurrences");
    }
    addExceptionTransfers(operation, exceptionOutcomes);
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
      return new GraphFragment(first.entry(), List.of(), first.value(), first.yieldExits());
    }
    connectExits(first, second.entry().orElseThrow(), TransferKind.NORMAL, Optional.empty());
    var yields = new ArrayList<>(first.yieldExits());
    yields.addAll(second.yieldExits());
    return new GraphFragment(first.entry(), second.normalExits(), second.value(), yields);
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
    if (!fragment.yieldExits().isEmpty()) {
      throw new IllegalStateException("Method assembly cannot retain unconsumed switch yields");
    }
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
    var draft = new ControlFlowGraph(graphId, method.methodKey(), entry, operationNodes, new ArrayList<>(values.values()), exit, edges);
    return qualification.map(value -> value.qualify(draft, creatingNodes, generatedRoles)).orElse(draft);
  }

  private FlowValue value(String valueId, ValueKind kind, Optional<String> displayName, Optional<String> literal,
      Optional<JavaTypeIdentity> staticType) {
    var value = new FlowValue(valueId, graphId, kind, displayName, literal, staticType);
    addValue(value);
    return value;
  }

  private void addValue(FlowValue value) {
    Objects.requireNonNull(value, "value");
    if (!value.graphId().equals(graphId)) {
      throw new IllegalArgumentException("A flow value belongs to another CFG");
    }
    var previous = values.putIfAbsent(value.valueId(), value);
    if (previous != null && !previous.equals(value)) {
      throw new IllegalArgumentException("A flow value identity cannot denote unequal typed values");
    }
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
