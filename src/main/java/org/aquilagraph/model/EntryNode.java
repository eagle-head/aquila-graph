package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

/**
 * Unique entry node of a method control-flow graph.
 *
 * @param nodeId
 *          stable node identifier
 * @param graphId
 *          owning graph identifier
 */
public record EntryNode(String nodeId, String graphId) implements CfgNode {
  /** Validates entry-node identity and ownership. */
  public EntryNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(graphId, "graphId");
  }

  /**
   * Returns the empty source-origin list for the synthetic entry node.
   *
   * @return empty list
   */
  @Override
  public List<SourceSpan> origins() {
    return List.of();
  }
}
