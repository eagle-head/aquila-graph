package org.aquilagraph.model;

import java.util.Objects;

/**
 * Public declaration asType components copied alongside actual canonical type translation.
 *
 * @param ownerTypeKey
 *          canonical selected declaring type identity
 * @param sourceFileKey
 *          captured source that owns the declaration
 * @param identity
 *          copied declared type identity
 * @param structure
 *          recursive declared shape selecting the same canonical owner
 */
public record DeclarationTypeEvidence(String ownerTypeKey, String sourceFileKey, JavaTypeIdentity identity,
    CoverageTypeStructure structure) {
  /** Requires actual canonical ownership and a selected declared shape. */
  public DeclarationTypeEvidence {
    if (Objects.requireNonNull(ownerTypeKey, "ownerTypeKey").isBlank()
        || Objects.requireNonNull(sourceFileKey, "sourceFileKey").isBlank()) {
      throw new IllegalArgumentException("Declaration type evidence requires canonical owner and source identities");
    }
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(structure, "structure");
    if (identity.category() != JavaTypeCategory.DECLARED || !(structure instanceof CoverageTypeStructure.Declared declared)
        || !declared.declarationKey().equals(ownerTypeKey)) {
      throw new IllegalArgumentException("Declaration type evidence must select its actual canonical owner");
    }
  }
}
