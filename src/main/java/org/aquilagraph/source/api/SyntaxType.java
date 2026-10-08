package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/** Compiler type outcome projected onto a durable source occurrence. */
public sealed interface SyntaxType {
  /**
   * Returns a retained compiler-resolved identity independently of recursive component availability.
   *
   * @return known identity, or empty when the outcome supplies none
   */
  default Optional<JavaTypeIdentity> knownIdentity() {
    return switch (this) {
      case Resolved resolved -> Optional.of(resolved.identity());
      case StructureUnavailable unavailable -> Optional.of(unavailable.identity());
      default -> Optional.empty();
    };
  }

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
        if (identity.category() != value.category()) {
          throw new IllegalArgumentException("Resolved type category conflicts with recursive structure");
        }
        if (value instanceof JavaTypeStructure.Executable executable) {
          if (!identity.erasedName().equals(executable.erasure().signature())) {
            throw new IllegalArgumentException("Executable identity erasure conflicts with its known signature components");
          }
        }
        validateCaptureScope(value, new HashSet<>());
      });
    }

    private static void validateCaptureScope(JavaTypeStructure value, Set<Integer> captures) {
      switch (value) {
        case JavaTypeStructure.Captured captured -> {
          if (captured.ordinal() != captures.size() || !captures.add(captured.ordinal())) {
            throw new IllegalArgumentException("Captured variable binders must have distinct first-encounter ordinals");
          }
          validateCaptureScope(captured.upperBound(), captures);
          captured.lowerBound().ifPresent(bound -> validateCaptureScope(bound, captures));
        }
        case JavaTypeStructure.CaptureReference reference -> {
          if (!captures.contains(reference.ordinal())) {
            throw new IllegalArgumentException("A captured variable reference requires a preceding binder in the same type structure");
          }
        }
        case JavaTypeStructure.Declared declared -> {
          declared.enclosingType().ifPresent(enclosing -> validateCaptureScope(enclosing, captures));
          declared.arguments().forEach(argument -> validateCaptureScope(argument, captures));
        }
        case JavaTypeStructure.LocalDeclared local -> {
          local.enclosingType().ifPresent(enclosing -> validateCaptureScope(enclosing, captures));
          local.arguments().forEach(argument -> validateCaptureScope(argument, captures));
        }
        case JavaTypeStructure.Array array -> validateCaptureScope(array.component(), captures);
        case JavaTypeStructure.Executable executable -> {
          executable.variables().forEach(variable -> validateCaptureScope(variable, captures));
          executable.parameters().forEach(Resolved::validateComponentScope);
          validateComponentScope(executable.returnType());
          executable.receiver().ifPresent(Resolved::validateComponentScope);
          executable.thrownTypes().forEach(Resolved::validateComponentScope);
        }
        case JavaTypeStructure.Wildcard wildcard -> wildcard.bound().ifPresent(bound -> validateCaptureScope(bound, captures));
        case JavaTypeStructure.Intersection intersection -> intersection.bounds().forEach(bound -> validateCaptureScope(bound, captures));
        case JavaTypeStructure.Union union -> union.alternatives().forEach(alternative -> validateCaptureScope(alternative, captures));
        case JavaTypeStructure.Primitive _ -> {
        }
        case JavaTypeStructure.Variable _ -> {
        }
      }
    }

    private static void validateComponentScope(SyntaxType component) {
      if (component instanceof Resolved resolved) {
        resolved.structure().ifPresent(value -> validateCaptureScope(value, new HashSet<>()));
      }
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
