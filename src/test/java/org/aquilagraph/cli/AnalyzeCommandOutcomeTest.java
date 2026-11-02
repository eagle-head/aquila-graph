package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.port.RepositoryException;

class AnalyzeCommandOutcomeTest {
  @Test
  void givenValidOutcomeValues_whenOutcomesAreCreated_thenValuesAreRetained() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var execution = CliTestFixtures.withoutRepositoryRequest(run);
    var failure = new AnalysisRepositoryException(run, new RepositoryException("database unavailable", new IllegalStateException("offline")));

    // Act
    var executed = new AnalyzeCommandOutcome.Executed(execution);
    var repositoryFailed = new AnalyzeCommandOutcome.RepositoryFailed(failure);
    var inputRejected = new AnalyzeCommandOutcome.InputRejected("invalid input");
    var environmentFailed = new AnalyzeCommandOutcome.EnvironmentFailed("compiler unavailable");

    // Assert
    assertSame(execution, executed.execution());
    assertSame(run, repositoryFailed.failure().analysisRun());
    assertEquals("database unavailable", repositoryFailed.failure().repositoryFailure().description());
    assertEquals("invalid input", inputRejected.message());
    assertEquals("compiler unavailable", environmentFailed.message());
  }

  @Test
  void givenNullOutcomeValue_whenOutcomeIsCreated_thenInvalidStateIsRejected() {
    // Arrange
    var run = CliTestFixtures.completedRun();

    // Act
    var executedFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.Executed(null));
    var runFailure = assertThrows(NullPointerException.class,
        () -> new AnalyzeCommandOutcome.RepositoryFailed(null));
    var inputFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.InputRejected(null));
    var environmentFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.EnvironmentFailed(null));

    // Assert
    assertEquals("execution", executedFailure.getMessage());
    assertEquals("failure", runFailure.getMessage());
    assertEquals("message", inputFailure.getMessage());
    assertEquals("message", environmentFailure.getMessage());
  }
}
