package org.aquilagraph.syntax;

import java.util.List;
import java.util.Objects;

/**
 * Project-owned source occurrences and relations assembled for one captured source.
 *
 * @param sourceFileKey
 *          captured source owner
 * @param root
 *          compilation-unit occurrence
 * @param nodes
 *          source occurrences, including the root
 * @param structuralEdges
 *          typed structural relations
 * @param semanticEdges
 *          typed semantic relations
 */
public record SyntaxSourceContribution(String sourceFileKey, SyntaxNode root, List<SyntaxNode> nodes,
    List<SyntaxStructuralEdge> structuralEdges, List<SyntaxSemanticEdge> semanticEdges) {
  /** Takes immutable copies of the contribution collections. */
  public SyntaxSourceContribution {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    if (sourceFileKey.isBlank()) {
      throw new IllegalArgumentException("Contribution source-file key cannot be blank");
    }
    Objects.requireNonNull(root, "root");
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
    structuralEdges = List.copyOf(Objects.requireNonNull(structuralEdges, "structuralEdges"));
    semanticEdges = List.copyOf(Objects.requireNonNull(semanticEdges, "semanticEdges"));
  }
}
