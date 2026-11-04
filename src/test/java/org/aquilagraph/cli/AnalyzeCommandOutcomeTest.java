package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.port.RepositoryException;
import org.junit.jupiter.api.Test;

class AnalyzeCommandOutcomeTest {
  @Test
  void givenExecutionAndRepositoryFailure_whenOutcomesAreCreated_thenExactPayloadsAreRetained() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var execution = CliTestFixtures.withoutRepositoryRequest(run);
    var failure = new AnalysisRepositoryException(run, new RepositoryException("database unavailable", new IllegalStateException("offline")));

    // Act
    var executed = new AnalyzeCommandOutcome.Executed(execution);
    var repositoryFailed = new AnalyzeCommandOutcome.RepositoryFailed(failure);

    // Assert
    assertSame(execution, executed.execution());
    var retainedFailure = repositoryFailed.failure();
    assertSame(failure, retainedFailure);
    assertSame(run, retainedFailure.analysisRun());
    assertEquals("database unavailable", retainedFailure.repositoryFailure().description());
  }

  @Test
  void givenFailureDescriptionsWithBoundaryWhitespace_whenOutcomesAreCreated_thenMessagesAreRetainedVerbatim() {
    // Arrange
    var inputDescription = "  invalid source path  ";
    var environmentDescription = "\tcompiler unavailable  ";

    // Act
    var inputRejected = new AnalyzeCommandOutcome.InputRejected(inputDescription);
    var environmentFailed = new AnalyzeCommandOutcome.EnvironmentFailed(environmentDescription);

    // Assert
    assertEquals(inputDescription, inputRejected.message());
    assertEquals(environmentDescription, environmentFailed.message());
  }

  @Test
  void givenEmptyFailureDescriptions_whenOutcomesAreCreated_thenMessagesRemainEmpty() {
    // Arrange
    var description = "";

    // Act
    var inputRejected = new AnalyzeCommandOutcome.InputRejected(description);
    var environmentFailed = new AnalyzeCommandOutcome.EnvironmentFailed(description);

    // Assert
    assertEquals(description, inputRejected.message());
    assertEquals(description, environmentFailed.message());
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
