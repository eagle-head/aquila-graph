package org.aquilagraph.cfg.api;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.GraphIdentity;
import org.aquilagraph.identity.api.GraphNodeKey;
import org.aquilagraph.identity.api.GraphValueKey;
import org.aquilagraph.identity.api.GraphEdgeKey;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.source.api.AnalysisDiagnostic;

import org.aquilagraph.cfg.internal.AdmittedControlFlowGraph;

/** Immutable graph admitted for one exact source body and interpretation. */
public sealed interface ControlFlowGraph permits AdmittedControlFlowGraph {
  /**
   * Returns ordered graph diagnostic occurrences covered by its content identity.
   *
   * @return immutable graph diagnostics, including repeated equal values
   */
  List<AnalysisDiagnostic> diagnostics();

  /**
   * Returns the stable owning graph identifier.
   *
   * @return graph identifier
   */
  String graphId();

  /**
   * Returns the canonical analyzed method.
   *
   * @return method identifier
   */
  String methodKey();

  /**
   * Returns the owned entry node.
   *
   * @return entry node
   */
  EntryNode entry();

  /**
   * Returns operations in construction order.
   *
   * @return immutable operations
   */
  List<OperationNode> operationNodes();

  /**
   * Returns owned values in construction order.
   *
   * @return immutable values
   */
  List<FlowValue> values();

  /**
   * Returns the owned exit node.
   *
   * @return exit node
   */
  ExitNode exit();

  /**
   * Returns transfers in construction order.
   *
   * @return immutable transfers
   */
  List<CfgEdge> edges();

  /**
   * Returns the admitted content qualification.
   *
   * @return required exact identity
   */
  GraphIdentity identity();

  /**
   * Returns the admitted source body association.
   *
   * @return required exact body
   */
  BodyRef body();

  /**
   * Returns entry, operation nodes, and exit in deterministic order.
   *
   * @return all graph nodes
   */
  List<CfgNode> nodes();

  /**
   * Returns the number of graph nodes without materializing the combined node list.
   *
   * @return entry, operation, and exit node count
   */
  int nodeCount();

  /**
   * Resolves a local node only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified node query
   * @return canonical owned node or absence for an unknown local key
   */
  Optional<CfgNode> node(GraphNodeKey key);

  /**
   * Resolves a local value only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified value query
   * @return canonical owned value or absence for an unknown local key
   */
  Optional<FlowValue> value(GraphValueKey key);

  /**
   * Resolves a local transfer only in this exact qualified graph interpretation.
   *
   * @param key
   *          qualified transfer query
   * @return canonical owned transfer or absence for an unknown local key
   */
  Optional<CfgEdge> edge(GraphEdgeKey key);

  /**
   * Returns nodes directly reachable from an owned node.
   *
   * @param node
   *          source node
   * @return direct successors
   */
  List<CfgNode> successors(CfgNode node);

  /**
   * Returns nodes that transfer directly to an owned node.
   *
   * @param node
   *          target node
   * @return direct predecessors
   */
  List<CfgNode> predecessors(CfgNode node);
}
