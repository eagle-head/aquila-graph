package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.aquilagraph.application.AnalysisExecution;
import org.junit.jupiter.api.Test;

class AnalyzeCommandOutcomeTest {
  @Test
  void givenValidOutcomeValues_whenOutcomesAreCreated_thenValuesAreRetained() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var execution = AnalysisExecution.withoutRepositoryRequest(run);

    // Act
    var executed = new AnalyzeCommandOutcome.Executed(execution);
    var repositoryFailed = new AnalyzeCommandOutcome.RepositoryFailed(run, "database unavailable");
    var inputRejected = new AnalyzeCommandOutcome.InputRejected("invalid input");
    var environmentFailed = new AnalyzeCommandOutcome.EnvironmentFailed("compiler unavailable");

    // Assert
    assertSame(execution, executed.execution());
    assertSame(run, repositoryFailed.run());
    assertEquals("database unavailable", repositoryFailed.message());
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
        () -> new AnalyzeCommandOutcome.RepositoryFailed(null, "database unavailable"));
    var messageFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.RepositoryFailed(run, null));
    var inputFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.InputRejected(null));
    var environmentFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.EnvironmentFailed(null));

    // Assert
    assertEquals("execution", executedFailure.getMessage());
    assertEquals("run", runFailure.getMessage());
    assertEquals("message", messageFailure.getMessage());
    assertEquals("message", inputFailure.getMessage());
    assertEquals("message", environmentFailure.getMessage());
  }
}
