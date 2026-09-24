package org.aquilagraph.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.JavaTypeCategory;

/** Compiler-neutral recursive semantic components of an attributed Java type. */
public sealed interface JavaTypeStructure {
  /**
   * Primitive or void result.
   *
   * @param kind
   *          exact primitive or void category
   */
  record Primitive(JavaTypeCategory kind) implements JavaTypeStructure {
    /** Validates the primitive category. */
    public Primitive {
      Objects.requireNonNull(kind, "kind");
      if (!Set.of(JavaTypeCategory.BOOLEAN, JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.INT, JavaTypeCategory.LONG,
          JavaTypeCategory.CHAR, JavaTypeCategory.FLOAT, JavaTypeCategory.DOUBLE, JavaTypeCategory.VOID).contains(kind)) {
        throw new IllegalArgumentException("A primitive type structure requires a primitive or void category");
      }
    }
  }

  /**
   * Selected declared type, lexical owner, enclosing instance type, and ordered arguments.
   *
   * @param declarationKey
   *          canonical selected declaration identity
   * @param lexicalOwnerKey
   *          canonical enclosing type declaration, when the selected type is a member type
   * @param enclosingType
   *          innermost enclosing instance type when the compiler supplies one
   * @param arguments
   *          actual type arguments in compiler order; empty for a raw or nongeneric use
   */
  record Declared(String declarationKey, Optional<String> lexicalOwnerKey, Optional<JavaTypeStructure> enclosingType,
      List<JavaTypeStructure> arguments) implements JavaTypeStructure {
    /**
     * Derives the lexical owner from an attributed enclosing instance when one exists.
     *
     * @param declarationKey
     *          canonical selected declaration identity
     * @param enclosingType
     *          innermost enclosing instance type when present
     * @param arguments
     *          actual type arguments in compiler order
     */
    public Declared(String declarationKey, Optional<JavaTypeStructure> enclosingType, List<JavaTypeStructure> arguments) {
      this(declarationKey, enclosingKey(enclosingType), enclosingType, arguments);
    }

    /** Validates declaration identity and component ownership. */
    public Declared {
      if (Objects.requireNonNull(declarationKey, "declarationKey").isBlank()) {
        throw new IllegalArgumentException("A declared type structure requires a declaration key");
      }
      Objects.requireNonNull(lexicalOwnerKey, "lexicalOwnerKey").ifPresent(key -> {
        if (key.isBlank()) {
          throw new IllegalArgumentException("A declared lexical owner requires a declaration key");
        }
      });
      Objects.requireNonNull(enclosingType, "enclosingType");
      enclosingType.ifPresent(value -> {
        if (!lexicalOwnerKey.equals(Optional.of(declaredKey(value)))) {
          throw new IllegalArgumentException("An enclosing instance type must match the lexical owner declaration");
        }
      });
      arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }

    private static Optional<String> enclosingKey(Optional<JavaTypeStructure> enclosingType) {
      return Objects.requireNonNull(enclosingType, "enclosingType").map(Declared::declaredKey);
    }

    private static String declaredKey(JavaTypeStructure value) {
      return switch (value) {
        case Declared declared -> declared.declarationKey();
        case LocalDeclared local -> local.declarationKey();
        default -> throw new IllegalArgumentException("A declared enclosing type must itself be declared");
      };
    }
  }

  /**
   * A local or anonymous declared type with independent lexical ownership and enclosing instance.
   *
   * @param declarationKey
   *          canonical local or anonymous declaration identity
   * @param lexicalOwnerKey
   *          canonical immediately enclosing declaration identity
   * @param kind
   *          local or anonymous source form
   * @param enclosingType
   *          enclosing instance type when supplied by the compiler
   * @param arguments
   *          actual type arguments in compiler order
   */
  record LocalDeclared(String declarationKey, String lexicalOwnerKey, Kind kind, Optional<JavaTypeStructure> enclosingType,
      List<JavaTypeStructure> arguments) implements JavaTypeStructure {
    /** Source declaration form. */
    public enum Kind {
      /** A named class declared in a local context. */
      LOCAL,
      /** An anonymous class declared by an object creation. */
      ANONYMOUS
    }

    /** Validates local declaration identity and compiler-derived components. */
    public LocalDeclared {
      if (Objects.requireNonNull(declarationKey, "declarationKey").isBlank()
          || Objects.requireNonNull(lexicalOwnerKey, "lexicalOwnerKey").isBlank()) {
        throw new IllegalArgumentException("A local declared type requires declaration and lexical owner keys");
      }
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(enclosingType, "enclosingType").ifPresent(value -> {
        if (!(value instanceof Declared || value instanceof LocalDeclared)) {
          throw new IllegalArgumentException("A local enclosing instance type must itself be declared");
        }
      });
      arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }
  }

  /**
   * Type variable selected by its declaring type or executable parameter identity.
   *
   * @param parameterKey
   *          canonical type-parameter declaration identity
   */
  record Variable(String parameterKey) implements JavaTypeStructure {
    /** Validates the declaring parameter identity. */
    public Variable {
      if (Objects.requireNonNull(parameterKey, "parameterKey").isBlank()) {
        throw new IllegalArgumentException("A type variable requires its declaring parameter key");
      }
    }
  }

  /**
   * One array dimension with a recursive compiler component.
   *
   * @param component
   *          immediate component type
   */
  record Array(JavaTypeStructure component) implements JavaTypeStructure {
    /** Validates the component. */
    public Array {
      Objects.requireNonNull(component, "component");
    }
  }

  /**
   * A wildcard with the one bound form written by the author.
   *
   * @param kind
   *          written bound form
   * @param bound
   *          explicit bound when the form requires one
   */
  record Wildcard(BoundKind kind, Optional<JavaTypeStructure> bound) implements JavaTypeStructure {
    /** Written wildcard bound form. */
    public enum BoundKind {
      /** No explicit bound. */
      UNBOUNDED,
      /** An explicit upper bound. */
      EXTENDS,
      /** An explicit lower bound. */
      SUPER
    }

    /** Rejects absent bounded components and invented unbounded components. */
    public Wildcard {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(bound, "bound");
      if ((kind == BoundKind.UNBOUNDED) != bound.isEmpty()) {
        throw new IllegalArgumentException("A wildcard bound must match its written form");
      }
    }
  }

  /**
   * Ordered components of an intersection type, including implicit compiler intersections.
   *
   * @param bounds
   *          ordered component bounds
   */
  record Intersection(List<JavaTypeStructure> bounds) implements JavaTypeStructure {
    /** Requires distinct, ordered bounds. */
    public Intersection {
      bounds = List.copyOf(Objects.requireNonNull(bounds, "bounds"));
      if (bounds.size() < 2 || Set.copyOf(bounds).size() != bounds.size()) {
        throw new IllegalArgumentException("An intersection requires at least two distinct bounds");
      }
    }
  }

  /**
   * Ordered alternatives of a union type.
   *
   * @param alternatives
   *          ordered alternative types
   */
  record Union(List<JavaTypeStructure> alternatives) implements JavaTypeStructure {
    /** Requires distinct, ordered alternatives. */
    public Union {
      alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
      if (alternatives.size() < 2 || Set.copyOf(alternatives).size() != alternatives.size()) {
        throw new IllegalArgumentException("A union requires at least two distinct alternatives");
      }
    }
  }
}
