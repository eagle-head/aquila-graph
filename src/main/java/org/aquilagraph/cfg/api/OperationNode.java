package org.aquilagraph.cfg.api;

import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
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
    List<String> possibleExceptions, List<SourceSpan> origins, Optional<GraphSourceRef> sourceRef) implements CfgNode {
  /**
   * Creates a construction draft before source-result qualification.
   *
   * @param nodeId
   *          local draft node identifier
   * @param graphId
   *          owning draft graph identifier
   * @param kind
   *          semantic operation kind
   * @param detail
   *          uninterpreted display description
   * @param operands
   *          ordered owned input values
   * @param definedValue
   *          optional owned output value
   * @param invocationSemantics
   *          selected invocation semantics
   * @param semanticReferences
   *          ordered source references
   * @param possibleExceptions
   *          ordered possible exception categories
   * @param origins
   *          ordered contributing source locations
   */
  public OperationNode(String nodeId, String graphId, OperationKind kind, String detail, List<OperationOperand> operands,
      Optional<FlowValue> definedValue, Optional<InvocationSemantics> invocationSemantics, List<SemanticReference> semanticReferences,
      List<String> possibleExceptions, List<SourceSpan> origins) {
    this(nodeId, graphId, kind, detail, operands, definedValue, invocationSemantics, semanticReferences, possibleExceptions, origins,
        Optional.empty());
  }

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
    if (possibleExceptions.stream().distinct().count() != possibleExceptions.size()) {
      throw new IllegalArgumentException("Possible exception categories must be distinct");
    }
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    Objects.requireNonNull(sourceRef, "sourceRef");
    for (var index = 0; index < operands.size(); index++) {
      if (operands.get(index).position() != index) {
        throw new IllegalArgumentException("Operand positions must be contiguous and ordered");
      }
    }
    var invocationOperation = kind == OperationKind.INVOKE || kind == OperationKind.NEW_OBJECT;
    if (invocationOperation != invocationSemantics.isPresent()) {
      throw new IllegalArgumentException("Invocation semantics must be present exactly for invocation and construction operations");
    }
    if (kind == OperationKind.CLASS_INITIALIZATION) {
      validateInitialization(operands, definedValue, semanticReferences);
    }
  }

  private static void validateInitialization(List<OperationOperand> operands, Optional<FlowValue> definedValue,
      List<SemanticReference> semanticReferences) {
    if (!operands.isEmpty() || definedValue.isPresent() || semanticReferences.size() != 1) {
      throw new IllegalArgumentException("Class initialization requires one declaring type reference and no value operands or definition");
    }
    var target = semanticReferences.getFirst();
    if (target.referenceKind() != ReferenceKind.TYPE || target.status() != ResolutionStatus.RESOLVED || target.symbolKey().isEmpty()
        || target.typeName().isEmpty() || target.typeName().filter(String::isBlank).isPresent() || !target.typeAlternativeNames().isEmpty()
        || !target.assignableToTypeNames().isEmpty()) {
      throw new IllegalArgumentException("Class initialization requires a resolved declaring type identity without inferred hierarchy");
    }
  }
}
