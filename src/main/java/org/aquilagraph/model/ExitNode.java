package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

public record ExitNode(String nodeId, String graphId) implements CfgNode {
  public ExitNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(graphId, "graphId");
  }

  @Override
  public List<SourceSpan> origins() {
    return List.of();
  }
}
