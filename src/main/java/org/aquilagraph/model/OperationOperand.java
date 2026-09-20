package org.aquilagraph.model;

import java.util.Objects;

public record OperationOperand(int position, FlowValue value) {
  public OperationOperand {
    if (position < 0) {
      throw new IllegalArgumentException("Operand positions cannot be negative");
    }
    Objects.requireNonNull(value, "value");
  }
}
