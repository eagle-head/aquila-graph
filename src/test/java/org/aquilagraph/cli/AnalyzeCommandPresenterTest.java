package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
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
    assertEquals(List.of("Run: " + run.identity().key(), "Software version: " + SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature(), "Completion: COMPLETED",
        "Java release: 25", "Java source files: 1", "Java types: 1", "Java methods: 1", "Analyzed method bodies: 1",
        "CFG operations: 0", "CFG nodes: 2", "CFG edges: 1", "Repository: NOT_REQUESTED"), streams.outputLines());
    assertEquals(List.of("WARNING FRONTEND cli.warning: analysis warning"), streams.errorLines());
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
    assertEquals(List.of("Run: " + run.identity().key(), "Software version: " + SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature(), "Completion: INCOMPLETE",
        "Java release: 25", "Java source files: 1", "Java types: 1", "Java methods: 1", "Analyzed method bodies: 1",
        "CFG operations: 0", "CFG nodes: 2", "CFG edges: 1", "Repository: NOT_ELIGIBLE"), streams.outputLines());
    assertEquals(List.of("ERROR FRONTEND cli.error: source failed"), streams.errorLines());
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
    assertEquals(List.of("Run: " + run.identity().key(), "Software version: " + "unavailable", "Completion: REJECTED",
        "Java release: 25", "Java source files: 0", "Repository: NOT_REQUESTED"), streams.outputLines());
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
      assertEquals("Completion: " + switch (row.execution().run().completion()) {
        case COMPLETE -> "COMPLETED";
        case PARTIAL, FAILED -> "INCOMPLETE";
        case REJECTED -> "REJECTED";
      }, streams.outputLines().get(2));
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
    assertEquals(List.of("WARNING FRONTEND cli.warning: analysis warning", "WARNING FRONTEND cli.warning: analysis warning"),
        streams.errorLines());
  }

  @Test
  void givenBorrowedStreams_whenCompletedExecutionIsPresented_thenCallsFollowSummaryDiagnosticsStatusWithoutClosingOrFlushing() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var calls = new ArrayList<String>();
    var output = new RecordingPrintStream("output", calls);
    var error = new RecordingPrintStream("error", calls);

    // Act
    var code = presenter.present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)), output, error);

    // Assert
    assertEquals(0, code);
    assertEquals(List.of("output: Run: " + run.identity().key(), "output: Software version: " + SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature(),
        "output: Completion: COMPLETED", "output: Java release: 25", "output: Java source files: 1", "output: Java types: 1",
        "output: Java methods: 1", "output: Analyzed method bodies: 1", "output: CFG operations: 0", "output: CFG nodes: 2",
        "output: CFG edges: 1", "error: WARNING FRONTEND cli.warning: analysis warning", "output: Repository: NOT_REQUESTED"), calls);
  }

  private record PresentationRow(AnalysisExecution execution, int exitCode, String repositoryText) {
  }

  private static final class RecordingPrintStream extends PrintStream {
    private final String name;
    private final List<String> calls;

    RecordingPrintStream(String name, List<String> calls) {
      super(OutputStream.nullOutputStream());
      this.name = name;
      this.calls = calls;
    }

    @Override
    public PrintStream printf(String format, Object... arguments) {
      calls.add(name + ": " + String.format(format, arguments).stripTrailing());
      return this;
    }

    @Override
    public void println(String value) {
      calls.add(name + ": " + value);
    }

    @Override
    public void close() {
      calls.add(name + ": close");
    }

    @Override
    public void flush() {
      calls.add(name + ": flush");
    }
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
