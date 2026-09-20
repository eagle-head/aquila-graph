package org.example.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OperationTest {
  @ParameterizedTest
  @MethodSource("supportedSymbols")
  void givenSupportedSymbol_whenOperationIsParsed_thenExpectedOperationIsReturned(
      String symbol, Operation expectedOperation) {
    // Arrange

    // Act
    var operation = Operation.fromSymbol(symbol);

    // Assert
    assertEquals(expectedOperation, operation);
  }

  @ParameterizedTest
  @ValueSource(strings = {"%", "", "x", "++"})
  void givenUnsupportedSymbol_whenOperationIsParsed_thenInputErrorIsThrown(String symbol) {
    // Arrange

    // Act
    var exception =
        assertThrows(IllegalArgumentException.class, () -> Operation.fromSymbol(symbol));

    // Assert
    assertEquals(
        "Unsupported operator: " + symbol + ". Supported operators: +, -, *, /.",
        exception.getMessage());
  }

  private static Stream<Arguments> supportedSymbols() {
    return Stream.of(
        Arguments.of("+", Operation.ADD),
        Arguments.of("-", Operation.SUBTRACT),
        Arguments.of("*", Operation.MULTIPLY),
        Arguments.of("/", Operation.DIVIDE));
  }
}
