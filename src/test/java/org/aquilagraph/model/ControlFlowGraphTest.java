package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ControlFlowGraphTest {
  @Test
  void givenOwnedGraphNodes_whenAdjacencyIsQueried_thenPredecessorsAndSuccessorsAreDeterministic() {
    // Arrange
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var operationNode =
        new OperationNode(
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
        new ControlFlowGraph(
            "graph",
            "method",
            entry,
            List.of(operationNode),
            List.of(),
            exit,
            List.of(
                new CfgEdge(
                    "edge-1", "graph", "entry", "node", TransferKind.NORMAL, Optional.empty()),
                new CfgEdge(
                    "edge-2", "graph", "node", "exit", TransferKind.RETURN, Optional.empty())));

    // Act
    var entrySuccessors = graph.successors(entry);
    var exitPredecessors = graph.predecessors(exit);

    // Assert
    assertEquals(List.of(operationNode), entrySuccessors);
    assertEquals(List.of(operationNode), exitPredecessors);
    assertEquals(1, graph.nodes().stream().filter(EntryNode.class::isInstance).count());
    assertEquals(1, graph.nodes().stream().filter(ExitNode.class::isInstance).count());
  }

  @Test
  void givenExitWithoutIncomingEdges_whenGraphIsCreated_thenUnreachableExitIsAllowed() {
    // Arrange
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");

    // Act
    var graph =
        new ControlFlowGraph(
            "graph", "method", entry, List.of(), List.of(), exit, List.of());

    // Assert
    assertTrue(graph.predecessors(exit).isEmpty());
  }

  @Test
  void givenForeignEdgeEndpoint_whenGraphIsCreated_thenOwnershipViolationIsRejected() {
    // Arrange
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var foreignEdge =
        new CfgEdge(
            "edge", "graph", "entry", "foreign", TransferKind.NORMAL, Optional.empty());

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ControlFlowGraph(
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
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var ownedValue =
        new FlowValue(
            "value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var foreignValue =
        new FlowValue(
            "value", "foreign", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operationNode =
        new OperationNode(
            "node",
            "graph",
            OperationKind.NO_OP,
            "test",
            List.of(new OperationOperand(0, foreignValue)),
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
                new ControlFlowGraph(
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
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var value = new FlowValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operation = operation("node", "graph", List.of(), Optional.empty());
    var edge = new CfgEdge("edge", "graph", "entry", "node", TransferKind.NORMAL, Optional.empty());

    // Act
    var duplicateValue = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(operation), List.of(value, value), exit, List.of(edge)));
    var duplicateOperation = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(operation, operation), List.of(value), exit, List.of(edge)));
    var duplicateExit = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(operation), List.of(value), new ExitNode("entry", "graph"), List.of(edge)));
    var duplicateEdge = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(operation), List.of(value), exit, List.of(edge, edge)));

    // Assert
    assertEquals("CFG values must be unique and owned by the graph", duplicateValue.getMessage());
    assertEquals("CFG nodes must be unique and owned by the graph", duplicateOperation.getMessage());
    assertEquals("CFG nodes must be unique and owned by the graph", duplicateExit.getMessage());
    assertEquals("CFG edges must be unique and connect nodes in the same graph", duplicateEdge.getMessage());
  }

  @Test
  void givenForeignMembers_whenGraphIsCreated_thenEachOwnershipFamilyIsRejected() {
    // Arrange
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var value = new FlowValue("value", "foreign", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operation = operation("node", "foreign", List.of(), Optional.empty());
    var edge = new CfgEdge("edge", "foreign", "entry", "exit", TransferKind.NORMAL, Optional.empty());

    // Act
    var foreignValue = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(), List.of(value), exit, List.of()));
    var foreignOperation = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(operation), List.of(), exit, List.of()));
    var foreignEntry = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", new EntryNode("entry", "foreign"), List.of(), List.of(), exit, List.of()));
    var foreignExit = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(), List.of(), new ExitNode("exit", "foreign"), List.of()));
    var foreignEdge = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(), List.of(), exit, List.of(edge)));

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
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var owned = new FlowValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var conflicting = new FlowValue("value", "graph", ValueKind.TEMPORARY, Optional.of("different"), Optional.empty(), Optional.empty());
    var operand = operation("operand", "graph", List.of(new OperationOperand(0, conflicting)), Optional.empty());
    var definition = operation("definition", "graph", List.of(), Optional.of(conflicting));

    // Act
    var operandFailure = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(operand), List.of(owned), exit, List.of()));
    var definitionFailure = assertThrows(IllegalArgumentException.class,
        () -> new ControlFlowGraph("graph", "method", entry, List.of(definition), List.of(owned), exit, List.of()));

    // Assert
    assertEquals("Operations may reference only values owned by the CFG", operandFailure.getMessage());
    assertEquals("Operations may reference only values owned by the CFG", definitionFailure.getMessage());
  }

  @Test
  void givenMutableMemberLists_whenSourcesAndResultsAreModified_thenGraphRetainsImmutableOrderedData() {
    // Arrange
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var value = new FlowValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var operation = operation("node", "graph", List.of(), Optional.of(value));
    var edge = new CfgEdge("edge", "graph", "entry", "node", TransferKind.NORMAL, Optional.empty());
    var operations = new ArrayList<>(List.of(operation));
    var values = new ArrayList<>(List.of(value));
    var edges = new ArrayList<>(List.of(edge));
    var graph = new ControlFlowGraph("graph", "method", entry, operations, values, exit, edges);

    // Act
    operations.clear();
    values.clear();
    edges.clear();

    // Assert
    assertEquals(List.of(operation), graph.operationNodes());
    assertEquals(List.of(value), graph.values());
    assertEquals(List.of(edge), graph.edges());
    assertEquals(List.of(entry, operation, exit), graph.nodes());
    assertEquals(3, graph.nodeCount());
    assertThrows(UnsupportedOperationException.class, () -> graph.operationNodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.values().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.successors(entry).clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.predecessors(operation).clear());
  }

  @Test
  void givenSameIdentityAndUnknownOrForeignQueryNodes_whenAdjacencyIsQueried_thenCanonicalOrderAndOwnershipArePreserved() {
    // Arrange
    var entry = new EntryNode("entry", "graph");
    var exit = new ExitNode("exit", "graph");
    var operation = operation("node", "graph", List.of(), Optional.empty());
    var graph = new ControlFlowGraph("graph", "method", entry, List.of(operation), List.of(), exit,
        List.of(new CfgEdge("normal", "graph", "node", "exit", TransferKind.NORMAL, Optional.empty()),
            new CfgEdge("exceptional", "graph", "node", "exit", TransferKind.EXCEPTIONAL, Optional.of("failure"))));
    var sameIdentity = new OperationNode("node", "graph", OperationKind.READ, "different", List.of(), Optional.empty(), Optional.empty(),
        List.of(), List.of(), List.of());
    var unknown = operation("unknown", "graph", List.of(), Optional.empty());
    var foreign = operation("node", "foreign", List.of(), Optional.empty());

    // Act
    var successors = graph.successors(sameIdentity);
    var predecessors = graph.predecessors(new ExitNode("exit", "graph"));

    // Assert
    assertEquals(List.of(exit, exit), successors);
    assertEquals(List.of(operation, operation), predecessors);
    assertTrue(graph.successors(unknown).isEmpty());
    assertTrue(graph.predecessors(unknown).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> graph.successors(foreign));
    assertThrows(IllegalArgumentException.class, () -> graph.predecessors(foreign));
    assertThrows(NullPointerException.class, () -> graph.successors(null));
    assertThrows(NullPointerException.class, () -> graph.predecessors(null));
  }

  @Test
  void givenGappedOrReversedOperandPositions_whenOperationIsCreated_thenOrderingViolationIsRejected() {
    // Arrange
    var value = new FlowValue("value", "graph", ValueKind.TEMPORARY, Optional.empty(), Optional.empty(), Optional.empty());
    var gapped = List.of(new OperationOperand(1, value));
    var reversed = List.of(new OperationOperand(1, value), new OperationOperand(0, value));

    // Act
    var gapFailure = assertThrows(IllegalArgumentException.class, () -> operation("gap", "graph", gapped, Optional.empty()));
    var orderFailure = assertThrows(IllegalArgumentException.class, () -> operation("reverse", "graph", reversed, Optional.empty()));

    // Assert
    assertEquals("Operand positions must be contiguous and ordered", gapFailure.getMessage());
    assertEquals("Operand positions must be contiguous and ordered", orderFailure.getMessage());
  }

  private static OperationNode operation(String nodeId, String graphId, List<OperationOperand> operands, Optional<FlowValue> definition) {
    return new OperationNode(nodeId, graphId, OperationKind.NO_OP, "test", operands, definition, Optional.empty(), List.of(), List.of(), List.of());
  }

  @Test
  void givenInvokeOperationWithoutInvocationSemantics_whenOperationIsCreated_thenInputIsRejected() {
    // Arrange

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new OperationNode("node", "graph", OperationKind.INVOKE, "call", List.of(), Optional.empty(), Optional.empty(),
            List.of(), List.of(), List.of()));

    // Assert
    assertEquals("Invocation semantics must be present exactly for invocation and construction operations", exception.getMessage());
  }

  @Test
  void givenUnrelatedOperationWithInvocationSemantics_whenOperationIsCreated_thenInputIsRejected() {
    // Arrange
    var qualifyingType = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Example", "Example"),
        Optional.of("type:Example"));
    var semantics = new InvocationSemantics(InvocationMode.VIRTUAL, qualifyingType, InvocationForm.INSTANCE_IMPLICIT);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new OperationNode("node", "graph", OperationKind.READ, "read", List.of(), Optional.empty(), Optional.of(semantics),
            List.of(), List.of(), List.of()));

    // Assert
    assertEquals("Invocation semantics must be present exactly for invocation and construction operations", exception.getMessage());
  }
}
