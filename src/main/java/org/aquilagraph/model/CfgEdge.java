package org.aquilagraph.model;

import java.util.Objects;
import java.util.Optional;

public record CfgEdge(
    String edgeId,
    String graphId,
    String sourceNodeId,
    String targetNodeId,
    TransferKind transferKind,
    Optional<String> discriminator) {
  public CfgEdge {
    Objects.requireNonNull(edgeId, "edgeId");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(sourceNodeId, "sourceNodeId");
    Objects.requireNonNull(targetNodeId, "targetNodeId");
    Objects.requireNonNull(transferKind, "transferKind");
    discriminator = Objects.requireNonNull(discriminator, "discriminator");
  }
}
