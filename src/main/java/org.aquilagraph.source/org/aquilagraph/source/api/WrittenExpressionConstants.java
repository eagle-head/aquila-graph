package org.aquilagraph.source.api;

import java.util.Optional;

/** Source-neutral constant derivation for recovered unary and additive occurrences. */
public final class WrittenExpressionConstants {
  private WrittenExpressionConstants() {
  }

  /**
   * Derives one unary constant using its independently established promoted type.
   *
   * @param operator
   *          written unary operator
   * @param operand
   *          justified operand value
   * @param category
   *          promoted result category
   * @return derived scalar, absent for an inapplicable operator/type pair
   */
  public static Optional<AnnotationScalar> unary(SyntaxOperator operator, AnnotationScalar operand, JavaTypeCategory category) {
    if (operator == SyntaxOperator.LOGICAL_COMPLEMENT) {
      return operand instanceof AnnotationScalar.BooleanValue value && category == JavaTypeCategory.BOOLEAN
          ? Optional.of(new AnnotationScalar.BooleanValue(!value.value()))
          : Optional.empty();
    }
    return number(operand).flatMap(value -> switch (category) {
      case DOUBLE -> operator == SyntaxOperator.UNARY_PLUS
          ? Optional.of(new AnnotationScalar.DoubleValue(value.doubleValue()))
          : operator == SyntaxOperator.UNARY_MINUS ? Optional.of(new AnnotationScalar.DoubleValue(-value.doubleValue())) : Optional.empty();
      case FLOAT -> operator == SyntaxOperator.UNARY_PLUS
          ? Optional.of(new AnnotationScalar.FloatValue(value.floatValue()))
          : operator == SyntaxOperator.UNARY_MINUS ? Optional.of(new AnnotationScalar.FloatValue(-value.floatValue())) : Optional.empty();
      case LONG -> switch (operator) {
        case UNARY_PLUS -> Optional.of(new AnnotationScalar.LongValue(value.longValue()));
        case UNARY_MINUS -> Optional.of(new AnnotationScalar.LongValue(-value.longValue()));
        case BITWISE_COMPLEMENT -> Optional.of(new AnnotationScalar.LongValue(~value.longValue()));
        default -> Optional.empty();
      };
      case INT -> switch (operator) {
        case UNARY_PLUS -> Optional.of(new AnnotationScalar.IntValue(value.intValue()));
        case UNARY_MINUS -> Optional.of(new AnnotationScalar.IntValue(-value.intValue()));
        case BITWISE_COMPLEMENT -> Optional.of(new AnnotationScalar.IntValue(~value.intValue()));
        default -> Optional.empty();
      };
      default -> Optional.empty();
    });
  }

  /**
   * Derives a left-associated additive constant at its established result type.
   *
   * @param operator
   *          PLUS or MINUS
   * @param left
   *          justified left operand
   * @param right
   *          justified right operand
   * @param category
   *          promoted numeric or String result category
   * @return derived scalar, absent when the operation has no valid scalar interpretation
   */
  public static Optional<AnnotationScalar> additive(SyntaxOperator operator, AnnotationScalar left, AnnotationScalar right,
      JavaTypeCategory category) {
    if (operator != SyntaxOperator.PLUS && operator != SyntaxOperator.MINUS) {
      return Optional.empty();
    }
    if (category == JavaTypeCategory.DECLARED && operator == SyntaxOperator.PLUS
        && (left instanceof AnnotationScalar.StringValue || right instanceof AnnotationScalar.StringValue)) {
      return text(left).flatMap(first -> text(right).map(second -> new AnnotationScalar.StringValue(first + second)));
    }
    var plus = operator == SyntaxOperator.PLUS;
    return number(left).flatMap(first -> number(right).flatMap(second -> switch (category) {
      case DOUBLE -> Optional.of(
          new AnnotationScalar.DoubleValue(plus ? first.doubleValue() + second.doubleValue() : first.doubleValue() - second.doubleValue()));
      case FLOAT -> Optional
          .of(new AnnotationScalar.FloatValue(plus ? first.floatValue() + second.floatValue() : first.floatValue() - second.floatValue()));
      case LONG ->
        Optional.of(new AnnotationScalar.LongValue(plus ? first.longValue() + second.longValue() : first.longValue() - second.longValue()));
      case INT ->
        Optional.of(new AnnotationScalar.IntValue(plus ? first.intValue() + second.intValue() : first.intValue() - second.intValue()));
      default -> Optional.empty();
    }));
  }

  /**
   * Interprets a restricted magnitude in its required complete unary-minus context.
   *
   * @param operand
   *          independently captured restricted magnitude
   * @return the complete expression's negative runtime value
   */
  public static AnnotationScalar minimum(DecimalMinimumOperand operand) {
    return operand.category() == JavaTypeCategory.LONG
        ? new AnnotationScalar.LongValue(Long.MIN_VALUE)
        : new AnnotationScalar.IntValue(Integer.MIN_VALUE);
  }

  private static Optional<Number> number(AnnotationScalar scalar) {
    return switch (scalar) {
      case AnnotationScalar.ByteValue value -> Optional.of(value.value());
      case AnnotationScalar.ShortValue value -> Optional.of(value.value());
      case AnnotationScalar.CharValue value -> Optional.of((int) value.value());
      case AnnotationScalar.IntValue value -> Optional.of(value.value());
      case AnnotationScalar.LongValue value -> Optional.of(value.value());
      case AnnotationScalar.FloatValue value -> Optional.of(value.value());
      case AnnotationScalar.DoubleValue value -> Optional.of(value.value());
      default -> Optional.empty();
    };
  }

  /**
   * Returns the Java runtime scalar spelling used by executable literal values.
   *
   * @param scalar
   *          copied primitive or String constant
   * @return canonical scalar spelling, absent for nonscalar annotation values
   */
  public static Optional<String> text(AnnotationScalar scalar) {
    return switch (scalar) {
      case AnnotationScalar.BooleanValue value -> Optional.of(Boolean.toString(value.value()));
      case AnnotationScalar.CharValue value -> Optional.of(Character.toString(value.value()));
      case AnnotationScalar.StringValue value -> Optional.of(value.value());
      default -> number(scalar).map(Object::toString);
    };
  }
}
