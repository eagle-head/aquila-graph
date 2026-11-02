package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;

class AnalyzeCommandExecutorTest {
  @Test
  void givenSuccessfulUseCase_whenCommandExecutes_thenExecutionIsReturned() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRun());
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
    assertSame(run, repositoryFailed.failure().analysisRun());
    assertSame(failure, repositoryFailed.failure().repositoryFailure());
    assertEquals("database unavailable", repositoryFailed.failure().repositoryFailure().description());
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

  @Test
  void givenRelativeRootWithParentSegment_whenCommandExecutes_thenOneNormalizedRequestIsDelivered() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRun());
    var useCase = new CapturingAnalysisUseCase(request -> execution);
    var executor = new AnalyzeCommandExecutor(useCase);
    var arguments = new AnalyzeCommandArguments(Path.of("source", "..", "target-source"),
        CliTestFixtures.arguments().release(), CliTestFixtures.arguments().publicationIntent());

    // Act
    var result = executor.execute(arguments);

    // Assert
    assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, result).execution());
    assertEquals(1, useCase.requests().size());
    var request = useCase.requests().getFirst();
    assertEquals(Path.of("target-source").toAbsolutePath().normalize(), ((SourceInput.Root) request.input()).path());
    assertEquals(arguments.release(), request.configuration().release());
    assertEquals(arguments.publicationIntent(), request.publicationIntent());
    assertEquals(List.of(), request.configuration().classPath());
    assertEquals(List.of(), request.configuration().modulePath());
    assertEquals(List.of(), request.configuration().options());
  }

  @Test
  void givenUnmatchedPrimaryWithSuppressedRepositoryFailure_whenCommandExecutes_thenPrimaryRemainsAbrupt() {
    // Arrange
    var primary = new IllegalStateException("unmatched primary");
    var cleanup = new AnalysisRepositoryException(CliTestFixtures.completedRun(),
        new RepositoryException("close failed", new IllegalStateException("close cause")));
    primary.addSuppressed(cleanup);
    var executor = new AnalyzeCommandExecutor(request -> {
      throw primary;
    });

    // Act
    var observed = assertThrows(IllegalStateException.class, () -> executor.execute(CliTestFixtures.arguments()));

    // Assert
    assertSame(primary, observed);
    assertEquals(1, observed.getSuppressed().length);
    assertSame(cleanup, observed.getSuppressed()[0]);
  }
}
