package org.aquilagraph.cfg.api;

import org.aquilagraph.cfg.internal.values.OperationPayloadValidation;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.SemanticReference;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

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
 * @param sourceRef
 *          exact qualified body and source provenance
 */
public record OperationNode(String nodeId, String graphId, OperationKind kind, String detail, List<OperationOperand> operands,
    Optional<FlowValue> definedValue, Optional<InvocationSemantics> invocationSemantics, List<SemanticReference> semanticReferences,
    List<String> possibleExceptions, List<SourceSpan> origins, GraphSourceRef sourceRef) implements CfgNode {
  /** Validates ownership and defensively copies operation data. */
  public OperationNode {
    OperationPayloadValidation.requireHeader(nodeId, graphId, kind, detail);
    operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
    Objects.requireNonNull(definedValue, "definedValue");
    Objects.requireNonNull(invocationSemantics, "invocationSemantics");
    semanticReferences = List.copyOf(Objects.requireNonNull(semanticReferences, "semanticReferences"));
    possibleExceptions = List.copyOf(Objects.requireNonNull(possibleExceptions, "possibleExceptions"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    Objects.requireNonNull(sourceRef, "sourceRef");
    OperationPayloadValidation.requireValid(kind, operands.stream().map(OperationOperand::position).toList(), definedValue.isPresent(),
        invocationSemantics, semanticReferences, possibleExceptions);
  }

}
