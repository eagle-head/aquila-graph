package org.aquilagraph.syntax;

import java.util.Objects;
import org.aquilagraph.model.JavaTypeIdentity;

/** Compiler type outcome attached directly to transient syntax evidence. */
public sealed interface SyntaxType {
  /** Indicates that a syntax node does not denote a typed Java value. */
  enum NotApplicable implements SyntaxType {
    /** Singleton outcome. */
    INSTANCE
  }

  /** Indicates that a value-bearing syntax node has no compiler type available. */
  enum Unresolved implements SyntaxType {
    /** Singleton outcome. */
    INSTANCE
  }

  /** Indicates that javac reported an error type rather than a resolved Java type. */
  enum Erroneous implements SyntaxType {
    /** Singleton outcome. */
    INSTANCE
  }

  /**
   * Compiler-resolved type identity.
   *
   * @param identity
   *          resolved Java type
   */
  record Resolved(JavaTypeIdentity identity) implements SyntaxType {
    /** Validates the resolved identity. */
    public Resolved {
      Objects.requireNonNull(identity, "identity");
    }
  }
}
