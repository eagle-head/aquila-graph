package org.aquilagraph.syntax;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
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
   * A compiler pseudo-type encountered where a written type was required.
   *
   * @param kind
   *          pseudo-type category reported by the compiler
   */
  record NoType(Kind kind) implements SyntaxType {
    /** Compiler-neutral pseudo-type category. */
    public enum Kind {
      /** No actual type exists. */
      NONE,
      /** Package pseudo-type. */
      PACKAGE,
      /** Module pseudo-type. */
      MODULE
    }

    /** Validates the pseudo-type category. */
    public NoType {
      Objects.requireNonNull(kind, "kind");
    }
  }

  /** An implementation-reserved compiler type outcome. */
  enum Unsupported implements SyntaxType {
    /** Singleton outcome. */
    INSTANCE
  }

  /**
   * A compiler type whose required recursive components could not be projected.
   *
   * @param identity
   *          compiler-resolved identity retained for diagnostics
   */
  record StructureUnavailable(JavaTypeIdentity identity) implements SyntaxType {
    /** Validates the compiler-resolved identity retained for diagnostics. */
    public StructureUnavailable {
      Objects.requireNonNull(identity, "identity");
    }
  }

  /**
   * Compiler-resolved type identity.
   *
   * @param identity
   *          resolved Java type presentation and category
   * @param structure
   *          recursive compiler-derived components when the occurrence requires them
   */
  record Resolved(JavaTypeIdentity identity, Optional<JavaTypeStructure> structure) implements SyntaxType {
    /** Validates the resolved identity. */
    public Resolved {
      Objects.requireNonNull(identity, "identity");
      Objects.requireNonNull(structure, "structure");
      if (structure.isEmpty() && (identity.category() == JavaTypeCategory.WILDCARD || identity.category() == JavaTypeCategory.INTERSECTION
          || identity.category() == JavaTypeCategory.UNION)) {
        throw new IllegalArgumentException("A composite resolved type requires recursive structure");
      }
      structure.ifPresent(value -> {
        var matches = switch (value) {
          case JavaTypeStructure.Primitive primitive -> identity.category() == primitive.kind();
          case JavaTypeStructure.Declared ignored -> identity.category() == JavaTypeCategory.DECLARED;
          case JavaTypeStructure.LocalDeclared ignored -> identity.category() == JavaTypeCategory.DECLARED;
          case JavaTypeStructure.Variable ignored -> identity.category() == JavaTypeCategory.TYPE_VARIABLE;
          case JavaTypeStructure.Array ignored -> identity.category() == JavaTypeCategory.ARRAY;
          case JavaTypeStructure.Wildcard ignored -> identity.category() == JavaTypeCategory.WILDCARD;
          case JavaTypeStructure.Intersection ignored -> identity.category() == JavaTypeCategory.INTERSECTION;
          case JavaTypeStructure.Union ignored -> identity.category() == JavaTypeCategory.UNION;
        };
        if (!matches) {
          throw new IllegalArgumentException("Resolved type category conflicts with recursive structure");
        }
      });
    }

    /**
     * Creates an identity-only resolved type for a context without required recursive components.
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
