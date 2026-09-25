package org.aquilagraph.syntax;

import java.util.Objects;

/** Compiler-neutral value of one explicit scalar annotation element. */
public sealed interface AnnotationScalar {
  /**
   * Boolean element value.
   *
   * @param value
   *          attributed boolean value
   */
  record BooleanValue(boolean value) implements AnnotationScalar {
  }

  /**
   * Byte element value after annotation assignment conversion.
   *
   * @param value
   *          attributed byte value
   */
  record ByteValue(byte value) implements AnnotationScalar {
  }

  /**
   * Short element value after annotation assignment conversion.
   *
   * @param value
   *          attributed short value
   */
  record ShortValue(short value) implements AnnotationScalar {
  }

  /**
   * Character element value after annotation assignment conversion.
   *
   * @param value
   *          attributed character value
   */
  record CharValue(char value) implements AnnotationScalar {
  }

  /**
   * Integer element value.
   *
   * @param value
   *          attributed integer value
   */
  record IntValue(int value) implements AnnotationScalar {
  }

  /**
   * Long element value.
   *
   * @param value
   *          attributed long value
   */
  record LongValue(long value) implements AnnotationScalar {
  }

  /**
   * Float element value.
   *
   * @param value
   *          attributed float value
   */
  record FloatValue(float value) implements AnnotationScalar {
  }

  /**
   * Double element value.
   *
   * @param value
   *          attributed double value
   */
  record DoubleValue(double value) implements AnnotationScalar {
  }

  /**
   * String element value.
   *
   * @param value
   *          attributed string value
   */
  record StringValue(String value) implements AnnotationScalar {
    /** Requires a present string value. */
    public StringValue {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * Selected enum constant and its declaring enum type.
   *
   * @param declarationKey
   *          selected enum constant identity
   * @param enumTypeKey
   *          declaring enum type identity
   */
  record EnumConstant(String declarationKey, String enumTypeKey) implements AnnotationScalar {
    /** Requires both canonical declaration identities. */
    public EnumConstant {
      if (Objects.requireNonNull(declarationKey, "declarationKey").isBlank()
          || Objects.requireNonNull(enumTypeKey, "enumTypeKey").isBlank()) {
        throw new IllegalArgumentException("An enum value requires constant and enum declaration keys");
      }
    }
  }

  /**
   * Source class-literal operand as a recursive semantic type.
   *
   * @param operandType
   *          compiler-derived operand type
   */
  record ClassLiteral(JavaTypeStructure operandType) implements AnnotationScalar {
    /** Requires a structural compiler-derived operand type. */
    public ClassLiteral {
      Objects.requireNonNull(operandType, "operandType");
    }
  }
}
