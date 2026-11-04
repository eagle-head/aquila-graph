package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
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
  void givenRepositoryFailureWithSuppressedCleanup_whenCommandExecutes_thenExactFailureAndChainAreRetained() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var cause = new IllegalStateException("offline");
    var repositoryFailure = new RepositoryException("database unavailable", cause);
    var repositoryCleanup = new IllegalStateException("repository session close failed");
    repositoryFailure.addSuppressed(repositoryCleanup);
    var firstCleanup = new IllegalStateException("analysis cleanup failed");
    var secondCleanup = new IllegalStateException("analysis context close failed");
    var execution = CliTestFixtures.withFailedPublication(run, repositoryFailure, firstCleanup, secondCleanup);
    var executor = new AnalyzeCommandExecutor(request -> execution);

    // Act
    var outcome = executor.execute(CliTestFixtures.arguments());

    // Assert
    var retained = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, outcome).execution();
    assertSame(execution, retained);
    assertSame(run, retained.run());
    var failure = retained.publication().failure().orElseThrow();
    assertSame(repositoryFailure, failure.effectivePrimary());
    assertEquals("database unavailable", failure.effectivePrimary().description());
    assertSame(cause, failure.effectivePrimary().getCause());
    assertEquals(List.of(repositoryCleanup), List.of(failure.effectivePrimary().getSuppressed()));
    assertEquals(List.of(firstCleanup, secondCleanup), List.of(failure.carrier().getSuppressed()));
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
    assertEquals(Path.of("target-source").toAbsolutePath().normalize(), ((SourceInput.Root) request.computation().source().input()).path());
    assertEquals(arguments.release(), request.computation().source().configuration().release());
    assertEquals(arguments.publicationIntent(), request.publicationIntent());
    assertEquals(List.of(), request.computation().source().configuration().classPath());
    assertEquals(List.of(), request.computation().source().configuration().modulePath());
    assertEquals(List.of(), request.computation().source().configuration().options());
  }

  @Test
  void givenUnmatchedPrimaryWithSuppressedRepositoryFailure_whenCommandExecutes_thenPrimaryRemainsAbrupt() {
    // Arrange
    var primary = new IllegalStateException("unmatched primary");
    var cleanup = new RepositoryException("close failed", new IllegalStateException("close cause"));
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

  @Test
  void givenInputFailureWithNamedCauseAndSuppressedRepositoryFailure_whenCommandExecutes_thenInputDescriptionWins() {
    // Arrange
    var cause = new AnalysisEnvironmentException("compiler rejected the source");
    var primary = new AnalysisInputException("source cannot be analyzed", cause);
    var cleanup = new RepositoryException("repository close failed", new IllegalStateException("close cause"));
    primary.addSuppressed(cleanup);
    var executor = new AnalyzeCommandExecutor(request -> {
      throw primary;
    });

    // Act
    var outcome = executor.execute(CliTestFixtures.arguments());

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandOutcome.InputRejected.class, outcome);
    assertEquals("source cannot be analyzed", rejected.message());
  }

  @Test
  void givenUnmatchedPrimaryWithNamedCause_whenCommandExecutes_thenExactPrimaryAndChainPropagate() {
    // Arrange
    var cause = new AnalysisInputException("nested input rejection", new IllegalArgumentException("invalid source"));
    var primary = new IllegalStateException("unexpected analysis failure", cause);
    var cleanup = new IllegalStateException("analysis close failed");
    primary.addSuppressed(cleanup);
    var executor = new AnalyzeCommandExecutor(request -> {
      throw primary;
    });

    // Act
    var observed = assertThrows(IllegalStateException.class, () -> executor.execute(CliTestFixtures.arguments()));

    // Assert
    assertSame(primary, observed);
    assertSame(cause, observed.getCause());
    assertEquals(List.of(cleanup), List.of(observed.getSuppressed()));
  }

  @Test
  void givenFatalErrorWithNamedCauseAndSuppressedFailure_whenCommandExecutes_thenExactErrorAndChainPropagate() {
    // Arrange
    var cause = new AnalysisEnvironmentException("nested compiler failure");
    var primary = new LinkageError("compiler linkage failed", cause);
    var cleanup = new AnalysisInputException("source close failed", new IllegalArgumentException("invalid source"));
    primary.addSuppressed(cleanup);
    var executor = new AnalyzeCommandExecutor(request -> {
      throw primary;
    });

    // Act
    var observed = assertThrows(LinkageError.class, () -> executor.execute(CliTestFixtures.arguments()));

    // Assert
    assertSame(primary, observed);
    assertSame(cause, observed.getCause());
    assertEquals(List.of(cleanup), List.of(observed.getSuppressed()));
  }
}
