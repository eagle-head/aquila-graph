package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.port.AnalysisEnvironmentException;
import org.aquilagraph.application.port.AnalysisInputException;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.AnalysisRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void givenRelativeRootWithParentSegment_whenCommandExecutes_thenOneNormalizedRequestIsDelivered() {
    // Arrange
    var invocations = new AtomicInteger();
    var capturedRequest = new AtomicReference<AnalysisRequest>();
    var execution = AnalysisExecution.withoutRepositoryRequest(CliTestFixtures.completedRun());
    var executor = new AnalyzeCommandExecutor(request -> {
      invocations.incrementAndGet();
      capturedRequest.set(request);
      return execution;
    });
    var arguments = new AnalyzeCommandArguments(Path.of("source", "..", "target-source"),
        CliTestFixtures.arguments().release(), CliTestFixtures.arguments().repositorySavePolicy());

    // Act
    var result = executor.execute(arguments);

    // Assert
    assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, result).execution());
    assertEquals(1, invocations.get());
    var request = capturedRequest.get();
    assertEquals(Path.of("target-source").toAbsolutePath().normalize(), request.sourceRoot());
    assertEquals(arguments.release(), request.compilerConfiguration().release());
    assertEquals(arguments.repositorySavePolicy(), request.repositorySavePolicy());
    assertEquals(List.of(), request.compilerConfiguration().classPath());
    assertEquals(List.of(), request.compilerConfiguration().modulePath());
    assertEquals(List.of(), request.compilerConfiguration().options());
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
