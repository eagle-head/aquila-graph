package org.example.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculatorApplicationTest {
  @Test
  void givenValidExpression_whenCommandRuns_thenResultIsWrittenToStandardOutput() {
    // Arrange
    var standardOutput = new ByteArrayOutputStream();
    var standardError = new ByteArrayOutputStream();
    var arguments = new String[] {"8", "/", "2"};

    // Act
    var exitCode = run(arguments, standardOutput, standardError);

    // Assert
    assertEquals(CalculatorApplication.SUCCESS_EXIT_CODE, exitCode);
    assertEquals("4" + System.lineSeparator(), standardOutput.toString(StandardCharsets.UTF_8));
    assertEquals("", standardError.toString(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 4})
  void givenWrongArgumentCount_whenCommandRuns_thenUsageErrorIsWrittenToStandardError(
      int argumentCount) {
    // Arrange
    var standardOutput = new ByteArrayOutputStream();
    var standardError = new ByteArrayOutputStream();
    var arguments = Collections.nCopies(argumentCount, "1").toArray(String[]::new);

    // Act
    var exitCode = run(arguments, standardOutput, standardError);

    // Assert
    assertEquals(CalculatorApplication.USAGE_ERROR_EXIT_CODE, exitCode);
    assertEquals("", standardOutput.toString(StandardCharsets.UTF_8));
    assertEquals(
        "Usage: calculator <left> <+|-|*|/> <right>" + System.lineSeparator(),
        standardError.toString(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @MethodSource("invalidOperands")
  void givenInvalidOperand_whenCommandRuns_thenInputErrorIsWrittenToStandardError(
      String left, String right, String operandName) {
    // Arrange
    var standardOutput = new ByteArrayOutputStream();
    var standardError = new ByteArrayOutputStream();
    var arguments = new String[] {left, "+", right};

    // Act
    var exitCode = run(arguments, standardOutput, standardError);

    // Assert
    assertEquals(CalculatorApplication.USAGE_ERROR_EXIT_CODE, exitCode);
    assertEquals("", standardOutput.toString(StandardCharsets.UTF_8));
    assertEquals(
        "Error: "
            + operandName
            + " operand must be a signed 32-bit integer."
            + System.lineSeparator()
            + "Usage: calculator <left> <+|-|*|/> <right>"
            + System.lineSeparator(),
        standardError.toString(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @MethodSource("validBoundaryExpressions")
  void givenBoundaryExpression_whenCommandRuns_thenResultIsWrittenToStandardOutput(
      String left, String operator, String right, String expectedResult) {
    // Arrange
    var standardOutput = new ByteArrayOutputStream();
    var standardError = new ByteArrayOutputStream();
    var arguments = new String[] {left, operator, right};

    // Act
    var exitCode = run(arguments, standardOutput, standardError);

    // Assert
    assertEquals(CalculatorApplication.SUCCESS_EXIT_CODE, exitCode);
    assertEquals(
        expectedResult + System.lineSeparator(),
        standardOutput.toString(StandardCharsets.UTF_8));
    assertEquals("", standardError.toString(StandardCharsets.UTF_8));
  }

  @Test
  void givenUnsupportedOperator_whenCommandRuns_thenInputErrorIsWrittenToStandardError() {
    // Arrange
    var standardOutput = new ByteArrayOutputStream();
    var standardError = new ByteArrayOutputStream();
    var arguments = new String[] {"8", "%", "2"};

    // Act
    var exitCode = run(arguments, standardOutput, standardError);

    // Assert
    assertEquals(CalculatorApplication.USAGE_ERROR_EXIT_CODE, exitCode);
    assertEquals("", standardOutput.toString(StandardCharsets.UTF_8));
    assertEquals(
        "Error: Unsupported operator: %. Supported operators: +, -, *, /."
            + System.lineSeparator()
            + "Usage: calculator <left> <+|-|*|/> <right>"
            + System.lineSeparator(),
        standardError.toString(StandardCharsets.UTF_8));
  }

  @Test
  void givenZeroDivisor_whenCommandRuns_thenArithmeticErrorIsWrittenToStandardError() {
    // Arrange
    var standardOutput = new ByteArrayOutputStream();
    var standardError = new ByteArrayOutputStream();
    var arguments = new String[] {"8", "/", "0"};

    // Act
    var exitCode = run(arguments, standardOutput, standardError);

    // Assert
    assertEquals(CalculatorApplication.ARITHMETIC_ERROR_EXIT_CODE, exitCode);
    assertEquals("", standardOutput.toString(StandardCharsets.UTF_8));
    assertEquals(
        "Error: Division by zero is not allowed." + System.lineSeparator(),
        standardError.toString(StandardCharsets.UTF_8));
  }

  private static int run(
      String[] arguments, ByteArrayOutputStream standardOutput, ByteArrayOutputStream standardError) {
    try (var output = new PrintStream(standardOutput, true, StandardCharsets.UTF_8);
        var error = new PrintStream(standardError, true, StandardCharsets.UTF_8)) {
      return CalculatorApplication.run(arguments, output, error);
    }
  }

  private static Stream<Arguments> invalidOperands() {
    return Stream.of(
        Arguments.of("left", "2", "Left"),
        Arguments.of("2", "right", "Right"),
        Arguments.of("2147483648", "0", "Left"),
        Arguments.of("-2147483649", "0", "Left"),
        Arguments.of("0", "2147483648", "Right"),
        Arguments.of("0", "-2147483649", "Right"));
  }

  private static Stream<Arguments> validBoundaryExpressions() {
    return Stream.of(
        Arguments.of("2147483647", "+", "0", "2147483647"),
        Arguments.of("-2147483648", "+", "0", "-2147483648"),
        Arguments.of("0", "/", "-2147483648", "0"),
        Arguments.of("0", "/", "2147483647", "0"));
  }
}
