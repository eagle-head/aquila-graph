package org.aquilagraph.application.port;

import org.aquilagraph.source.api.AnalysisInputException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class AnalysisInputExceptionTest {
  @Test
  void givenRejectedInputCause_whenExceptionCreated_thenMessageAndCauseAreRetained() {
    // Arrange
    var message = "source cannot be read";
    var cause = new IOException("access denied");

    // Act
    var exception = new AnalysisInputException(message, cause);

    // Assert
    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
