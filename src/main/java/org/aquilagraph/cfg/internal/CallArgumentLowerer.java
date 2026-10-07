package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.FlowValue;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;

/** Evaluates selected fixed arguments and any ordered variable-arity packing. */
final class CallArgumentLowerer {
  private static final JavaTypeIdentity INDEX_TYPE = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");

  private final SyntaxGraphLowerer syntax;
  private final ControlFlowGraphConstruction graph;

  CallArgumentLowerer(SyntaxGraphLowerer syntax, ControlFlowGraphConstruction graph) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.graph = Objects.requireNonNull(graph, "graph");
  }

  Evaluation lower(SyntaxNode call, GraphFragment prefix, List<FlowValue> prefixOperands) {
    var arity = syntax.invocationArity(call);
    var count = call.children(SyntaxRole.ARGUMENT).size();
    var fixedCount = arity.mode() == InvocationApplicabilityMode.FIXED ? count : arity.formalCount() - 1;
    var evaluation = prefix;
    var operands = new ArrayList<>(prefixOperands);
    operands.addAll(syntax.enumConstructorContextOperands(call));
    for (var position = 0; position < fixedCount; position++) {
      var argument = syntax.lowerUse(call, SyntaxRole.ARGUMENT, position);
      evaluation = graph.sequence(evaluation, argument);
      operands.add(argument.value().orElseThrow());
    }
    if (arity.mode() == InvocationApplicabilityMode.FIXED) {
      return new Evaluation(evaluation, operands);
    }
    var arrayOperationId = graph.operationNodeId(call, "varargs-array");
    var array = graph.temporaryValue(arrayOperationId, "varargs", arity.erasedArrayType());
    var allocation = graph.addOperationWithId(arrayOperationId, call, OperationKind.VARARGS_ARRAY, "varargs", List.of(), Optional.of(array),
        List.of(UseConversionLowerer.allocationFailure()), List.of());
    evaluation = graph.sequence(evaluation, GraphFragment.value(allocation.nodeId(), array));
    for (var position = fixedCount; position < count; position++) {
      var argument = syntax.lowerUse(call, SyntaxRole.ARGUMENT, position);
      evaluation = graph.sequence(evaluation, argument);
      var storeId = graph.operationNodeId(call, "varargs-element:" + position);
      var index = graph.constantValue(storeId, Integer.toString(position - fixedCount), Optional.of(INDEX_TYPE));
      var store = graph.addOperationWithId(storeId, call, OperationKind.WRITE, "varargs element",
          List.of(array, index, argument.value().orElseThrow()), Optional.empty(), List.of(), List.of());
      evaluation = graph.sequence(evaluation, GraphFragment.value(store.nodeId(), array));
    }
    operands.add(array);
    return new Evaluation(evaluation, operands);
  }

  record Evaluation(GraphFragment fragment, List<FlowValue> operands) {
    Evaluation {
      Objects.requireNonNull(fragment, "fragment");
      operands = List.copyOf(operands);
    }
  }
}
