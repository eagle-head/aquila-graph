package org.aquilagraph.application.port;

import org.aquilagraph.source.api.AnalysisEnvironmentException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class AnalysisEnvironmentExceptionTest {
  @Test
  void givenEnvironmentFailureMessage_whenExceptionCreated_thenMessageIsRetainedWithoutCause() {
    // Arrange
    var message = "compiler unavailable";

    // Act
    var exception = new AnalysisEnvironmentException(message);

    // Assert
    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void givenEnvironmentFailureCause_whenExceptionCreated_thenMessageAndCauseAreRetained() {
    // Arrange
    var message = "dependency changed";
    var cause = new IOException("read failed");

    // Act
    var exception = new AnalysisEnvironmentException(message, cause);

    // Assert
    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
