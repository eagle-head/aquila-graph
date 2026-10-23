package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Unique entry node of a method control-flow graph.
 *
 * @param nodeId
 *          stable node identifier
 * @param graphId
 *          owning graph identifier
 */
record DraftEntry(String nodeId, String graphId) implements DraftNode {
  /** Validates entry-node identity and ownership. */
  public DraftEntry {
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
