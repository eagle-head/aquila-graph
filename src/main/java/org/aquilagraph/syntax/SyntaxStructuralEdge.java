package org.aquilagraph.syntax;

import java.util.Objects;

/**
 * Durable structural relation between two source occurrences.
 *
 * @param parentKey
 *          parent occurrence key
 * @param role
 *          typed child role
 * @param position
 *          zero-based role-local position
 * @param childKey
 *          child occurrence key
 */
public record SyntaxStructuralEdge(String parentKey, SyntaxRole role, int position, String childKey) {
  /** Validates both endpoints, the role, and the position. */
  public SyntaxStructuralEdge {
    requireText(parentKey, "Structural parent key");
    Objects.requireNonNull(role, "role");
    if (position < 0) {
      throw new IllegalArgumentException("A structural edge position cannot be negative");
    }
    requireText(childKey, "Structural child key");
  }

  private static void requireText(String text, String label) {
    Objects.requireNonNull(text, label);
    if (text.isBlank()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
  }
}
