package org.example.calculator;

import java.io.PrintStream;
import java.util.Objects;

public final class CalculatorApplication {
  static final int SUCCESS_EXIT_CODE = 0;
  static final int ARITHMETIC_ERROR_EXIT_CODE = 1;
  static final int USAGE_ERROR_EXIT_CODE = 2;

  private static final String USAGE = "Usage: calculator <left> <+|-|*|/> <right>";

  private CalculatorApplication() {}

  public static void main(String[] arguments) {
    var exitCode = run(arguments, System.out, System.err);
    if (exitCode != SUCCESS_EXIT_CODE) {
      System.exit(exitCode);
    }
  }

  public static int run(String[] arguments, PrintStream output, PrintStream error) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");

    if (arguments.length != 3) {
      error.println(USAGE);
      return USAGE_ERROR_EXIT_CODE;
    }

    try {
      var left = parseOperand("Left", arguments[0]);
      var operation = Operation.fromSymbol(arguments[1]);
      var right = parseOperand("Right", arguments[2]);
      var result = new Calculator().calculate(left, operation, right);
      output.println(result);
      return SUCCESS_EXIT_CODE;
    } catch (IllegalArgumentException exception) {
      error.println("Error: " + exception.getMessage());
      error.println(USAGE);
      return USAGE_ERROR_EXIT_CODE;
    } catch (ArithmeticException exception) {
      error.println("Error: Division by zero is not allowed.");
      return ARITHMETIC_ERROR_EXIT_CODE;
    }
  }

  private static int parseOperand(String name, String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          name + " operand must be a signed 32-bit integer.", exception);
    }
  }
}
