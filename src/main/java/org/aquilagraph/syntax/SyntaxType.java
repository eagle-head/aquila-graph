package org.aquilagraph.syntax;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeIdentity;

/** Compiler type outcome projected onto a durable source occurrence. */
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
   *          resolved Java type presentation and category
   * @param structure
   *          recursive compiler-derived components when projected for a source type use
   */
  record Resolved(JavaTypeIdentity identity, Optional<JavaTypeStructure> structure) implements SyntaxType {
    /** Validates the resolved identity. */
    public Resolved {
      Objects.requireNonNull(identity, "identity");
      Objects.requireNonNull(structure, "structure");
    }

    /**
     * Creates a resolved type before recursive source-type projection applies.
     *
     * @param identity
     *          resolved Java type identity
     */
    public Resolved(JavaTypeIdentity identity) {
      this(identity, Optional.empty());
    }

    /**
     * Creates a resolved type with compiler-derived recursive components.
     *
     * @param identity
     *          resolved Java type identity
     * @param structure
     *          recursive compiler-derived components
     */
    public Resolved(JavaTypeIdentity identity, JavaTypeStructure structure) {
      this(identity, Optional.of(Objects.requireNonNull(structure, "structure")));
    }
  }
}
