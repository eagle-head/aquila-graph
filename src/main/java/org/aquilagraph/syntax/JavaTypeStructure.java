package org.aquilagraph.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.JavaTypeCategory;

/** Compiler-neutral recursive semantic components of an attributed source type use. */
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
        if (!(value instanceof Declared declared)) {
          throw new IllegalArgumentException("A declared enclosing type must itself be declared");
        }
        if (!lexicalOwnerKey.equals(Optional.of(declared.declarationKey()))) {
          throw new IllegalArgumentException("An enclosing instance type must match the lexical owner declaration");
        }
      });
      arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }

    private static Optional<String> enclosingKey(Optional<JavaTypeStructure> enclosingType) {
      return Objects.requireNonNull(enclosingType, "enclosingType").map(value -> {
        if (!(value instanceof Declared declared)) {
          throw new IllegalArgumentException("A declared enclosing type must itself be declared");
        }
        return declared.declarationKey();
      });
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
}
