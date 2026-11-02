package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.junit.jupiter.api.Test;

class AnalyzeCommandPresenterTest {
  private final AnalyzeCommandPresenter presenter = new AnalyzeCommandPresenter();

  @Test
  void givenCompletedExecution_whenOutcomeIsPresented_thenProgramSummaryAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var outcome = new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run));
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(outcome, streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_COMPLETED, exitCode);
    assertEquals(expectedOutput(run, "COMPLETE", "NOT_REQUESTED"), streams.outputLines());
    assertEquals(expectedWarning(1), streams.errorLines());
  }

  @Test
  void givenIncompleteExecution_whenOutcomeIsPresented_thenIncompleteExitAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.incompleteRun();
    var execution = CliTestFixtures.notEligibleForRepository(run, PublicationIntent.SAVE_COMPLETED);
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.Executed(execution), streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
    assertEquals(expectedOutput(run, "FAILED", "NOT_ELIGIBLE"), streams.outputLines());
    assertEquals(1, streams.errorLines().stream().filter(line -> line.equals("Diagnostic: \"" + CliTestFixtures.errorDiagnostic().diagnosticKey() + "\"")).count());
    assertEquals(true, streams.errorLines().contains("Qualification: APPLICATION APPLICATION_DIAGNOSTIC"));
  }

  @Test
  void givenRejectedExecution_whenOutcomeIsPresented_thenRejectedExitAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.rejectedRun();
    var execution = CliTestFixtures.withoutRepositoryRequest(run);
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.Executed(execution), streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals(List.of("Run: " + run.identity().key(), "Software version: unavailable", "Completion: REJECTED",
        "Java release: 25", "Java source files: 0", "AST: UNAVAILABLE", "AST profile: java-source-bounded/1",
        "CFG: UNAVAILABLE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/2\"",
        "CFG selection: ALL_BODIES", "CFG binding: UNBOUND", "Repository: NOT_REQUESTED"), streams.outputLines());
    assertEquals(1, streams.errorLines().stream().filter(line -> line.equals("Diagnostic: \"" + CliTestFixtures.errorDiagnostic().diagnosticKey() + "\"")).count());
    assertEquals(true, streams.errorLines().contains("Qualification: APPLICATION APPLICATION_DIAGNOSTIC"));
  }

  @Test
  void givenRepositoryFailure_whenOutcomeIsPresented_thenAnalysisAndRepositoryFailureRemainSeparate() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var streams = CapturedStreams.create();

    // Act
    var exitCode = presenter.present(new AnalyzeCommandOutcome.RepositoryFailed(new AnalysisRepositoryException(run, new RepositoryException("database unavailable", new IllegalStateException("offline")))), streams.output(),
        streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REPOSITORY_FAILED, exitCode);
    assertEquals("Completion: COMPLETE", streams.outputLines().get(2));
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

  @Test
  void givenAllCompletionAndIntentRows_whenExecutionIsPresented_thenExitAndRepositoryTextFollowEachRetainedRow() {
    // Arrange
    var rejected = CliTestFixtures.rejectedRun();
    var incomplete = CliTestFixtures.incompleteRun();
    var completed = CliTestFixtures.completedRun();
    var rows = List.of(
        new PresentationRow(CliTestFixtures.withoutRepositoryRequest(rejected), 2, "NOT_REQUESTED"),
        new PresentationRow(CliTestFixtures.notEligibleForRepository(rejected, PublicationIntent.SAVE_COMPLETED), 2, "NOT_ELIGIBLE"),
        new PresentationRow(CliTestFixtures.withoutRepositoryRequest(incomplete), 1, "NOT_REQUESTED"),
        new PresentationRow(CliTestFixtures.notEligibleForRepository(incomplete, PublicationIntent.SAVE_COMPLETED), 1, "NOT_ELIGIBLE"),
        new PresentationRow(CliTestFixtures.withoutRepositoryRequest(completed), 0, "NOT_REQUESTED"),
        new PresentationRow(CliTestFixtures.saved(completed), 0, "SAVED"));

    for (var row : rows) {
      var streams = CapturedStreams.create();
      // Act
      var code = presenter.present(new AnalyzeCommandOutcome.Executed(row.execution()), streams.output(), streams.error());

      // Assert
      assertEquals(row.exitCode(), code);
      assertEquals("Repository: " + row.repositoryText(), streams.outputLines().getLast());
      assertEquals("Completion: " + row.execution().run().completion(), streams.outputLines().get(2));
    }
  }

  @Test
  void givenEqualDiagnosticValuesAtTwoSlots_whenExecutionIsPresented_thenBothSlotsRemainInOriginalOrder() {
    // Arrange
    var original = CliTestFixtures.completedRun();
    var warning = CliTestFixtures.warningDiagnostic();
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(warning, warning));
    var streams = CapturedStreams.create();

    // Act
    var code = presenter.present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)),
        streams.output(), streams.error());

    // Assert
    assertEquals(0, code);
    var expected = new ArrayList<>(expectedWarning(1));
    expected.addAll(expectedWarning(2));
    assertEquals(expected, streams.errorLines());
  }

  @Test
  void givenBorrowedStreams_whenCompletedExecutionIsPresented_thenBothChannelsRemainWritable() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var streams = CapturedStreams.create();
    // Act
    var code = presenter.present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)), streams.output(), streams.error());
    streams.output().println("output remains open");
    streams.error().println("error remains open");
    // Assert
    assertEquals(0, code);
    assertEquals("output remains open", streams.outputLines().getLast());
    assertEquals("error remains open", streams.errorLines().getLast());
  }

  private static List<String> expectedOutput(AnalysisRun run, String completion, String repository) {
    var ast = run.representation().ast().result().orElseThrow();
    var key = ast.program().methods().getFirst().methodKey();
    return List.of("Run: " + run.identity().key(), "Software version: " + SoftwareVersion.from(ast.snapshot(), ast.environment()).signature(),
        "Completion: " + completion, "Java release: 25", "Java source files: 1", "AST: COMPLETE", "AST profile: java-source-bounded/1",
        "CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/2\"", "CFG selection: ALL_BODIES",
        "CFG binding: " + ast.identity().astResultKey(), "Selected method: \"" + key + "\"", "Java types: 1", "Java methods: 1",
        "Analyzed method bodies: 1", "CFG operations: 0", "CFG nodes: 2", "CFG edges: 1", "Method CFG: \"" + key + "\" AVAILABLE",
        "Repository: " + repository);
  }

  private static List<String> expectedWarning(int occurrence) {
    return List.of("Diagnostic: \"" + CliTestFixtures.warningDiagnostic().diagnosticKey() + "\"", "  Occurrence: " + occurrence,
        "  Severity: WARNING", "  Stage: FRONTEND", "  Cause: OTHER_ANALYSIS_ERROR", "  Code: \"cli.warning\"",
        "  Message: \"analysis warning\"", "  Origins: unavailable");
  }

  private record PresentationRow(AnalysisExecution execution, int exitCode, String repositoryText) {
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
