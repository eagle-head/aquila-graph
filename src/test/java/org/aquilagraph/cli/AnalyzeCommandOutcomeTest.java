package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.aquilagraph.application.port.RepositoryException;
import org.junit.jupiter.api.Test;

class AnalyzeCommandOutcomeTest {
  @Test
  void givenExecutionWithPublicationFailure_whenOutcomeIsCreated_thenExactExecutionIsRetained() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var primary = new RepositoryException("database unavailable", new IllegalStateException("offline"));
    var execution = CliTestFixtures.withFailedPublication(run, primary);

    // Act
    var executed = new AnalyzeCommandOutcome.Executed(execution);

    // Assert
    assertSame(execution, executed.execution());
    assertSame(run, executed.execution().run());
    assertSame(primary, executed.execution().publication().failure().orElseThrow().effectivePrimary());
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
    // Act
    var executedFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.Executed(null));
    var inputFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.InputRejected(null));
    var environmentFailure = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.EnvironmentFailed(null));

    // Assert
    assertEquals("execution", executedFailure.getMessage());
    assertEquals("message", inputFailure.getMessage());
    assertEquals("message", environmentFailure.getMessage());
  }
}
