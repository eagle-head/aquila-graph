package org.aquilagraph.cfg.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import org.aquilagraph.identity.api.GraphIdentity;
import org.aquilagraph.identity.api.GraphNodeKey;
import org.aquilagraph.identity.api.GraphValueKey;
import org.aquilagraph.identity.api.GraphEdgeKey;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Validated intraprocedural control-flow graph and its owned immutable values. */
public final class ControlFlowGraph {
  private final GraphContent content;
  /**
   * Creates a validated construction draft before intrinsic source-result admission.
   *
   * @param graphId
   *          construction-local graph identifier
   * @param methodKey
   *          analyzed declaration
   * @param entry
   *          unique graph entry
   * @param operationNodes
   *          ordered operations
   * @param values
   *          ordered owned values
   * @param exit
   *          unique graph exit
   * @param edges
   *          ordered transfers
   */
  public ControlFlowGraph(String graphId, String methodKey, EntryNode entry, List<OperationNode> operationNodes, List<FlowValue> values,
      ExitNode exit, List<CfgEdge> edges) {
    this(graphId, methodKey, entry, operationNodes, values, exit, edges, Optional.empty(), Optional.empty());
  }

  /** Validates graph ownership, uniqueness, and edge integrity. */
  ControlFlowGraph(String graphId, String methodKey, EntryNode entry, List<OperationNode> operationNodes, List<FlowValue> values,
      ExitNode exit, List<CfgEdge> edges, Optional<GraphIdentity> identity, Optional<BodyRef> body) {
    this(graphId, methodKey, entry, operationNodes, values, exit, edges, identity, body, List.of());
  }

  ControlFlowGraph(String graphId, String methodKey, EntryNode entry, List<OperationNode> operationNodes, List<FlowValue> values,
      ExitNode exit, List<CfgEdge> edges, Optional<GraphIdentity> identity, Optional<BodyRef> body, List<AnalysisDiagnostic> diagnostics) {
    content = new GraphContent(graphId, methodKey, entry, operationNodes, values, exit, edges, identity, body, diagnostics);
  }

  /**
   * Returns ordered graph diagnostic occurrences covered by its content identity.
   *
   * @return immutable graph diagnostics, including repeated equal values
   */
  public List<AnalysisDiagnostic> diagnostics() {
    return content.diagnostics();
  }

  /**
   * Returns the stable owning graph identifier.
   *
   * @return graph identifier
   */
  public String graphId() {
    return content.graphId();
  }
  /**
   * Returns the canonical analyzed method.
   *
   * @return method identifier
   */
  public String methodKey() {
    return content.methodKey();
  }
  /**
   * Returns the owned entry node.
   *
   * @return entry node
   */
  public EntryNode entry() {
    return content.entry();
  }
  /**
   * Returns operations in construction order.
   *
   * @return immutable operations
   */
  public List<OperationNode> operationNodes() {
    return content.operationNodes();
  }
  /**
   * Returns owned values in construction order.
   *
   * @return immutable values
   */
  public List<FlowValue> values() {
    return content.values();
  }
  /**
   * Returns the owned exit node.
   *
   * @return exit node
   */
  public ExitNode exit() {
    return content.exit();
  }
  /**
   * Returns transfers in construction order.
   *
   * @return immutable transfers
   */
  public List<CfgEdge> edges() {
    return content.edges();
  }
  /**
   * Returns the admitted content qualification.
   *
   * @return optional exact identity
   */
  public Optional<GraphIdentity> identity() {
    return content.identity();
  }
  /**
   * Returns the admitted source body association.
   *
   * @return optional exact body
   */
  public Optional<BodyRef> body() {
    return content.body();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ControlFlowGraph graph && content.equals(graph.content);
  }

  @Override
  public int hashCode() {
    return content.hashCode();
  }

  @Override
  public String toString() {
    return "ControlFlowGraph[graphId=" + content.graphId() + ", methodKey=" + content.methodKey() + ", entry=" + content.entry()
        + ", operationNodes=" + content.operationNodes() + ", values=" + content.values() + ", exit=" + content.exit() + ", edges="
        + content.edges() + ", identity=" + content.identity() + ", body=" + content.body() + "]";
  }

  /**
   * Returns entry, operation nodes, and exit in deterministic order.
   *
   * @return all graph nodes
   */
  public List<CfgNode> nodes() {
    var nodes = new ArrayList<CfgNode>(content.operationNodes().size() + 2);
    nodes.add(content.entry());
    nodes.addAll(content.operationNodes());
    nodes.add(content.exit());
    return List.copyOf(nodes);
  }

  /**
   * Returns the number of graph nodes without materializing the combined node list.
   *
   * @return entry, operation, and exit node count
   */
  public int nodeCount() {
    return content.operationNodes().size() + 2;
  }

  /**
   * Resolves a local node only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified node query
   * @return canonical owned node or absence for an unknown local key
   */
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
  public Optional<FlowValue> value(GraphValueKey key) {
    Objects.requireNonNull(key, "key");
    requireIdentity(key.graphIdentity());
    return content.values().stream().filter(value -> value.valueId().equals(key.localKey())).findFirst();
  }

  /**
   * Resolves a local transfer only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified transfer query
   * @return canonical owned transfer or absence for an unknown local key
   */
  public Optional<CfgEdge> edge(GraphEdgeKey key) {
    Objects.requireNonNull(key, "key");
    requireIdentity(key.graphIdentity());
    return content.edges().stream().filter(value -> value.edgeId().equals(key.localKey())).findFirst();
  }

  private void requireIdentity(GraphIdentity expected) {
    if (!content.identity().filter(expected::equals).isPresent()) {
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
    if (!node.graphId().equals(content.graphId())) {
      throw new IllegalArgumentException("The CFG node belongs to another graph");
    }
    Map<String, CfgNode> byId = new HashMap<>();
    nodes().forEach(candidate -> byId.put(candidate.nodeId(), candidate));
    return content.edges().stream().filter(edge -> (outgoing ? edge.sourceNodeId() : edge.targetNodeId()).equals(node.nodeId()))
        .map(edge -> byId.get(outgoing ? edge.targetNodeId() : edge.sourceNodeId())).toList();
  }

  private record GraphContent(String graphId, String methodKey, EntryNode entry, List<OperationNode> operationNodes, List<FlowValue> values,
      ExitNode exit, List<CfgEdge> edges, Optional<GraphIdentity> identity, Optional<BodyRef> body, List<AnalysisDiagnostic> diagnostics) {
    GraphContent {
      Objects.requireNonNull(graphId, "graphId");
      Objects.requireNonNull(methodKey, "methodKey");
      Objects.requireNonNull(entry, "entry");
      operationNodes = List.copyOf(Objects.requireNonNull(operationNodes, "operationNodes"));
      values = List.copyOf(Objects.requireNonNull(values, "values"));
      Objects.requireNonNull(exit, "exit");
      edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
      Objects.requireNonNull(identity, "identity");
      Objects.requireNonNull(body, "body");
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
      if (identity.isEmpty() && !diagnostics.isEmpty()) {
        throw new IllegalArgumentException("Graph diagnostics require intrinsic content qualification");
      }
      if (identity.isPresent() != body.isPresent()) {
        throw new IllegalArgumentException("Graph identity and exact body must be qualified together");
      }
      if (identity.isPresent()) {
        var qualified = identity.orElseThrow();
        var sourceBody = body.orElseThrow();
        if (entry.nodeId().isBlank() || exit.nodeId().isBlank() || operationNodes.stream().anyMatch(node -> node.nodeId().isBlank())
            || values.stream().anyMatch(value -> value.valueId().isBlank()) || edges.stream().anyMatch(edge -> edge.edgeId().isBlank())) {
          throw new IllegalArgumentException("Qualified graph members require nonblank local query identities");
        }
        if (!graphId.equals(qualified.derivationKey()) || !methodKey.equals(qualified.methodKey())
            || !methodKey.equals(sourceBody.methodKey()) || !qualified.astIdentity().equals(sourceBody.astIdentity()) || operationNodes
                .stream().anyMatch(node -> node.sourceRef().isEmpty() || !node.sourceRef().orElseThrow().body().equals(sourceBody))) {
          throw new IllegalArgumentException("Qualified graph content must belong to its exact source body");
        }
      } else if (operationNodes.stream().anyMatch(node -> node.sourceRef().isPresent())) {
        throw new IllegalArgumentException("Construction drafts cannot claim an admitted source association");
      }
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
