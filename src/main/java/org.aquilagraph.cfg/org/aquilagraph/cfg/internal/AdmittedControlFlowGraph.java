package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.GraphIdentity;
import org.aquilagraph.identity.api.GraphNodeKey;
import org.aquilagraph.identity.api.GraphValueKey;
import org.aquilagraph.identity.api.GraphEdgeKey;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.source.api.AnalysisDiagnostic;

import org.aquilagraph.cfg.api.ControlFlowGraph;
import org.aquilagraph.cfg.api.CfgNode;
import org.aquilagraph.cfg.api.EntryNode;
import org.aquilagraph.cfg.api.ExitNode;
import org.aquilagraph.cfg.api.OperationNode;
import org.aquilagraph.cfg.api.FlowValue;
import org.aquilagraph.cfg.api.CfgEdge;

/** Direct canonical state and immutable queries for an admitted graph. */
public final class AdmittedControlFlowGraph implements ControlFlowGraph {
  private final String owningGraphKey;
  private final String declarationKey;
  private final EntryNode entryNode;
  private final List<OperationNode> orderedOperations;
  private final List<FlowValue> orderedValues;
  private final ExitNode exitNode;
  private final List<CfgEdge> orderedEdges;
  private final GraphIdentity qualifiedIdentity;
  private final BodyRef sourceBody;
  private final List<AnalysisDiagnostic> graphDiagnostics;
  AdmittedControlFlowGraph(String owningGraphKey, String declarationKey, EntryNode entryNode, List<OperationNode> operations,
      List<FlowValue> orderedValues, ExitNode exitNode, List<CfgEdge> orderedEdges, GraphIdentity qualifiedIdentity, BodyRef sourceBody,
      List<AnalysisDiagnostic> graphDiagnostics) {
    this.owningGraphKey = owningGraphKey;
    this.declarationKey = declarationKey;
    this.entryNode = entryNode;
    this.orderedOperations = List.copyOf(operations);
    this.orderedValues = List.copyOf(orderedValues);
    this.exitNode = exitNode;
    this.orderedEdges = List.copyOf(orderedEdges);
    this.qualifiedIdentity = Objects.requireNonNull(qualifiedIdentity, "identity");
    this.sourceBody = Objects.requireNonNull(sourceBody, "body");
    this.graphDiagnostics = List.copyOf(graphDiagnostics);
  }
  /**
   * Returns ordered graph diagnostic occurrences covered by its content identity.
   *
   * @return immutable graph diagnostics, including repeated equal values
   */
  @Override
  public List<AnalysisDiagnostic> diagnostics() {
    return graphDiagnostics;
  }

  /**
   * Returns the stable owning graph identifier.
   *
   * @return graph identifier
   */
  @Override
  public String graphId() {
    return owningGraphKey;
  }
  /**
   * Returns the canonical analyzed method.
   *
   * @return method identifier
   */
  @Override
  public String methodKey() {
    return declarationKey;
  }
  /**
   * Returns the owned entry node.
   *
   * @return entry node
   */
  @Override
  public EntryNode entry() {
    return entryNode;
  }
  /**
   * Returns operations in construction order.
   *
   * @return immutable operations
   */
  @Override
  public List<OperationNode> operationNodes() {
    return orderedOperations;
  }
  /**
   * Returns owned values in construction order.
   *
   * @return immutable values
   */
  @Override
  public List<FlowValue> values() {
    return orderedValues;
  }
  /**
   * Returns the owned exit node.
   *
   * @return exit node
   */
  @Override
  public ExitNode exit() {
    return exitNode;
  }
  /**
   * Returns transfers in construction order.
   *
   * @return immutable transfers
   */
  @Override
  public List<CfgEdge> edges() {
    return orderedEdges;
  }
  /**
   * Returns the admitted content qualification.
   *
   * @return exact admitted identity
   */
  @Override
  public GraphIdentity identity() {
    return qualifiedIdentity;
  }
  /**
   * Returns the admitted source body association.
   *
   * @return exact admitted body
   */
  @Override
  public BodyRef body() {
    return sourceBody;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AdmittedControlFlowGraph graph && owningGraphKey.equals(graph.owningGraphKey)
        && declarationKey.equals(graph.declarationKey) && entryNode.equals(graph.entryNode)
        && orderedOperations.equals(graph.orderedOperations) && orderedValues.equals(graph.orderedValues) && exitNode.equals(graph.exitNode)
        && orderedEdges.equals(graph.orderedEdges) && qualifiedIdentity.equals(graph.qualifiedIdentity)
        && sourceBody.equals(graph.sourceBody) && graphDiagnostics.equals(graph.graphDiagnostics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(owningGraphKey, declarationKey, entryNode, orderedOperations, orderedValues, exitNode, orderedEdges,
        qualifiedIdentity, sourceBody, graphDiagnostics);
  }

  @Override
  public String toString() {
    return "ControlFlowGraph[graphId=" + owningGraphKey + ", methodKey=" + declarationKey + ", entry=" + entryNode + ", operationNodes="
        + orderedOperations + ", values=" + orderedValues + ", exit=" + exitNode + ", edges=" + orderedEdges + ", identity="
        + qualifiedIdentity + ", body=" + sourceBody + "]";
  }

  /**
   * Returns entry, operation nodes, and exit in deterministic order.
   *
   * @return all graph nodes
   */
  @Override
  public List<CfgNode> nodes() {
    var nodes = new ArrayList<CfgNode>(orderedOperations.size() + 2);
    nodes.add(entryNode);
    nodes.addAll(orderedOperations);
    nodes.add(exitNode);
    return List.copyOf(nodes);
  }

  /**
   * Returns the number of graph nodes without materializing the combined node list.
   *
   * @return entry, operation, and exit node count
   */
  @Override
  public int nodeCount() {
    return orderedOperations.size() + 2;
  }

  /**
   * Resolves a local node only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified node query
   * @return canonical owned node or absence for an unknown local key
   */
  @Override
  public Optional<CfgNode> node(GraphNodeKey key) {
    Objects.requireNonNull(key, "key");
    requireIdentity(key.graphIdentity());
    return nodes().stream().filter(value -> value.nodeId().equals(key.localKey())).findFirst();
  }

  /**
   * Resolves a local value only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified value query
   * @return canonical owned value or absence for an unknown local key
   */
  @Override
  public Optional<FlowValue> value(GraphValueKey key) {
    Objects.requireNonNull(key, "key");
    requireIdentity(key.graphIdentity());
    return orderedValues.stream().filter(value -> value.valueId().equals(key.localKey())).findFirst();
  }

  /**
   * Resolves a local transfer only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified transfer query
   * @return canonical owned transfer or absence for an unknown local key
   */
  @Override
  public Optional<CfgEdge> edge(GraphEdgeKey key) {
    Objects.requireNonNull(key, "key");
    requireIdentity(key.graphIdentity());
    return orderedEdges.stream().filter(value -> value.edgeId().equals(key.localKey())).findFirst();
  }

  private void requireIdentity(GraphIdentity expected) {
    if (!qualifiedIdentity.equals(expected)) {
      throw new IllegalArgumentException("Graph query belongs to another exact derivation identity");
    }
  }

  /**
   * Returns nodes directly reachable from an owned node.
   *
   * @param node
   *          source node
   * @return direct successors
   */
  @Override
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
  @Override
  public List<CfgNode> predecessors(CfgNode node) {
    return adjacent(node, false);
  }

  private List<CfgNode> adjacent(CfgNode node, boolean outgoing) {
    Objects.requireNonNull(node, "node");
    if (!node.graphId().equals(owningGraphKey)) {
      throw new IllegalArgumentException("The CFG node belongs to another graph");
    }
    Map<String, CfgNode> byId = new HashMap<>();
    nodes().forEach(candidate -> byId.put(candidate.nodeId(), candidate));
    return orderedEdges.stream().filter(edge -> (outgoing ? edge.sourceNodeId() : edge.targetNodeId()).equals(node.nodeId()))
        .map(edge -> byId.get(outgoing ? edge.targetNodeId() : edge.sourceNodeId())).toList();
  }

}
