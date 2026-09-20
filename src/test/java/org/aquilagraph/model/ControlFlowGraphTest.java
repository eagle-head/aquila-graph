package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
}
