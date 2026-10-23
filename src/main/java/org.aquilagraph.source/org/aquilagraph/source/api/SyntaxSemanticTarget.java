package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Explicit project-owned endpoint of a semantic relation. */
public sealed interface SyntaxSemanticTarget {
  /** Exact type destination of one written annotation. */
  sealed interface AnnotationTypeLocation {
    /**
     * One array dimension written on a creation expression, distinct from its component type.
     *
     * @param nodeKey
     *          canonical array creation occurrence
     * @param dimension
     *          zero-based sized dimension, or zero for the initialized creation's first omitted dimension
     */
    record ArrayDimension(String nodeKey, int dimension) implements AnnotationTypeLocation {
      /** Validates the occurrence and dimension ordinal. */
      public ArrayDimension {
        SyntaxValueSupport.requireText(nodeKey, "array dimension nodeKey");
        if (dimension < 0) {
          throw new IllegalArgumentException("An array dimension ordinal cannot be negative");
        }
      }
    }
    /**
     * A source type-use occurrence, including its typed structural path.
     *
     * @param nodeKey
     *          source type-use node key
     */
    record Written(String nodeKey) implements AnnotationTypeLocation {
      /** Validates the source occurrence key. */
      public Written {
        SyntaxValueSupport.requireText(nodeKey, "annotation type-use nodeKey");
      }
    }

    /**
     * The constructed type of a source constructor declaration or enum constant.
     *
     * @param declarationKey
     *          owning type declaration key
     */
    record Constructed(String declarationKey) implements AnnotationTypeLocation {
      /** Validates the constructed type key. */
      public Constructed {
        SyntaxValueSupport.requireText(declarationKey, "constructed annotation declarationKey");
      }
    }
  }

  /**
   * One physical annotation attachment with its complete declaration and type applicability.
   *
   * @param ownerNodeKey
   *          source owner node key
   * @param declarationNodeKey
   *          applicable declaration node key, if any
   * @param typeLocation
   *          applicable type location, if any
   * @param propagatedCounterparts
   *          implicit record counterparts receiving declaration or type applicability
   * @param fieldDeclarators
   *          additional source declarators receiving this shared field-header annotation
   */
  record AnnotationApplication(String ownerNodeKey, Optional<String> declarationNodeKey, Optional<AnnotationTypeLocation> typeLocation,
      List<PropagatedCounterpart> propagatedCounterparts,
      List<FieldDeclaratorApplication> fieldDeclarators) implements SyntaxSemanticTarget {
    /**
     * A further written field declarator receiving one shared physical annotation.
     *
     * @param fieldNodeKey
     *          written field declarator identity
     * @param declarationApplicable
     *          declaration-target applicability
     * @param typeLocation
     *          exact type context in this declarator
     */
    public record FieldDeclaratorApplication(String fieldNodeKey, boolean declarationApplicable,
        Optional<AnnotationTypeLocation> typeLocation) {
      /** Validates a nonempty source application. */
      public FieldDeclaratorApplication {
        SyntaxValueSupport.requireText(fieldNodeKey, "field declarator node key");
        Objects.requireNonNull(typeLocation, "typeLocation");
        if ((!declarationApplicable && typeLocation.isEmpty())
            || (typeLocation.isPresent() && !(typeLocation.orElseThrow() instanceof AnnotationTypeLocation.Written))) {
          throw new IllegalArgumentException("A field declarator application requires a declaration or written type destination");
        }
      }
    }
    /**
     * One compiler-correlated counterpart receiving a declaration annotation, a type annotation, or both.
     *
     * @param reference
     *          counterpart declaration identity
     * @param declarationApplicable
     *          declaration-target applicability
     * @param typeLocation
     *          source type position propagated into the counterpart's declared type
     */
    public record PropagatedCounterpart(SemanticReference reference, boolean declarationApplicable,
        Optional<AnnotationTypeLocation> typeLocation) {
      /** Validates a nonempty counterpart application. */
      public PropagatedCounterpart {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(typeLocation, "typeLocation");
        if (!declarationApplicable && typeLocation.isEmpty()) {
          throw new IllegalArgumentException("A propagated counterpart requires declaration or type applicability");
        }
        if (reference.status() != ResolutionStatus.RESOLVED || reference.symbolKey().isEmpty()
            || !Set.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER).contains(reference.referenceKind())
            || (typeLocation.isPresent() && !(typeLocation.orElseThrow() instanceof AnnotationTypeLocation.Written))) {
          throw new IllegalArgumentException("A propagated counterpart requires a resolved record member and written type position");
        }
      }
    }

    /** Validates the target's immutable source site and effective destinations. */
    public AnnotationApplication {
      SyntaxValueSupport.requireText(ownerNodeKey, "annotation ownerNodeKey");
      Objects.requireNonNull(declarationNodeKey, "declarationNodeKey")
          .ifPresent(key -> SyntaxValueSupport.requireText(key, "annotation declarationNodeKey"));
      Objects.requireNonNull(typeLocation, "typeLocation");
      propagatedCounterparts = List.copyOf(Objects.requireNonNull(propagatedCounterparts, "propagatedCounterparts"));
      fieldDeclarators = List.copyOf(Objects.requireNonNull(fieldDeclarators, "fieldDeclarators"));
      if (fieldDeclarators.stream().map(FieldDeclaratorApplication::fieldNodeKey).distinct().count() != fieldDeclarators.size()) {
        throw new IllegalArgumentException("A shared annotation requires distinct field declarator destinations");
      }
    }

  }

  /**
   * A source occurrence owned by the same durable AST.
   *
   * @param nodeKey
   *          target source-occurrence key
   */
  record SourceOccurrence(String nodeKey) implements SyntaxSemanticTarget {
    /** Validates the target occurrence key. */
    public SourceOccurrence {
      Objects.requireNonNull(nodeKey, "nodeKey");
      if (nodeKey.isBlank()) {
        throw new IllegalArgumentException("A semantic source-occurrence target key cannot be blank");
      }
    }
  }

  /**
   * An explicit compiler-derived declaration reference, including external declarations.
   *
   * @param reference
   *          project-owned reference value
   */
  record Declaration(SemanticReference reference) implements SyntaxSemanticTarget {
    /** Validates the declaration reference. */
    public Declaration {
      Objects.requireNonNull(reference, "reference");
    }
  }

}
