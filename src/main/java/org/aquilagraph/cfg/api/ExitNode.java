package org.aquilagraph.cfg.api;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Unique exit node of a method control-flow graph.
 *
 * @param nodeId
 *          stable node identifier
 * @param graphId
 *          owning graph identifier
 */
public record ExitNode(String nodeId, String graphId) implements CfgNode {
  /** Validates exit-node identity and ownership. */
  public ExitNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(graphId, "graphId");
  }

  /**
   * Returns the empty source-origin list for the synthetic exit node.
   *
   * @return empty list
   */
  @Override
  public List<SourceSpan> origins() {
    return List.of();
  }
}
