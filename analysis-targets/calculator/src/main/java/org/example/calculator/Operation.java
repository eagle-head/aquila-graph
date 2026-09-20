package org.example.calculator;

import java.util.Objects;

public enum Operation {
  ADD,
  SUBTRACT,
  MULTIPLY,
  DIVIDE;

  public static Operation fromSymbol(String symbol) {
    return switch (Objects.requireNonNull(symbol, "symbol")) {
      case "+" -> ADD;
      case "-" -> SUBTRACT;
      case "*" -> MULTIPLY;
      case "/" -> DIVIDE;
      default ->
          throw new IllegalArgumentException(
              "Unsupported operator: " + symbol + ". Supported operators: +, -, *, /.");
    };
  }
}
