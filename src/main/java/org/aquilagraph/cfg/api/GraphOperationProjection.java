package org.aquilagraph.cfg.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Remaps draft operation values into their admitted graph ownership. */
final class GraphOperationProjection {
  private GraphOperationProjection() {
  }

  static OperationNode remap(OperationNode operation, String graphId, String nodeId, Map<String, FlowValue> values,
      Optional<GraphSourceRef> source) {
    return new OperationNode(nodeId, graphId, operation.kind(), operation.detail(),
        operation.operands().stream().map(operand -> new OperationOperand(operand.position(), owned(values, operand.value().valueId())))
            .toList(),
        operation.definedValue().map(value -> owned(values, value.valueId())), operation.invocationSemantics(),
        operation.semanticReferences(), operation.possibleExceptions(), operation.origins(), source);
  }

  private static FlowValue owned(Map<String, FlowValue> values, String key) {
    return Objects.requireNonNull(values.get(key), "owned projected value");
  }
}
