package org.aquilagraph.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validated intraprocedural control-flow graph and its owned values.
 *
 * @param graphId
 *          stable graph identifier
 * @param methodKey
 *          analyzed method identifier
 * @param entry
 *          unique graph entry
 * @param operationNodes
 *          ordered semantic operation nodes
 * @param values
 *          values owned by the graph
 * @param exit
 *          unique graph exit
 * @param edges
 *          validated control transfers
 */
public record ControlFlowGraph(String graphId, String methodKey, EntryNode entry, List<OperationNode> operationNodes,
    List<FlowValue> values, ExitNode exit, List<CfgEdge> edges) {
  /** Validates graph ownership, uniqueness, and edge integrity. */
  public ControlFlowGraph {
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(entry, "entry");
    operationNodes = List.copyOf(Objects.requireNonNull(operationNodes, "operationNodes"));
    values = List.copyOf(Objects.requireNonNull(values, "values"));
    Objects.requireNonNull(exit, "exit");
    edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    Map<String, FlowValue> valuesById = new HashMap<>();
    for (var value : values) {
      if (!value.graphId().equals(graphId) || valuesById.putIfAbsent(value.valueId(), value) != null) {
        throw new IllegalArgumentException("CFG values must be unique and owned by the graph");
      }
    }
    var nodeIds = new HashSet<String>();
    requireOwnedNode(graphId, entry, nodeIds);
    for (var node : operationNodes) {
      requireOwnedNode(graphId, node, nodeIds);
      node.definedValue().ifPresent(value -> requireOwnedValue(valuesById, value));
      node.operands().forEach(operand -> requireOwnedValue(valuesById, operand.value()));
    }
    requireOwnedNode(graphId, exit, nodeIds);
    var edgeIds = new HashSet<String>();
    for (var edge : edges) {
      if (!edge.graphId().equals(graphId) || !nodeIds.contains(edge.sourceNodeId()) || !nodeIds.contains(edge.targetNodeId())
          || !edgeIds.add(edge.edgeId())) {
        throw new IllegalArgumentException("CFG edges must be unique and connect nodes in the same graph");
      }
    }
  }

  /**
   * Returns entry, operation nodes, and exit in deterministic order.
   *
   * @return all graph nodes
   */
  public List<CfgNode> nodes() {
    var nodes = new ArrayList<CfgNode>(operationNodes.size() + 2);
    nodes.add(entry);
    nodes.addAll(operationNodes);
    nodes.add(exit);
    return List.copyOf(nodes);
  }

  /**
   * Returns the number of graph nodes without materializing the combined node list.
   *
   * @return entry, operation, and exit node count
   */
  public int nodeCount() {
    return operationNodes.size() + 2;
  }

  /**
   * Returns nodes directly reachable from an owned node.
   *
   * @param node
   *          source node
   * @return direct successors
   */
  public List<CfgNode> successors(CfgNode node) {
    return adjacent(node, true);
  }

  /**
   * Returns nodes that transfer directly to an owned node.
   *
   * @param node
   *          target node
   * @return direct predecessors
   */
  public List<CfgNode> predecessors(CfgNode node) {
    return adjacent(node, false);
  }

  private List<CfgNode> adjacent(CfgNode node, boolean outgoing) {
    Objects.requireNonNull(node, "node");
    if (!node.graphId().equals(graphId)) {
      throw new IllegalArgumentException("The CFG node belongs to another graph");
    }
    Map<String, CfgNode> byId = new HashMap<>();
    nodes().forEach(candidate -> byId.put(candidate.nodeId(), candidate));
    return edges.stream().filter(edge -> (outgoing ? edge.sourceNodeId() : edge.targetNodeId()).equals(node.nodeId()))
        .map(edge -> byId.get(outgoing ? edge.targetNodeId() : edge.sourceNodeId())).toList();
  }

  private static void requireOwnedNode(String graphId, CfgNode node, Set<String> nodeIds) {
    if (!node.graphId().equals(graphId) || !nodeIds.add(node.nodeId())) {
      throw new IllegalArgumentException("CFG nodes must be unique and owned by the graph");
    }
  }

  private static void requireOwnedValue(Map<String, FlowValue> valuesById, FlowValue value) {
    if (!value.equals(valuesById.get(value.valueId()))) {
      throw new IllegalArgumentException("Operations may reference only values owned by the CFG");
    }
  }
}
