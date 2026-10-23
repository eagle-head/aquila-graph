package org.aquilagraph.cfg.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Directed control transfer between two nodes in the same method graph.
 *
 * @param edgeId
 *          stable edge identifier
 * @param graphId
 *          owning graph identifier
 * @param sourceNodeId
 *          source node identifier
 * @param targetNodeId
 *          target node identifier
 * @param transferKind
 *          control transfer kind
 * @param discriminator
 *          optional branch or exception discriminator
 */
public record CfgEdge(String edgeId, String graphId, String sourceNodeId, String targetNodeId, TransferKind transferKind,
    Optional<String> discriminator) {
  /** Validates edge identity, ownership, and endpoints. */
  public CfgEdge {
    Objects.requireNonNull(edgeId, "edgeId");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(sourceNodeId, "sourceNodeId");
    Objects.requireNonNull(targetNodeId, "targetNodeId");
    Objects.requireNonNull(transferKind, "transferKind");
    Objects.requireNonNull(discriminator, "discriminator");
  }
}
