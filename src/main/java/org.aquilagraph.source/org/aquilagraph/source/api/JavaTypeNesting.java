package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Lexical declaration ownership independent of enclosing-type membership.
 *
 * @param kind
 *          nesting category
 * @param ownerDetails
 *          copied compiler owner description, absent in historical values
 * @param lexicalOwnerKey
 *          compiler declaration enclosing this type, absent for a top-level type
 */
public record JavaTypeNesting(Kind kind, Optional<String> lexicalOwnerKey, Optional<OwnerDetails> ownerDetails) {
  /**
   * Copied compiler description of the lexical declaration.
   *
   * @param kind
   *          compiler declaration category
   * @param displayName
   *          compiler declaration display
   * @param written
   *          whether the declaration is explicit
   */
  public record OwnerDetails(String kind, String displayName, boolean written) {
    /** Requires copied nonblank category and display text. */
    public OwnerDetails {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(displayName, "displayName");
      if (kind.isBlank()) {
        throw new IllegalArgumentException("Lexical owner category cannot be blank");
      }
    }
  }

  /**
   * Creates historical nesting without a separately retained owner description.
   *
   * @param kind
   *          nesting category
   * @param lexicalOwnerKey
   *          canonical owner
   */
  public JavaTypeNesting(Kind kind, Optional<String> lexicalOwnerKey) {
    this(kind, lexicalOwnerKey, Optional.empty());
  }
  /** Written type nesting categories. */
  public enum Kind {
    /** Compilation-unit declaration. */
    TOP_LEVEL,
    /** Direct member of another type. */
    MEMBER,
    /** Named declaration within an execution scope. */
    LOCAL,
    /** Nameless class body of a creation expression. */
    ANONYMOUS
  }

  /** Requires an owner for every nested declaration. */
  public JavaTypeNesting {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(lexicalOwnerKey, "lexicalOwnerKey");
    Objects.requireNonNull(ownerDetails, "ownerDetails");
    if (lexicalOwnerKey.isEmpty() && ownerDetails.isPresent()) {
      throw new IllegalArgumentException("An owner description requires its canonical declaration");
    }
    if ((kind == Kind.TOP_LEVEL) != lexicalOwnerKey.isEmpty() || lexicalOwnerKey.filter(String::isBlank).isPresent()) {
      throw new IllegalArgumentException("Nested types require a nonblank lexical declaration owner");
    }
  }
}
