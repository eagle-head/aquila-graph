package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Decimal magnitude legal only as the immediate operand of unary minus.
 *
 * @param category
 *          integral type of the complete negative expression
 * @param magnitude
 *          positive spelling magnitude, without a runtime positive value
 */
public record DecimalMinimumOperand(JavaTypeCategory category, String magnitude) {
  /** Requires an exact context-restricted int or long boundary magnitude. */
  public DecimalMinimumOperand {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(magnitude, "magnitude");
    if (!(category == JavaTypeCategory.INT && "2147483648".equals(magnitude))
        && !(category == JavaTypeCategory.LONG && "9223372036854775808".equals(magnitude))) {
      throw new IllegalArgumentException("A restricted decimal operand requires its exact integral boundary magnitude");
    }
  }
}
