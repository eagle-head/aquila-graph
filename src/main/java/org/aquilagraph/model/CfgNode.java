package org.aquilagraph.model;

import java.util.List;

public sealed interface CfgNode permits EntryNode, OperationNode, ExitNode {
  String nodeId();

  String graphId();

  List<SourceSpan> origins();
}
