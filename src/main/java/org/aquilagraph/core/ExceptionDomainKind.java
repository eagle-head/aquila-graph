package org.aquilagraph.core;

/** Non-null runtime classes represented by one construction-time exception effect. */
enum ExceptionDomainKind {
  EXACT, SUBTYPE_BOUND, UNKNOWN
}
