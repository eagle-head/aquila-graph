package org.aquilagraph.syntax;

import java.util.Objects;

/**
 * One syntax child with an explicit structural role and role-local position.
 *
 * @param role
 *          structural role within the parent
 * @param position
 *          zero-based position among children in the same role
 * @param node
 *          child syntax node
 */
public record SyntaxChild(SyntaxRole role, int position, SyntaxNode node) {
  /** Validates the role, position, and child node. */
  public SyntaxChild {
    Objects.requireNonNull(role, "role");
    if (position < 0) {
      throw new IllegalArgumentException("A syntax child position cannot be negative");
    }
    Objects.requireNonNull(node, "node");
  }
}
