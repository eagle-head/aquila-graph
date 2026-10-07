package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Exact reference to an actual retained declaration or source occurrence.
 *
 * @param kind
 *          retained fact boundary
 * @param identity
 *          exact canonical declaration or source occurrence identity
 * @param sourceFileKey
 *          captured source that owns the fact
 * @param value
 *          complete copied fact value
 * @param origin
 *          available source span belonging to the same captured source
 */
public record CoverageFactReference(Kind kind, String identity, String sourceFileKey, CoverageFactValue value,
    Optional<SourceSpan> origin) {
  /** Validates the immutable reference projection. */
  public CoverageFactReference {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(origin, "origin");
    if (identity.isBlank() || sourceFileKey.isBlank() || origin.filter(span -> !span.sourceFileKey().equals(sourceFileKey)).isPresent()) {
      throw new IllegalArgumentException("Fact references require exact identity, value and source ownership");
    }
  }

  /** Owned fact boundaries. */
  public enum Kind {
    /** Type declaration. */
    TYPE,
    /** Executable declaration. */
    METHOD,
    /** Field declaration. */
    FIELD,
    /** Formal parameter. */
    PARAMETER,
    /** Record component. */
    RECORD_COMPONENT,
    /** Compiler-derived counterpart. */
    SYNTHESIZED_DECLARATION,
    /** Aggregate-owned canonical direct-member order. */
    DECLARATION_MEMBER_ORDER,
    /** Aggregate-owned public declaration type components. */
    DECLARATION_TYPE,
    /** Source AST occurrence. */
    SYNTAX
  }
}
