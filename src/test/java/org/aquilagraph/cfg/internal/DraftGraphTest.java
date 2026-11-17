package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.api.ValueKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.JavaTypeIdentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DraftGraphTest {
  @Test
  void givenClosedDraftTopology_whenConstructed_thenEntryOperationExitAndTransfersRetainTheirOrder() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");
    var operationNode =
        new DraftOperation(
            "node",
            "graph",
            OperationKind.NO_OP,
            "test",
            List.of(),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of());
    var graph =
        new DraftGraph(
            "graph",
            "method",
            entry,
            List.of(operationNode),
            List.of(),
            exit,
            List.of(
                new DraftEdge(
                    "edge-1", "graph", "entry", "node", TransferKind.NORMAL, Optional.empty()),
                new DraftEdge(
                    "edge-2", "graph", "node", "exit", TransferKind.RETURN, Optional.empty())));

    // Act
    var nodes = graph.nodes();
    var edges = graph.edges();

    // Assert
    assertEquals(List.of(entry, operationNode, exit), nodes);
    assertEquals(List.of("edge-1", "edge-2"), edges.stream().map(DraftEdge::edgeId).toList());
    assertEquals(List.of("entry", "node"), edges.stream().map(DraftEdge::sourceNodeId).toList());
    assertEquals(List.of("node", "exit"), edges.stream().map(DraftEdge::targetNodeId).toList());
  }

  @Test
  void givenExitWithoutIncomingEdges_whenGraphIsCreated_thenUnreachableExitIsAllowed() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");

    // Act
    var graph =
        new DraftGraph(
            "graph", "method", entry, List.of(), List.of(), exit, List.of());

    // Assert
    assertTrue(graph.edges().stream().filter(edge -> edge.targetNodeId().equals(exit.nodeId())).map(edge -> graph.nodes().stream().filter(node -> node.nodeId().equals(edge.sourceNodeId())).findFirst().orElseThrow()).toList().isEmpty());
  }

  @Test
  void givenForeignEdgeEndpoint_whenGraphIsCreated_thenOwnershipViolationIsRejected() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");
    var foreignEdge =
        new DraftEdge(
            "edge", "graph", "entry", "foreign", TransferKind.NORMAL, Optional.empty());

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new DraftGraph(
                    "graph",
                    "method",
                    entry,
                    List.of(),
                    List.of(),
                    exit,
                    List.of(foreignEdge)));

    // Assert
    assertEquals("CFG edges must be unique and connect nodes in the same graph", exception.getMessage());
  }

  @Test
  void givenOperationReferencingForeignValue_whenGraphIsCreated_thenValueOwnershipIsRejected() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");
    var ownedValue =
        new DraftValue(
            "value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var foreignValue =
        new DraftValue(
            "value", "foreign", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operationNode =
        new DraftOperation(
            "node",
            "graph",
            OperationKind.NO_OP,
            "test",
            List.of(new DraftOperand(0, foreignValue)),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of());

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new DraftGraph(
                    "graph",
                    "method",
                    entry,
                    List.of(operationNode),
                    List.of(ownedValue),
                    exit,
                    List.of()));

    // Assert
    assertEquals("Operations may reference only values owned by the CFG", exception.getMessage());
  }

  @Test
  void givenRepeatedMemberIdentities_whenGraphIsCreated_thenEachDuplicateFamilyIsRejected() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");
    var value = new DraftValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operation = operation("node", "graph", List.of(), Optional.empty());
    var edge = new DraftEdge("edge", "graph", "entry", "node", TransferKind.NORMAL, Optional.empty());

    // Act
    var duplicateValue = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(operation), List.of(value, value), exit, List.of(edge)));
    var duplicateOperation = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(operation, operation), List.of(value), exit, List.of(edge)));
    var duplicateExit = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(operation), List.of(value), new DraftExit("entry", "graph"), List.of(edge)));
    var duplicateEdge = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(operation), List.of(value), exit, List.of(edge, edge)));

    // Assert
    assertEquals("CFG values must be unique and owned by the graph", duplicateValue.getMessage());
    assertEquals("CFG nodes must be unique and owned by the graph", duplicateOperation.getMessage());
    assertEquals("CFG nodes must be unique and owned by the graph", duplicateExit.getMessage());
    assertEquals("CFG edges must be unique and connect nodes in the same graph", duplicateEdge.getMessage());
  }

  @Test
  void givenForeignMembers_whenGraphIsCreated_thenEachOwnershipFamilyIsRejected() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");
    var value = new DraftValue("value", "foreign", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operation = operation("node", "foreign", List.of(), Optional.empty());
    var edge = new DraftEdge("edge", "foreign", "entry", "exit", TransferKind.NORMAL, Optional.empty());

    // Act
    var foreignValue = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(), List.of(value), exit, List.of()));
    var foreignOperation = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(operation), List.of(), exit, List.of()));
    var foreignEntry = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", new DraftEntry("entry", "foreign"), List.of(), List.of(), exit, List.of()));
    var foreignExit = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(), List.of(), new DraftExit("exit", "foreign"), List.of()));
    var foreignEdge = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(), List.of(), exit, List.of(edge)));

    // Assert
    assertEquals("CFG values must be unique and owned by the graph", foreignValue.getMessage());
    assertEquals("CFG nodes must be unique and owned by the graph", foreignOperation.getMessage());
    assertEquals("CFG nodes must be unique and owned by the graph", foreignEntry.getMessage());
    assertEquals("CFG nodes must be unique and owned by the graph", foreignExit.getMessage());
    assertEquals("CFG edges must be unique and connect nodes in the same graph", foreignEdge.getMessage());
  }

  @Test
  void givenUnequalValueWithOwnedIdentity_whenGraphIsCreated_thenOperandsAndDefinitionsRequireTheCompleteValue() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");
    var owned = new DraftValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var conflicting = new DraftValue("value", "graph", ValueKind.TEMPORARY, Optional.of("different"), Optional.empty(), Optional.empty());
    var operand = operation("operand", "graph", List.of(new DraftOperand(0, conflicting)), Optional.empty());
    var definition = operation("definition", "graph", List.of(), Optional.of(conflicting));

    // Act
    var operandFailure = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(operand), List.of(owned), exit, List.of()));
    var definitionFailure = assertThrows(IllegalArgumentException.class,
        () -> new DraftGraph("graph", "method", entry, List.of(definition), List.of(owned), exit, List.of()));

    // Assert
    assertEquals("Operations may reference only values owned by the CFG", operandFailure.getMessage());
    assertEquals("Operations may reference only values owned by the CFG", definitionFailure.getMessage());
  }

  @Test
  void givenMutableMemberLists_whenSourcesAndResultsAreModified_thenGraphRetainsImmutableOrderedData() {
    // Arrange
    var entry = new DraftEntry("entry", "graph");
    var exit = new DraftExit("exit", "graph");
    var value = new DraftValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operation = operation("node", "graph", List.of(), Optional.of(value));
    var edge = new DraftEdge("edge", "graph", "entry", "node", TransferKind.NORMAL, Optional.empty());
    var operations = new ArrayList<>(List.of(operation));
    var values = new ArrayList<>(List.of(value));
    var edges = new ArrayList<>(List.of(edge));
    var graph = new DraftGraph("graph", "method", entry, operations, values, exit, edges);

    // Act
    operations.clear();
    values.clear();
    edges.clear();

    // Assert
    assertEquals(List.of(operation), graph.operationNodes());
    assertEquals(List.of(value), graph.values());
    assertEquals(List.of(edge), graph.edges());
    assertEquals(List.of(entry, operation, exit), graph.nodes());
    assertEquals(3, graph.nodes().size());
    assertThrows(UnsupportedOperationException.class, () -> graph.operationNodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.values().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
  }

  @Test
  void givenGappedOrReversedOperandPositions_whenOperationIsCreated_thenOrderingViolationIsRejected() {
    // Arrange
    var value = new DraftValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var gapped = List.of(new DraftOperand(1, value));
    var reversed = List.of(new DraftOperand(1, value), new DraftOperand(0, value));

    // Act
    var gapFailure = assertThrows(IllegalArgumentException.class, () -> operation("gap", "graph", gapped, Optional.empty()));
    var orderFailure = assertThrows(IllegalArgumentException.class, () -> operation("reverse", "graph", reversed, Optional.empty()));

    // Assert
    assertEquals("Operand positions must be contiguous and ordered", gapFailure.getMessage());
    assertEquals("Operand positions must be contiguous and ordered", orderFailure.getMessage());
  }

  private static DraftOperation operation(String nodeId, String graphId, List<DraftOperand> operands, Optional<DraftValue> definition) {
    return new DraftOperation(nodeId, graphId, OperationKind.NO_OP, "test", operands, definition, Optional.empty(), List.of(), List.of(), List.of());
  }

  @Test
  void givenInvokeOperationWithoutInvocationSemantics_whenOperationIsCreated_thenInputIsRejected() {
    // Arrange

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new DraftOperation("node", "graph", OperationKind.INVOKE, "call", List.of(), Optional.empty(), Optional.empty(),
            List.of(), List.of(), List.of()));

    // Assert
    assertEquals("Invocation semantics must be present exactly for invocation and construction operations", exception.getMessage());
  }

  @Test
  void givenUnrelatedOperationWithInvocationSemantics_whenOperationIsCreated_thenInputIsRejected() {
    // Arrange
    var qualifyingType = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Example", "Example"),
        Optional.of("type:Example"));
    var semantics = new InvocationSemantics(InvocationMode.VIRTUAL, qualifyingType, InvocationForm.INSTANCE_IMPLICIT,
        InvocationSelection.NAMED_EXECUTABLE);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new DraftOperation("node", "graph", OperationKind.READ, "read", List.of(), Optional.empty(), Optional.of(semantics),
            List.of(), List.of(), List.of()));

    // Assert
    assertEquals("Invocation semantics must be present exactly for invocation and construction operations", exception.getMessage());
  }
}
