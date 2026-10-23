package org.aquilagraph.cfg.internal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.ConversionEvidence;

/** Applies the selected ordinary conversion after one reached value producer. */
final class UseConversionLowerer {
  private final ControlFlowGraphConstruction graph;

  UseConversionLowerer(ControlFlowGraphConstruction graph) {
    this.graph = Objects.requireNonNull(graph, "graph");
  }

  GraphFragment convert(SyntaxNode consumer, ConversionEvidence decision, GraphFragment input) {
    var converted = input;
    if (decision.checkedReferenceTarget().isPresent()) {
      converted = step(consumer, "cast", OperationKind.CAST, decision.checkedReferenceTarget().orElseThrow(), converted,
          List.of(ExceptionType.runtime("java.lang.ClassCastException")));
    }
    if (decision.unboxedType().isPresent()) {
      return step(consumer, conversionRole("unbox", decision), OperationKind.UNBOX, decision.effectivePrimitiveType().orElseThrow(),
          converted, List.of(ExceptionType.runtime("java.lang.NullPointerException")));
    }
    if (decision.boxedType().isPresent()) {
      return step(consumer, conversionRole("box", decision), OperationKind.BOX, decision.selectedTarget(), converted,
          List.of(allocationFailure()));
    }
    return converted;
  }

  private GraphFragment step(SyntaxNode consumer, String role, OperationKind kind, JavaTypeIdentity target, GraphFragment input,
      List<ExceptionType> exceptions) {
    if (input.entry().isPresent() && input.normalExits().isEmpty()) {
      return input;
    }
    var operationId = graph.operationNodeId(consumer, role);
    var result = graph.temporaryValue(operationId, kind.name(), Optional.of(target));
    var operation = graph.addOperationWithId(operationId, consumer, kind, kind.name(), List.of(input.value().orElseThrow()),
        Optional.of(result), exceptions, List.of());
    graph.connectExits(input, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(input.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()), Optional.of(result),
        input.yieldExits());
  }

  private static String conversionRole(String step, ConversionEvidence decision) {
    var role = decision.role().stableId();
    return step + ":v1:" + role.getBytes(StandardCharsets.UTF_8).length + ':' + role + ':' + decision.position();
  }

  static ExceptionType allocationFailure() {
    return ExceptionType.resolved("java.lang.OutOfMemoryError",
        List.of("java.lang.VirtualMachineError", "java.lang.Error", "java.lang.Throwable", "java.lang.Object"));
  }
}
