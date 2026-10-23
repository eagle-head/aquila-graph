package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;

/** Compiler-neutral attributed value of a written annotation element or default. */
public sealed interface AnnotationElementValue permits AnnotationScalar, AnnotationElementValue.Nested, AnnotationElementValue.Array {
  /**
   * Attributed nested annotation type; its written arguments belong to its source node.
   *
   * @param annotationTypeKey
   *          canonical nested annotation type identity
   */
  record Nested(String annotationTypeKey) implements AnnotationElementValue {
    /** Requires a canonical annotation type identity. */
    public Nested {
      if (Objects.requireNonNull(annotationTypeKey, "annotationTypeKey").isBlank()) {
        throw new IllegalArgumentException("A nested annotation requires a type identity");
      }
    }
  }

  /**
   * Ordered attributed array values, including repeated equal values.
   *
   * @param elements
   *          ordered component values
   */
  record Array(List<AnnotationElementValue> elements) implements AnnotationElementValue {
    /** Retains an immutable ordered value sequence. */
    public Array {
      elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
    }
  }
}
