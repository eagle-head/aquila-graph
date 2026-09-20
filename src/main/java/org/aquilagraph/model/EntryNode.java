package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

public record EntryNode(String nodeId, String graphId) implements CfgNode {
  public EntryNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(graphId, "graphId");
  }

  @Override
  public List<SourceSpan> origins() {
    return List.of();
  }
}
