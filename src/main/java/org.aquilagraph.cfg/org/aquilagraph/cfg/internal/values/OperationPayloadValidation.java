package org.aquilagraph.cfg.internal.values;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;

/** Intrinsic payload constraints shared by draft and admitted operation values. */
public final class OperationPayloadValidation {
  private OperationPayloadValidation() {
  }

  /**
   * Requires the local operation identity and descriptive header.
   *
   * @param nodeId
   *          local operation identity
   * @param graphId
   *          owning graph identity
   * @param kind
   *          semantic operation category
   * @param detail
   *          uninterpreted operation description
   */
  public static void requireHeader(String nodeId, String graphId, OperationKind kind, String detail) {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
  }

  /**
   * Requires ordered operands and the exact payload shape for the operation kind.
   *
   * @param kind
   *          operation responsibility
   * @param positions
   *          actual operand positions
   * @param hasDefinition
   *          whether an output value is retained
   * @param invocation
   *          selected invocation meaning, when applicable
   * @param references
   *          exact semantic targets
   * @param exceptions
   *          ordered possible exception categories
   */
  public static void requireValid(OperationKind kind, List<Integer> positions, boolean hasDefinition,
      Optional<InvocationSemantics> invocation, List<SemanticReference> references, List<String> exceptions) {
    if (exceptions.stream().distinct().count() != exceptions.size()) {
      throw new IllegalArgumentException("Possible exception categories must be distinct");
    }
    for (var index = 0; index < positions.size(); index++) {
      if (positions.get(index) != index) {
        throw new IllegalArgumentException("Operand positions must be contiguous and ordered");
      }
    }
    var invocationOperation = kind == OperationKind.INVOKE || kind == OperationKind.NEW_OBJECT;
    if (invocationOperation != invocation.isPresent()) {
      throw new IllegalArgumentException("Invocation semantics must be present exactly for invocation and construction operations");
    }
    if (invocation.filter(value -> value.selection() == InvocationSelection.ARRAY_CLONE).isPresent()
        && (kind != OperationKind.INVOKE || positions.size() != 1 || !hasDefinition || !references.isEmpty())) {
      throw new IllegalArgumentException("Array clone requires one receiver, an array result, and no declaration reference");
    }
    if (kind == OperationKind.CLASS_INITIALIZATION) {
      validateInitialization(!positions.isEmpty(), hasDefinition, references);
    }
  }
  private static void validateInitialization(boolean hasOperands, boolean hasDefinition, List<SemanticReference> semanticReferences) {
    if (hasOperands || hasDefinition || semanticReferences.size() != 1) {
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
