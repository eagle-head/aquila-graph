package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Explicit origin of a body that has no written source occurrence.
 *
 * @param origin
 *          copied compiler insertion kind
 * @param sourceFileKey
 *          captured source owner
 * @param ownerDeclarationKey
 *          canonical declaration receiving the inserted body
 */
public record NonwrittenBodyProvenance(String origin, String sourceFileKey, String ownerDeclarationKey) {
  /** Requires an explicit insertion kind and both canonical owners. */
  public NonwrittenBodyProvenance {
    requireText(origin, "origin");
    requireText(sourceFileKey, "sourceFileKey");
    requireText(ownerDeclarationKey, "ownerDeclarationKey");
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
