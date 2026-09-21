package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Semantic operation node in a method control-flow graph.
 *
 * @param nodeId
 *          stable node identifier
 * @param graphId
 *          owning graph identifier
 * @param kind
 *          semantic operation kind
 * @param detail
 *          human-readable operation description that is never interpreted as semantics
 * @param operands
 *          ordered input values
 * @param definedValue
 *          optional value produced by the operation
 * @param invocationSemantics
 *          compile-time invocation semantics for invocation and construction operations
 * @param semanticReferences
 *          compiler semantic references
 * @param possibleExceptions
 *          exception types the operation may transfer
 * @param origins
 *          contributing source locations
 */
public record OperationNode(String nodeId, String graphId, OperationKind kind, String detail, List<OperationOperand> operands,
    Optional<FlowValue> definedValue, Optional<InvocationSemantics> invocationSemantics, List<SemanticReference> semanticReferences,
    List<String> possibleExceptions, List<SourceSpan> origins) implements CfgNode {
  /** Validates ownership and defensively copies operation data. */
  public OperationNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
    operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
    Objects.requireNonNull(definedValue, "definedValue");
    Objects.requireNonNull(invocationSemantics, "invocationSemantics");
    semanticReferences = List.copyOf(Objects.requireNonNull(semanticReferences, "semanticReferences"));
    possibleExceptions = List.copyOf(Objects.requireNonNull(possibleExceptions, "possibleExceptions"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    for (var index = 0; index < operands.size(); index++) {
      if (operands.get(index).position() != index) {
        throw new IllegalArgumentException("Operand positions must be contiguous and ordered");
      }
    }
    var invocationOperation = kind == OperationKind.INVOKE || kind == OperationKind.NEW_OBJECT;
    if (invocationOperation != invocationSemantics.isPresent()) {
      throw new IllegalArgumentException("Invocation semantics must be present exactly for invocation and construction operations");
    }
  }
}
