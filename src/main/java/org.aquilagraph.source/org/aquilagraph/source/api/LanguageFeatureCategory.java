package org.aquilagraph.source.api;

/** Finite language-feature categories governed by the release-bound coverage contract. */
public enum LanguageFeatureCategory {
  /** Compiler tree kinds exposed by the supported JDK. */
  COMPILER_TREE_KIND,

  /** Attributed type kinds exposed by the supported JDK. */
  COMPILER_TYPE_KIND,

  /** Java-language declaration properties exposed by compiler tree and element APIs. */
  DECLARATION_PROPERTY
}
