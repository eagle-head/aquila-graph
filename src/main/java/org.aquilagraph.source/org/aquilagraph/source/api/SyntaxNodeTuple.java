package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Canonical identity projection for one durable Java source occurrence.
 *
 * @param nodeKey
 *          derived occurrence key
 * @param sourceFileKey
 *          captured source owner
 * @param structuralPath
 *          root-relative typed path
 * @param sourceKind
 *          exact Java source kind
 */
public record SyntaxNodeTuple(String nodeKey, String sourceFileKey, SyntaxStructuralPath structuralPath, JavaSourceKind sourceKind) {
  /** Validates the occurrence identity projection. */
  public SyntaxNodeTuple {
    requireText(nodeKey, "Syntax node key");
    requireText(sourceFileKey, "Source-file key");
    Objects.requireNonNull(structuralPath, "structuralPath");
    Objects.requireNonNull(sourceKind, "sourceKind");
  }

  private static void requireText(String text, String label) {
    Objects.requireNonNull(text, label);
    if (text.isBlank()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
  }
}
