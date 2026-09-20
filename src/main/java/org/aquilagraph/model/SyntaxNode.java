package org.aquilagraph.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SyntaxNode(
    String nodeId,
    SyntaxKind syntaxKind,
    String detail,
    List<SyntaxNode> children,
    List<SemanticReference> semanticReferences,
    List<SourceSpan> origins,
    boolean compilerGenerated) {
  public SyntaxNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(syntaxKind, "syntaxKind");
    detail = Objects.requireNonNull(detail, "detail");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
    semanticReferences =
        List.copyOf(Objects.requireNonNull(semanticReferences, "semanticReferences"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
  }

  public List<SyntaxNode> descendantsAndSelf() {
    var result = new ArrayList<SyntaxNode>();
    collect(this, result, new HashSet<>());
    return List.copyOf(result);
  }

  private static void collect(SyntaxNode node, List<SyntaxNode> result, HashSet<String> seen) {
    if (!seen.add(node.nodeId())) {
      throw new IllegalArgumentException("A syntax node may have only one parent: " + node.nodeId());
    }
    result.add(node);
    for (var child : node.children()) {
      collect(Objects.requireNonNull(child, "child"), result, seen);
    }
  }
}
