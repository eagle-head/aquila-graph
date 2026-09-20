package org.aquilagraph.application.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class RepositoryExceptionTest {
  @Test
  void givenRepositoryImplementationFailure_whenExceptionCreated_thenMessageAndCauseAreRetained() {
    // Arrange
    var message = "save failed";
    var cause = new IllegalStateException("transaction rolled back");

    // Act
    var exception = new RepositoryException(message, cause);

    // Assert
    assertEquals(message, exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
