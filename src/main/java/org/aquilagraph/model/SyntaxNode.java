package org.aquilagraph.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Normalized syntax tree node with semantic references and source provenance.
 *
 * @param nodeId
 *          stable syntax-node identifier
 * @param syntaxKind
 *          normalized syntax category
 * @param detail
 *          source-derived node detail
 * @param children
 *          ordered child nodes
 * @param semanticReferences
 *          compiler semantic references
 * @param origins
 *          contributing source locations
 * @param compilerGenerated
 *          whether the node lacks a direct source range
 */
public record SyntaxNode(String nodeId, SyntaxKind syntaxKind, String detail, List<SyntaxNode> children,
    List<SemanticReference> semanticReferences, List<SourceSpan> origins, boolean compilerGenerated) {
  /** Validates and defensively copies syntax data. */
  public SyntaxNode {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(syntaxKind, "syntaxKind");
    Objects.requireNonNull(detail, "detail");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
    semanticReferences = List.copyOf(Objects.requireNonNull(semanticReferences, "semanticReferences"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
  }

  /**
   * Traverses this node and descendants in preorder while validating tree ownership.
   *
   * @return immutable preorder traversal
   */
  public List<SyntaxNode> descendantsAndSelf() {
    var result = new ArrayList<SyntaxNode>();
    collect(this, result, new HashSet<>());
    return List.copyOf(result);
  }

  private static void collect(SyntaxNode node, List<SyntaxNode> result, Set<String> seen) {
    if (!seen.add(node.nodeId())) {
      throw new IllegalArgumentException("A syntax node may have only one parent: " + node.nodeId());
    }
    result.add(node);
    for (var child : node.children()) {
      collect(Objects.requireNonNull(child, "child"), result, seen);
    }
  }
}
