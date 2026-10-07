package org.aquilagraph.application;

/** Normal exact-subject lookup after required normal resource closure. */
public enum AstLookupStatus {
  /** Present supported facts were intrinsically admitted and exactly qualified. */
  FOUND,
  /** The exact requested source result is absent. */
  NOT_FOUND,
  /** Present stored facts use an unsupported source storage format. */
  INCOMPATIBLE
}
