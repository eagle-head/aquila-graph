package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OperationNode(
    String nodeId,
    String graphId,
    OperationKind kind,
    String detail,
    List<OperationOperand> operands,
    Optional<FlowValue> definedValue,
    List<SemanticReference> semanticReferences,
    List<String> possibleExceptions,
    List<SourceSpan> origins)
    implements CfgNode {
  public OperationNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(kind, "kind");
    detail = Objects.requireNonNull(detail, "detail");
    operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
    definedValue = Objects.requireNonNull(definedValue, "definedValue");
    semanticReferences =
        List.copyOf(Objects.requireNonNull(semanticReferences, "semanticReferences"));
    possibleExceptions =
        List.copyOf(Objects.requireNonNull(possibleExceptions, "possibleExceptions"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    for (var index = 0; index < operands.size(); index++) {
      if (operands.get(index).position() != index) {
        throw new IllegalArgumentException("Operand positions must be contiguous and ordered");
      }
    }
  }
}
