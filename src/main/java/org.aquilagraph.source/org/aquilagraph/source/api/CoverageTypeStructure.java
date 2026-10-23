package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable selected public type components used by declaration-property evidence. */
public sealed interface CoverageTypeStructure {
  /**
   * Primitive, void, null, or unavailable component category.
   *
   * @param category
   *          exact copied type category
   */
  record Simple(JavaTypeCategory category) implements CoverageTypeStructure {
    /** Requires an exact copied category. */
    public Simple {
      Objects.requireNonNull(category, "category");
    }
  }

  /**
   * Canonical declared selection with lexical ownership and ordered arguments.
   *
   * @param declarationKey
   *          canonical selected declaration identity
   * @param lexicalOwnerKey
   *          canonical lexical owner identity, when present
   * @param enclosingType
   *          copied enclosing type, when present
   * @param arguments
   *          ordered copied type arguments
   */
  record Declared(String declarationKey, Optional<String> lexicalOwnerKey, Optional<CoverageTypeStructure> enclosingType,
      List<CoverageTypeStructure> arguments) implements CoverageTypeStructure {
    /** Copies all selected declaration components. */
    public Declared {
      if (Objects.requireNonNull(declarationKey, "declarationKey").isBlank()) {
        throw new IllegalArgumentException("A copied declared type requires a canonical selection");
      }
      Objects.requireNonNull(lexicalOwnerKey, "lexicalOwnerKey");
      Objects.requireNonNull(enclosingType, "enclosingType");
      arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }
  }

  /**
   * Canonical type-parameter selection.
   *
   * @param declarationKey
   *          canonical declaring type-parameter identity
   */
  record Variable(String declarationKey) implements CoverageTypeStructure {
    /** Requires a canonical parameter identity. */
    public Variable {
      if (Objects.requireNonNull(declarationKey, "declarationKey").isBlank()) {
        throw new IllegalArgumentException("A copied type variable requires its declaring parameter");
      }
    }
  }

  /**
   * One array dimension.
   *
   * @param component
   *          copied component type
   */
  record Array(CoverageTypeStructure component) implements CoverageTypeStructure {
    /** Requires the actual component. */
    public Array {
      Objects.requireNonNull(component, "component");
    }
  }

  /**
   * Public wildcard upper and lower components.
   *
   * @param upperBound
   *          copied upper bound, when present
   * @param lowerBound
   *          copied lower bound, when present, mutually exclusive with an upper bound
   */
  record Wildcard(Optional<CoverageTypeStructure> upperBound, Optional<CoverageTypeStructure> lowerBound) implements CoverageTypeStructure {
    /** Rejects simultaneous opposing bounds. */
    public Wildcard {
      Objects.requireNonNull(upperBound, "upperBound");
      Objects.requireNonNull(lowerBound, "lowerBound");
      if (upperBound.isPresent() && lowerBound.isPresent()) {
        throw new IllegalArgumentException("A copied wildcard cannot have both upper and lower bounds");
      }
    }
  }

  /**
   * Ordered public intersection bounds.
   *
   * @param bounds
   *          copied intersection bounds in compiler order
   */
  record Intersection(List<CoverageTypeStructure> bounds) implements CoverageTypeStructure {
    /** Copies actual bound order. */
    public Intersection {
      bounds = List.copyOf(Objects.requireNonNull(bounds, "bounds"));
    }
  }

  /**
   * Ordered public union alternatives.
   *
   * @param alternatives
   *          copied union alternatives in compiler order
   */
  record Union(List<CoverageTypeStructure> alternatives) implements CoverageTypeStructure {
    /** Copies actual alternative order. */
    public Union {
      alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
    }
  }
}
