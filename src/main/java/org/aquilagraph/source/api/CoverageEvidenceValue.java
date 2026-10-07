package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/** Closed immutable components of a copied source fact, with exact optional and sequence states. */
public sealed interface CoverageEvidenceValue {
  /**
   * Text value.
   *
   * @param value
   *          exact copied text
   */
  record Text(String value) implements CoverageEvidenceValue {
    /** Requires copied text. */
    public Text {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Typed enum identity.
   *
   * @param value
   *          exact enum identity
   */
  record Named(String value) implements CoverageEvidenceValue {
    /** Requires an identity. */
    public Named {
      if (CoverageValueValidation.requireValue(value).isBlank()) {
        throw new IllegalArgumentException("An enum evidence value requires an identity");
      }
    }
  }

  /**
   * Integral value; its owning semantic tag retains its Java scalar kind.
   *
   * @param value
   *          copied integral value
   */
  record Integral(long value) implements CoverageEvidenceValue {
  }

  /**
   * Floating value; its owning semantic tag distinguishes float and double.
   *
   * @param value
   *          copied floating value
   */
  record Decimal(double value) implements CoverageEvidenceValue {
  }

  /**
   * Boolean value.
   *
   * @param value
   *          copied flag
   */
  record Flag(boolean value) implements CoverageEvidenceValue {
  }

  /**
   * Exact type identity.
   *
   * @param value
   *          copied type identity
   */
  record TypeIdentity(JavaTypeIdentity value) implements CoverageEvidenceValue {
    /** Requires the immutable identity. */
    public TypeIdentity {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Exact source span.
   *
   * @param value
   *          copied source span
   */
  record Origin(SourceSpan value) implements CoverageEvidenceValue {
    /** Requires the immutable span. */
    public Origin {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Exact invocation semantics.
   *
   * @param value
   *          copied invocation semantics
   */
  record Invocation(InvocationSemantics value) implements CoverageEvidenceValue {
    /** Requires the immutable invocation value. */
    public Invocation {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Ordered sequence, including an empty sequence.
   *
   * @param values
   *          copied components
   */
  record Sequence(List<CoverageEvidenceValue> values) implements CoverageEvidenceValue {
    /** Copies ordered components. */
    public Sequence {
      values = List.copyOf(Objects.requireNonNull(values, "values"));
    }
  }

  /**
   * Optional component, distinct from an empty sequence.
   *
   * @param value
   *          copied optional value
   */
  record OptionalValue(Optional<CoverageEvidenceValue> value) implements CoverageEvidenceValue {
    /** Requires the exact optional state. */
    public OptionalValue {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Closed semantic product with named, ordered components.
   *
   * @param tag
   *          semantic form
   * @param fields
   *          copied fields
   */
  record Structure(Tag tag, List<Field> fields) implements CoverageEvidenceValue {
    /** Copies fields and rejects duplicate field names. */
    public Structure {
      Objects.requireNonNull(tag, "tag");
      fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
      if (fields.stream().map(Field::name).distinct().count() != fields.size()) {
        throw new IllegalArgumentException("A semantic evidence structure requires unique field names");
      }
    }
  }

  /**
   * One named semantic component.
   *
   * @param name
   *          domain field name
   * @param value
   *          copied value
   */
  record Field(String name, CoverageEvidenceValue value) {
    /** Requires a name and closed value. */
    public Field {
      if (Objects.requireNonNull(name, "name").isBlank()) {
        throw new IllegalArgumentException("An evidence field requires a name");
      }
      CoverageValueValidation.requireValue(value);
    }
  }

  /** Semantic value forms; these are data identities, not implementation class identities. */
  enum Tag {
    /** Written diamond syntax without written type arguments. */
    DIAMOND_TYPE,
    /** No additional syntax value. */
    NONE,
    /** Shared physical annotation identities. */
    SHARED_TYPE_ANNOTATIONS,
    /** Written primitive type. */
    PRIMITIVE_TYPE,
    /** Written package qualifier. */
    PACKAGE_QUALIFIER,
    /** Written module declaration. */
    MODULE_DECLARATION,
    /** Requires directive. */
    REQUIRES,
    /** Exports directive. */
    EXPORTS,
    /** Opens directive. */
    OPENS,
    /** Uses directive. */
    USES,
    /** Provides directive. */
    PROVIDES,
    /** Local declaration. */
    LOCAL_DECLARATION,
    /** Local binding identity and written shape. */
    LOCAL_BINDING,
    /** Written local type. */
    WRITTEN_TYPE,
    /** Annotation argument. */
    ANNOTATION_ARGUMENT,
    /** Annotation occurrence. */
    ANNOTATION,
    /** Type declaration. */
    TYPE_DECLARATION,
    /** Type declaration shape. */
    TYPE_DECLARATION_SHAPE,
    /** Variable declaration. */
    VARIABLE_DECLARATION,
    /** Record component. */
    RECORD_COMPONENT,
    /** Record counterpart identities. */
    RECORD_COUNTERPARTS,
    /** Type parameter. */
    TYPE_PARAMETER,
    /** Executable declaration. */
    EXECUTABLE_DECLARATION,
    /** Executable shape. */
    EXECUTABLE_SHAPE,
    /** Attributed executable header. */
    ATTRIBUTED_HEADER,
    /** Parameter declaration. */
    PARAMETER,
    /** Receiver declaration. */
    RECEIVER_PARAMETER,
    /** Written source symbol. */
    SOURCE_SYMBOL,
    /** Compilation unit. */
    COMPILATION_UNIT,
    /** Package declaration. */
    PACKAGE_DECLARATION,
    /** Exact written package-name kind and range. */
    PACKAGE_QUALIFIER_EVIDENCE,
    /** Import declaration. */
    IMPORT_DECLARATION,
    /** Field access. */
    FIELD_ACCESS,
    /** Invocation source form. */
    INVOCATION,
    /** Receiver keyword. */
    RECEIVER,
    /** Literal text. */
    LITERAL,
    /** Operator. */
    OPERATOR,
    /** Type display name. */
    TYPE_NAME,
    /** Switch case. */
    SWITCH_CASE,
    /** Default label. */
    DEFAULT_LABEL,
    /** Expression label attachment. */
    EXPRESSION_LABEL,
    /** Unsupported source syntax. */
    UNSUPPORTED_SYNTAX,
    /** Not applicable type. */
    TYPE_NOT_APPLICABLE,
    /** Unresolved type. */
    TYPE_UNRESOLVED,
    /** Erroneous type. */
    TYPE_ERRONEOUS,
    /** Pseudo type. */
    TYPE_NONE,
    /** Unsupported compiler type. */
    TYPE_UNSUPPORTED,
    /** Unavailable recursive structure. */
    TYPE_STRUCTURE_UNAVAILABLE,
    /** Resolved compiler type. */
    TYPE_RESOLVED,
    /** Primitive recursive type. */
    TYPE_PRIMITIVE,
    /** Declared recursive type. */
    TYPE_DECLARED,
    /** Local recursive type. */
    TYPE_LOCAL_DECLARED,
    /** Selected variable type. */
    TYPE_VARIABLE,
    /** Implicit variable with complete bounds and a type-local binder. */
    TYPE_CAPTURED,
    /** Reference to a captured variable in the same type structure. */
    TYPE_CAPTURE_REFERENCE,
    /** Recursive array. */
    TYPE_ARRAY,
    /** Wildcard form. */
    TYPE_WILDCARD,
    /** Ordered intersection. */
    TYPE_INTERSECTION,
    /** Ordered union. */
    TYPE_UNION,
    /** Boolean annotation scalar. */
    ANNOTATION_BOOLEAN,
    /** Byte annotation scalar. */
    ANNOTATION_BYTE,
    /** Short annotation scalar. */
    ANNOTATION_SHORT,
    /** Character annotation scalar. */
    ANNOTATION_CHAR,
    /** Integer annotation scalar. */
    ANNOTATION_INT,
    /** Long annotation scalar. */
    ANNOTATION_LONG,
    /** Float annotation scalar. */
    ANNOTATION_FLOAT,
    /** Double annotation scalar. */
    ANNOTATION_DOUBLE,
    /** String annotation scalar. */
    ANNOTATION_STRING,
    /** Selected enum annotation value. */
    ANNOTATION_ENUM,
    /** Class literal annotation value. */
    ANNOTATION_CLASS_LITERAL,
    /** Nested annotation value. */
    ANNOTATION_NESTED,
    /** Ordered annotation array. */
    ANNOTATION_ARRAY
  }
}
