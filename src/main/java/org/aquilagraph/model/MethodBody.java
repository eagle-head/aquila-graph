package org.aquilagraph.model;

import java.util.Objects;

public record MethodBody(SyntaxNode syntaxRoot, boolean supported) {
  public MethodBody {
    Objects.requireNonNull(syntaxRoot, "syntaxRoot").descendantsAndSelf();
    if (syntaxRoot.syntaxKind() != SyntaxKind.BLOCK) {
      throw new IllegalArgumentException("A method-body syntax root must be a block");
    }
  }
}
