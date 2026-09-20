package org.example.calculator;

import java.util.Objects;

public final class Calculator {
  public int calculate(int left, Operation operation, int right) {
    return switch (Objects.requireNonNull(operation, "operation")) {
      case ADD -> add(left, right);
      case SUBTRACT -> subtract(left, right);
      case MULTIPLY -> multiply(left, right);
      case DIVIDE -> divide(left, right);
    };
  }

  private int add(int left, int right) {
    return left + right;
  }

  private int subtract(int left, int right) {
    return left - right;
  }

  private int multiply(int left, int right) {
    return left * right;
  }

  private int divide(int left, int right) {
    return left / right;
  }
}
