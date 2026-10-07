package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Copied scalar or ordered accessor items, with unavailable distinct from absence.
 *
 * @param state
 *          exact accessor presence and cardinality state
 * @param items
 *          ordered copied values consistent with the state
 */
public record CoveragePropertyValue(State state, List<Item> items) {
  /** Validates exact scalar/list cardinality and copied provenance. */
  public CoveragePropertyValue {
    Objects.requireNonNull(state, "state");
    items = List.copyOf(Objects.requireNonNull(items, "items"));
    if ((state == State.SCALAR && items.size() != 1)
        || ((state == State.ABSENT || state == State.EMPTY || state == State.UNAVAILABLE) && !items.isEmpty())
        || (state == State.ITEMS && items.isEmpty())) {
      throw new IllegalArgumentException("Property value state and item cardinality disagree");
    }
  }

  /**
   * Encodes exact accessor modifier flags without treating parser-supplied flags as lexical tokens.
   *
   * @param flags
   *          complete ordered accessor flags
   * @return canonical length-framed flag text
   */
  public static String canonicalFlags(List<String> flags) {
    var copied = List.copyOf(Objects.requireNonNull(flags, "flags"));
    var result = new StringBuilder("M(").append(copied.size()).append(":[");
    for (var flag : copied) {
      result.append(flag.length()).append(':').append(flag);
    }
    return result.append("])").toString();
  }

  /** Exact accessor presence state. */
  public enum State {
    /** One scalar. */
    SCALAR,
    /** Nonempty ordered list. */
    ITEMS,
    /** Absent optional scalar. */
    ABSENT,
    /** Successfully queried empty list. */
    EMPTY,
    /** Unavailable query; never successful absence. */
    UNAVAILABLE
  }

  /**
   * Copied accessor item identity, value, and source relationship.
   *
   * @param identity
   *          exact accessor item identity
   * @param value
   *          copied accessor text
   * @param provenance
   *          relationship of the item to written source
   * @param executableType
   *          copied executable components, when present
   * @param typeIdentity
   *          copied type identity, when present
   * @param modifierFlags
   *          complete ordered accessor flags, when present
   * @param annotationValue
   *          copied typed annotation components, when present
   * @param typeStructure
   *          copied recursive type components, when present
   */
  public record Item(String identity, String value, Provenance provenance, Optional<CoverageExecutableType> executableType,
      Optional<JavaTypeIdentity> typeIdentity, Optional<List<String>> modifierFlags, Optional<CoverageAnnotationValue> annotationValue,
      Optional<CoverageTypeStructure> typeStructure) {
    /** Validates copied item data. */
    public Item {
      Objects.requireNonNull(identity, "identity");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(provenance, "provenance");
      Objects.requireNonNull(executableType, "executableType");
      Objects.requireNonNull(typeIdentity, "typeIdentity");
      modifierFlags = Objects.requireNonNull(modifierFlags, "modifierFlags").map(List::copyOf);
      Objects.requireNonNull(annotationValue, "annotationValue");
      Objects.requireNonNull(typeStructure, "typeStructure");
      if (identity.isBlank()) {
        throw new IllegalArgumentException("Property items require an exact identity");
      }
    }

    /**
     * Creates an accessor item that has no executable-type components.
     *
     * @param identity
     *          exact accessor item identity
     * @param value
     *          copied accessor text
     * @param provenance
     *          relationship of the item to written source
     */
    public Item(String identity, String value, Provenance provenance) {
      this(identity, value, provenance, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Creates an item with copied executable components.
     *
     * @param identity
     *          exact accessor item identity
     * @param value
     *          copied accessor text
     * @param provenance
     *          relationship of the item to written source
     * @param executableType
     *          copied executable components, when present
     */
    public Item(String identity, String value, Provenance provenance, Optional<CoverageExecutableType> executableType) {
      this(identity, value, provenance, executableType, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Creates an item with copied type identity or accessor flags.
     *
     * @param identity
     *          exact accessor item identity
     * @param value
     *          copied accessor text
     * @param provenance
     *          relationship of the item to written source
     * @param executableType
     *          copied executable components, when present
     * @param typeIdentity
     *          copied type identity, when present
     * @param modifierFlags
     *          complete ordered accessor flags, when present
     */
    public Item(String identity, String value, Provenance provenance, Optional<CoverageExecutableType> executableType,
        Optional<JavaTypeIdentity> typeIdentity, Optional<List<String>> modifierFlags) {
      this(identity, value, provenance, executableType, typeIdentity, modifierFlags, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an item with typed annotation components.
     *
     * @param identity
     *          exact accessor item identity
     * @param value
     *          copied accessor text
     * @param provenance
     *          relationship of the item to written source
     * @param executableType
     *          copied executable components, when present
     * @param typeIdentity
     *          copied type identity, when present
     * @param modifierFlags
     *          complete ordered accessor flags, when present
     * @param annotationValue
     *          copied typed annotation components, when present
     */
    public Item(String identity, String value, Provenance provenance, Optional<CoverageExecutableType> executableType,
        Optional<JavaTypeIdentity> typeIdentity, Optional<List<String>> modifierFlags, Optional<CoverageAnnotationValue> annotationValue) {
      this(identity, value, provenance, executableType, typeIdentity, modifierFlags, annotationValue, Optional.empty());
    }
  }

  /** Distinguishes source items from compiler corroboration. */
  public enum Provenance {
    /** Present in parsed source. */
    WRITTEN,
    /** Implicit or compiler-derived semantic item. */
    COMPILER_DERIVED
  }
}
