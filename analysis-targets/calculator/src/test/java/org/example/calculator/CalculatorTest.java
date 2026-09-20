package org.example.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculatorTest {
  @ParameterizedTest
  @MethodSource("supportedCalculations")
  void givenSupportedOperation_whenCalculationIsRequested_thenExpectedResultIsReturned(
      int left, Operation operation, int right, int expectedResult) {
    // Arrange
    var calculator = new Calculator();

    // Act
    var result = calculator.calculate(left, operation, right);

    // Assert
    assertEquals(expectedResult, result);
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Integer.MIN_VALUE, -1_000_000, -2, -1, 1, 2, 1_000_000, Integer.MAX_VALUE
      })
  void givenZeroDividendAndNonZeroDivisor_whenDivisionIsRequested_thenZeroIsReturned(int divisor) {
    // Arrange
    var calculator = new Calculator();

    // Act
    var result = calculator.calculate(0, Operation.DIVIDE, divisor);

    // Assert
    assertEquals(0, result);
  }

  @ParameterizedTest
  @MethodSource("signedCalculations")
  void givenSignedOperands_whenCalculationIsRequested_thenExpectedResultIsReturned(
      int left, Operation operation, int right, int expectedResult) {
    // Arrange
    var calculator = new Calculator();

    // Act
    var result = calculator.calculate(left, operation, right);

    // Assert
    assertEquals(expectedResult, result);
  }

  @ParameterizedTest
  @MethodSource("boundaryCalculations")
  void givenIntegerBoundaryOperands_whenCalculationIsRequested_thenExpectedResultIsReturned(
      int left, Operation operation, int right, int expectedResult) {
    // Arrange
    var calculator = new Calculator();

    // Act
    var result = calculator.calculate(left, operation, right);

    // Assert
    assertEquals(expectedResult, result);
  }

  @ParameterizedTest
  @MethodSource("overflowCalculations")
  void givenOverflowingIntegerOperation_whenCalculationIsRequested_thenWrappedResultIsReturned(
      int left, Operation operation, int right, int expectedResult) {
    // Arrange
    var calculator = new Calculator();

    // Act
    var result = calculator.calculate(left, operation, right);

    // Assert
    assertEquals(expectedResult, result);
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE})
  void givenZeroDivisor_whenDivisionIsRequested_thenArithmeticExceptionIsThrown(int dividend) {
    // Arrange
    var calculator = new Calculator();

    // Act
    var exception =
        assertThrows(
            ArithmeticException.class,
            () -> calculator.calculate(dividend, Operation.DIVIDE, 0));

    // Assert
    assertEquals("/ by zero", exception.getMessage());
  }

  private static Stream<Arguments> supportedCalculations() {
    return Stream.of(
        Arguments.of(7, Operation.ADD, 5, 12),
        Arguments.of(7, Operation.SUBTRACT, 5, 2),
        Arguments.of(7, Operation.MULTIPLY, 5, 35),
        Arguments.of(8, Operation.DIVIDE, 2, 4));
  }

  private static Stream<Arguments> signedCalculations() {
    return Stream.of(
        Arguments.of(-7, Operation.ADD, 5, -2),
        Arguments.of(7, Operation.ADD, -5, 2),
        Arguments.of(-7, Operation.ADD, -5, -12),
        Arguments.of(-7, Operation.SUBTRACT, 5, -12),
        Arguments.of(7, Operation.SUBTRACT, -5, 12),
        Arguments.of(-7, Operation.SUBTRACT, -5, -2),
        Arguments.of(-7, Operation.MULTIPLY, 5, -35),
        Arguments.of(7, Operation.MULTIPLY, -5, -35),
        Arguments.of(-7, Operation.MULTIPLY, -5, 35),
        Arguments.of(7, Operation.DIVIDE, -2, -3),
        Arguments.of(-7, Operation.DIVIDE, 2, -3),
        Arguments.of(-7, Operation.DIVIDE, -2, 3),
        Arguments.of(1, Operation.DIVIDE, 2, 0),
        Arguments.of(-1, Operation.DIVIDE, 2, 0));
  }

  private static Stream<Arguments> boundaryCalculations() {
    return Stream.of(
        Arguments.of(Integer.MAX_VALUE, Operation.ADD, 0, Integer.MAX_VALUE),
        Arguments.of(Integer.MIN_VALUE, Operation.SUBTRACT, 0, Integer.MIN_VALUE),
        Arguments.of(Integer.MAX_VALUE, Operation.MULTIPLY, 1, Integer.MAX_VALUE),
        Arguments.of(Integer.MIN_VALUE, Operation.DIVIDE, 1, Integer.MIN_VALUE));
  }

  private static Stream<Arguments> overflowCalculations() {
    return Stream.of(
        Arguments.of(Integer.MAX_VALUE, Operation.ADD, 1, Integer.MIN_VALUE),
        Arguments.of(Integer.MIN_VALUE, Operation.SUBTRACT, 1, Integer.MAX_VALUE),
        Arguments.of(Integer.MAX_VALUE, Operation.MULTIPLY, 2, -2),
        Arguments.of(Integer.MIN_VALUE, Operation.DIVIDE, -1, Integer.MIN_VALUE));
  }
}
