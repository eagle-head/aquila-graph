package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.aquilagraph.application.port.RepositoryException;
import org.junit.jupiter.api.Test;

class AnalysisRepositoryExceptionTest {
  @Test
  void givenCompletedRunAndRepositoryCause_whenExceptionCreated_thenRunCauseAndMessageAreRetained() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    var cause = new RepositoryException("repository unavailable", new IllegalStateException("connection refused"));

    // Act
    var exception = new AnalysisRepositoryException(run, cause);

    // Assert
    assertSame(run, exception.analysisRun());
    assertSame(cause, exception.getCause());
    assertEquals("Repository save failed for analysis run " + run.runId(), exception.getMessage());
  }

  @Test
  void givenSerializedRepositoryFailure_whenDeserializationAttempted_thenMissingRunIsRejected() throws IOException {
    // Arrange
    var original = new AnalysisRepositoryException(TestAnalysisRuns.completed(),
        new RepositoryException("repository unavailable", new IllegalStateException("connection refused")));
    final byte[] serialized;
    try (var buffer = new ByteArrayOutputStream(); var output = new ObjectOutputStream(buffer)) {
      output.writeObject(original);
      serialized = buffer.toByteArray();
    }

    // Act
    var exception = assertThrows(InvalidObjectException.class, () -> {
      try (var input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
        input.readObject();
      }
    });

    // Assert
    assertEquals("AnalysisRepositoryException cannot be deserialized without its analysis run", exception.getMessage());
  }
}
