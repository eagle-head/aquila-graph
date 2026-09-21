package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.RepositorySavePolicy;
import org.junit.jupiter.api.Test;

class AnalyzeCommandPresenterTest {
  private final AnalyzeCommandPresenter presenter = new AnalyzeCommandPresenter();

  @Test
  void givenCompletedExecution_whenOutcomeIsPresented_thenProgramSummaryAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var outcome = new AnalyzeCommandOutcome.Executed(AnalysisExecution.withoutRepositoryRequest(run));
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(outcome, streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_COMPLETED, exitCode);
    assertEquals(List.of("Run: " + run.runId(), "Software version: " + run.softwareVersion().signature(), "Completion: COMPLETED",
        "Java release: 25", "Java source files: 1", "Java types: 1", "Java methods: 1", "Analyzed method bodies: 1",
        "CFG operations: 0", "CFG nodes: 2", "CFG edges: 1", "Repository: NOT_REQUESTED"), streams.outputLines());
    assertEquals(List.of("WARNING FRONTEND cli.warning: analysis warning"), streams.errorLines());
  }

  @Test
  void givenIncompleteExecution_whenOutcomeIsPresented_thenIncompleteExitAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.incompleteRun();
    var execution = AnalysisExecution.notEligibleForRepository(run, RepositorySavePolicy.SAVE_COMPLETED);
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.Executed(execution), streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
    assertEquals(List.of("Run: " + run.runId(), "Software version: " + run.softwareVersion().signature(), "Completion: INCOMPLETE",
        "Java release: 25", "Java source files: 1", "Repository: NOT_ELIGIBLE"), streams.outputLines());
    assertEquals(List.of("ERROR FRONTEND cli.error: source failed"), streams.errorLines());
  }

  @Test
  void givenRejectedExecution_whenOutcomeIsPresented_thenRejectedExitAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.rejectedRun();
    var execution = AnalysisExecution.notEligibleForRepository(run, RepositorySavePolicy.DO_NOT_SAVE);
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.Executed(execution), streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals(List.of("Run: " + run.runId(), "Software version: " + run.softwareVersion().signature(), "Completion: REJECTED",
        "Java release: 25", "Java source files: 0", "Repository: NOT_ELIGIBLE"), streams.outputLines());
    assertEquals(List.of("ERROR FRONTEND cli.error: source failed"), streams.errorLines());
  }

  @Test
  void givenRepositoryFailure_whenOutcomeIsPresented_thenAnalysisAndRepositoryFailureRemainSeparate() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.RepositoryFailed(run, "database unavailable"), streams.output(),
        streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REPOSITORY_FAILED, exitCode);
    assertEquals("Completion: COMPLETED", streams.outputLines().get(2));
    assertEquals(List.of("Repository save failed: database unavailable"), streams.errorLines());
  }

  @Test
  void givenInputRejection_whenOutcomeIsPresented_thenInputFailureIsRendered() {
    // Arrange
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.InputRejected("source unreadable"), streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(List.of("Input rejected: source unreadable"), streams.errorLines());
  }

  @Test
  void givenEnvironmentFailure_whenOutcomeIsPresented_thenAnalysisFailureIsRendered() {
    // Arrange
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.EnvironmentFailed("compiler unavailable"), streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(List.of("Analysis failed: compiler unavailable"), streams.errorLines());
  }

  @Test
  void givenParserRejection_whenOutcomeIsPresented_thenMessageAndUsageAreRendered() {
    // Arrange
    var streams = CapturedStreams.create();
    var rejection = new AnalyzeCommandParser.Rejected("Exactly one source root is required");

    // Act
    var exitCode = presenter.present(rejection, streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals(List.of("Exactly one source root is required", AnalyzeCommandParser.USAGE), streams.errorLines());
  }

  private record CapturedStreams(ByteArrayOutputStream outputBytes, ByteArrayOutputStream errorBytes, PrintStream output,
      PrintStream error) {
    static CapturedStreams create() {
      var outputBytes = new ByteArrayOutputStream();
      var errorBytes = new ByteArrayOutputStream();
      return new CapturedStreams(outputBytes, errorBytes, new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
          new PrintStream(errorBytes, true, StandardCharsets.UTF_8));
    }

    List<String> outputLines() {
      return outputBytes.toString(StandardCharsets.UTF_8).lines().toList();
    }

    List<String> errorLines() {
      return errorBytes.toString(StandardCharsets.UTF_8).lines().toList();
    }
  }
}
