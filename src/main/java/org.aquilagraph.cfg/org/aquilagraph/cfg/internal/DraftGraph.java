package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed construction content; only graph admission may publish its qualified materialization. */
record DraftGraph(String graphId, String methodKey, DraftEntry entry, List<DraftOperation> operationNodes, List<DraftValue> values,
    DraftExit exit, List<DraftEdge> edges) {
  DraftGraph {
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(exit, "exit");
    operationNodes = List.copyOf(operationNodes);
    values = List.copyOf(values);
    edges = List.copyOf(edges);
    Map<String, DraftValue> valuesById = new HashMap<>();
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

  List<DraftNode> nodes() {
    var nodes = new ArrayList<DraftNode>();
    nodes.add(entry);
    nodes.addAll(operationNodes);
    nodes.add(exit);
    return List.copyOf(nodes);
  }
  private static void requireOwnedNode(String graphId, DraftNode node, Set<String> nodeIds) {
    if (!node.graphId().equals(graphId) || !nodeIds.add(node.nodeId())) {
      throw new IllegalArgumentException("CFG nodes must be unique and owned by the graph");
    }
  }

  private static void requireOwnedValue(Map<String, DraftValue> valuesById, DraftValue value) {
    if (!value.equals(valuesById.get(value.valueId()))) {
      throw new IllegalArgumentException("Operations may reference only values owned by the CFG");
    }
  }
}
