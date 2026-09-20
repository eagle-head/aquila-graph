package org.aquilagraph.model;

import java.util.Objects;

/**
 * Translated root syntax node and accepted-profile status for a method body.
 *
 * @param syntaxRoot
 *          root syntax node
 * @param supported
 *          whether every retained construct is supported
 */
public record MethodBody(SyntaxNode syntaxRoot, boolean supported) {
  /** Validates the body root. */
  public MethodBody {
    Objects.requireNonNull(syntaxRoot, "syntaxRoot").descendantsAndSelf();
    if (syntaxRoot.syntaxKind() != SyntaxKind.BLOCK) {
      throw new IllegalArgumentException("A method-body syntax root must be a block");
    }
  }
}
