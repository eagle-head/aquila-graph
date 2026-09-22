package org.aquilagraph.model;

/** Exclusive policies for one feature in the release-bound coverage contract. */
public enum LanguageFeaturePolicy {
  /** The feature is retained at a named project-owned representation boundary. */
  REPRESENTED(false),

  /** The feature is deliberately outside the semantic AST contract for a documented reason. */
  IRRELEVANT(false),

  /** The feature emits a stable diagnostic and prevents a complete analysis result when encountered. */
  DIAGNOSED(true);

  private final boolean completionBlocking;

  LanguageFeaturePolicy(boolean blocksCompletion) {
    completionBlocking = blocksCompletion;
  }

  /**
   * Indicates whether an encountered feature with this policy prevents completion.
   *
   * @return {@code true} only for {@link #DIAGNOSED}
   */
  public boolean blocksCompletion() {
    return completionBlocking;
  }
}
