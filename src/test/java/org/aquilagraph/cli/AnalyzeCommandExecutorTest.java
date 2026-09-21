package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.port.AnalysisEnvironmentException;
import org.aquilagraph.application.port.AnalysisInputException;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.RepositoryException;
import org.junit.jupiter.api.Test;

class AnalyzeCommandExecutorTest {
  @Test
  void givenSuccessfulUseCase_whenCommandExecutes_thenExecutionIsReturned() {
    // Arrange
    var execution = AnalysisExecution.withoutRepositoryRequest(CliTestFixtures.completedRun());
    var executor = new AnalyzeCommandExecutor(request -> execution);

    // Act
    var outcome = executor.execute(CliTestFixtures.arguments());

    // Assert
    var executed = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, outcome);
    assertSame(execution, executed.execution());
  }

  @Test
  void givenRepositoryFailure_whenCommandExecutes_thenRunAndFailureArePreserved() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var failure = new RepositoryException("database unavailable", new IllegalStateException("offline"));
    AnalysisUseCase useCase = request -> {
      throw new AnalysisRepositoryException(run, failure);
    };
    var executor = new AnalyzeCommandExecutor(useCase);

    // Act
    var outcome = executor.execute(CliTestFixtures.arguments());

    // Assert
    var repositoryFailed = assertInstanceOf(AnalyzeCommandOutcome.RepositoryFailed.class, outcome);
    assertSame(run, repositoryFailed.run());
    assertEquals("database unavailable", repositoryFailed.message());
  }

  @Test
  void givenInputFailure_whenCommandExecutes_thenStableDescriptionIsReturned() {
    // Arrange
    AnalysisUseCase useCase = request -> {
      throw new AnalysisInputException("source unreadable", new IllegalArgumentException("invalid input"));
    };
    var executor = new AnalyzeCommandExecutor(useCase);

    // Act
    var outcome = executor.execute(CliTestFixtures.arguments());

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandOutcome.InputRejected.class, outcome);
    assertEquals("source unreadable", rejected.message());
  }

  @Test
  void givenEnvironmentFailure_whenCommandExecutes_thenStableDescriptionIsReturned() {
    // Arrange
    AnalysisUseCase useCase = request -> {
      throw new AnalysisEnvironmentException("compiler unavailable");
    };
    var executor = new AnalyzeCommandExecutor(useCase);

    // Act
    var outcome = executor.execute(CliTestFixtures.arguments());

    // Assert
    var failed = assertInstanceOf(AnalyzeCommandOutcome.EnvironmentFailed.class, outcome);
    assertEquals("compiler unavailable", failed.message());
  }
}
