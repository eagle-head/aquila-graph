package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable public-compiler annotation values copied without retaining compiler objects. */
public sealed interface CoverageAnnotationValue {
  /**
   * Encodes every copied semantic component as canonical, length-framed text.
   *
   * @return lossless semantic text independent of compiler presentation formats
   */
  default String canonicalText() {
    return CoverageAnnotationValueSupport.canonicalText(this);
  }

  /**
   * Exact scalar kind and value; characters use their integer UTF-16 code unit.
   *
   * @param kind
   *          copied Java scalar category
   * @param value
   *          exact scalar text; characters use their integer UTF-16 code unit
   */
  record Scalar(Kind kind, String value) implements CoverageAnnotationValue {
    /** Scalar annotation value categories. */
    public enum Kind {
      /** Boolean value. */
      BOOLEAN,
      /** Byte value. */
      BYTE,
      /** Short value. */
      SHORT,
      /** Character value. */
      CHAR,
      /** Integer value. */
      INT,
      /** Long value. */
      LONG,
      /** Float value. */
      FLOAT,
      /** Double value. */
      DOUBLE,
      /** String value. */
      STRING
    }

    /** Requires the exact copied category and value. */
    public Scalar {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * Selected enum constant and its canonical type owner.
   *
   * @param declarationKey
   *          canonical selected enum constant identity
   * @param ownerTypeKey
   *          canonical declaring enum type identity
   */
  record EnumConstant(String declarationKey, String ownerTypeKey) implements CoverageAnnotationValue {
    /** Requires both copied canonical identities. */
    public EnumConstant {
      CoverageAnnotationValueSupport.requireKey(declarationKey);
      CoverageAnnotationValueSupport.requireKey(ownerTypeKey);
    }
  }

  /**
   * Selected class-literal operand with recursive array and enclosing type structure.
   *
   * @param operand
   *          copied recursive class-literal type
   */
  record ClassLiteral(TypeOperand operand) implements CoverageAnnotationValue {
    /** Requires the copied operand. */
    public ClassLiteral {
      Objects.requireNonNull(operand, "operand");
    }
  }

  /**
   * Primitive, declared, or array operand admissible in an annotation class literal.
   *
   * @param category
   *          copied type category
   * @param declarationKey
   *          canonical declared type identity, when present
   * @param lexicalOwnerKey
   *          canonical lexical owner identity, when present
   * @param enclosingType
   *          copied enclosing type, when present
   * @param components
   *          ordered recursive type components; an array has one component
   */
  record TypeOperand(JavaTypeCategory category, Optional<String> declarationKey, Optional<String> lexicalOwnerKey,
      Optional<TypeOperand> enclosingType, List<TypeOperand> components) {
    /** Copies recursive operands and checks their category-specific shape. */
    public TypeOperand {
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(declarationKey, "declarationKey").ifPresent(CoverageAnnotationValueSupport::requireKey);
      Objects.requireNonNull(lexicalOwnerKey, "lexicalOwnerKey").ifPresent(CoverageAnnotationValueSupport::requireKey);
      Objects.requireNonNull(enclosingType, "enclosingType");
      components = List.copyOf(Objects.requireNonNull(components, "components"));
      if ((category == JavaTypeCategory.DECLARED) != declarationKey.isPresent()
          || (category == JavaTypeCategory.ARRAY && components.size() != 1)
          || (category != JavaTypeCategory.DECLARED && category != JavaTypeCategory.ARRAY && !components.isEmpty())) {
        throw new IllegalArgumentException("A class-literal operand must preserve its exact type category and recursive shape");
      }
    }
  }

  /**
   * Annotation type and explicitly supplied values in public compiler order.
   *
   * @param typeKey
   *          canonical annotation type identity
   * @param explicitValues
   *          explicit element values in copied compiler order
   * @param provenance
   *          relationship of the annotation to written source
   */
  record Annotation(String typeKey, List<Entry> explicitValues,
      CoveragePropertyValue.Provenance provenance) implements CoverageAnnotationValue {
    /** Copies canonical type, keyed values, and source relationship. */
    public Annotation {
      CoverageAnnotationValueSupport.requireKey(typeKey);
      explicitValues = List.copyOf(Objects.requireNonNull(explicitValues, "explicitValues"));
      Objects.requireNonNull(provenance, "provenance");
      if (explicitValues.stream().map(Entry::elementKey).distinct().count() != explicitValues.size()) {
        throw new IllegalArgumentException("Annotation values require unique selected element keys");
      }
    }
  }

  /**
   * One explicitly supplied annotation element value.
   *
   * @param elementKey
   *          canonical selected annotation element identity
   * @param value
   *          copied typed element value
   */
  record Entry(String elementKey, CoverageAnnotationValue value) {
    /** Requires the copied element identity and typed value. */
    public Entry {
      CoverageAnnotationValueSupport.requireKey(elementKey);
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * Ordered values, including repeated equal entries and empty arrays.
   *
   * @param elements
   *          complete copied element sequence
   */
  record Array(List<CoverageAnnotationValue> elements) implements CoverageAnnotationValue {
    /** Copies the complete ordered value sequence. */
    public Array {
      elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
    }
  }

}
