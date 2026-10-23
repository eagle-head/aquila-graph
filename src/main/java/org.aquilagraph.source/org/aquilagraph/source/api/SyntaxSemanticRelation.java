package org.aquilagraph.source.api;

/** Typed semantic relations emitted from source occurrences. */
public enum SyntaxSemanticRelation {
  /** Source occurrence refers to a declaration or declaration occurrence. */
  DECLARATION_REFERENCE("declaration-reference"),
  /** Written annotation belongs to one exact declaration occurrence. */
  ANNOTATED_TARGET("annotated-target");

  private final String stableIdentity;

  SyntaxSemanticRelation(String stableId) {
    stableIdentity = stableId;
  }

  /**
   * Returns the stable relation identity.
   *
   * @return stable relation identity
   */
  public String stableId() {
    return stableIdentity;
  }
}
