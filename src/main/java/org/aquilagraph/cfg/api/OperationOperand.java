package org.aquilagraph.cfg.api;

import java.util.Objects;

/**
 * Ordered input value consumed by an operation node.
 *
 * @param position
 *          zero-based operand position
 * @param value
 *          consumed graph value
 */
public record OperationOperand(int position, FlowValue value) {
  /** Validates operand position and value. */
  public OperationOperand {
    if (position < 0) {
      throw new IllegalArgumentException("Operand positions cannot be negative");
    }
    Objects.requireNonNull(value, "value");
  }
}
