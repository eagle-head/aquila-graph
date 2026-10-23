package org.aquilagraph.cfg.internal;

import java.util.Objects;

/**
 * Ordered input value consumed by an operation node.
 *
 * @param position
 *          zero-based operand position
 * @param value
 *          consumed graph value
 */
record DraftOperand(int position, DraftValue value) {
  /** Validates operand position and value. */
  public DraftOperand {
    if (position < 0) {
      throw new IllegalArgumentException("Operand positions cannot be negative");
    }
    Objects.requireNonNull(value, "value");
  }
}
