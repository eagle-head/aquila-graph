package org.aquilagraph.model;

import java.util.List;

/** Node owned by a method control-flow graph. */
public sealed interface CfgNode permits EntryNode, OperationNode, ExitNode {
  /**
   * Returns the stable node identifier.
   *
   * @return node identifier
   */
  String nodeId();

  /**
   * Returns the owning graph identifier.
   *
   * @return graph identifier
   */
  String graphId();

  /**
   * Returns source locations contributing to this node.
   *
   * @return immutable source origins
   */
  List<SourceSpan> origins();
}
