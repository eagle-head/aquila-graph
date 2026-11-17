package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Temporarily changes the JVM format locale")
class CommandReportPresentationTest {


  @Test
  void givenCompletedExecution_whenOutcomeIsPresented_thenProgramSummaryAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var outcome = new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run));
    var streams = new CliStreams();

    // Act
    var exitCode = CliTestFixtures.present(outcome, streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, exitCode);
    assertEquals(expectedOutput(run, "COMPLETE", "NOT_REQUESTED"), streams.outputLines());
    assertEquals(expectedWarning(1), streams.errorLines());
  }

  @Test
  void givenIncompleteExecution_whenOutcomeIsPresented_thenIncompleteExitAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.incompleteRun();
    var execution = CliTestFixtures.notEligibleForRepository(run, PublicationIntent.SAVE_COMPLETED);
    var streams = new CliStreams();

    // Act
    var exitCode = CliTestFixtures.present(new AnalyzeCommandOutcome.Executed(execution), streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, exitCode);
    assertEquals(expectedOutput(run, "FAILED", "NOT_ELIGIBLE"), streams.outputLines());
    assertEquals(1, streams.errorLines().stream().filter(line -> line.equals("Diagnostic: \"" + CliTestFixtures.errorDiagnostic().diagnosticKey() + "\"")).count());
    assertEquals(true, streams.errorLines().contains("Qualification: APPLICATION APPLICATION_DIAGNOSTIC"));
  }

  @Test
  void givenRejectedExecution_whenOutcomeIsPresented_thenRejectedExitAndDiagnosticAreRendered() {
    // Arrange
    var run = CliTestFixtures.rejectedRun();
    var execution = CliTestFixtures.withoutRepositoryRequest(run);
    var streams = new CliStreams();

    // Act
    var exitCode = CliTestFixtures.present(new AnalyzeCommandOutcome.Executed(execution), streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REJECTED, exitCode);
    assertEquals(List.of("Run: \"" + run.identity().key() + "\"", "Software version: unavailable", "Completion: REJECTED",
        "Java release: 25", "Java source files: 0", "AST: UNAVAILABLE", "AST profile: \"java-source-bounded/1\"",
        "CFG: UNAVAILABLE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/3\"",
        "CFG selection: ALL_BODIES", "CFG binding: UNBOUND", "Repository: NOT_REQUESTED", "Publication intent: DO_NOT_SAVE", "Publication subject: unavailable", "Commit knowledge: NOT_STARTED"), streams.outputLines());
    assertEquals(1, streams.errorLines().stream().filter(line -> line.equals("Diagnostic: \"" + CliTestFixtures.errorDiagnostic().diagnosticKey() + "\"")).count());
    assertEquals(true, streams.errorLines().contains("Qualification: APPLICATION APPLICATION_DIAGNOSTIC"));
  }

  @Test
  void givenRepositoryFailure_whenOutcomeIsPresented_thenAnalysisAndRepositoryFailureRemainSeparate() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var streams = new CliStreams();

    // Act
    var exitCode = CliTestFixtures.present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withFailedPublication(run,
        new RepositoryException("database unavailable", new IllegalStateException("offline")))), streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REPOSITORY_FAILED, exitCode);
    assertEquals("Completion: COMPLETE", streams.outputLines().get(2));
    assertEquals(ReportTextExpectations.publicationFailure(run, "org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException", "not-applicable", "UNKNOWN"), streams.errorLines());
  }

  @Test
  void givenInputRejection_whenOutcomeIsPresented_thenInputFailureIsRendered() {
    // Arrange
    var streams = new CliStreams();

    // Act
    var exitCode = CliTestFixtures.present(new AnalyzeCommandOutcome.InputRejected(new AnalysisInputException("source unreadable", new IOException("input"))), streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REJECTED, exitCode);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(ReportTextExpectations.namedInput("source unreadable", "java.io.IOException", "input"), streams.errorLines());
  }

  @Test
  void givenEnvironmentFailure_whenOutcomeIsPresented_thenAnalysisFailureIsRendered() {
    // Arrange
    var streams = new CliStreams();

    // Act
    var exitCode = CliTestFixtures.present(new AnalyzeCommandOutcome.EnvironmentFailed(new AnalysisEnvironmentException("compiler unavailable")), streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, exitCode);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(ReportTextExpectations.namedEnvironment("compiler unavailable"), streams.errorLines());
  }

  @Test
  void givenParserRejection_whenOutcomeIsPresented_thenMessageAndUsageAreRendered() {
    // Arrange
    var streams = new CliStreams();
    var rejection = new AnalyzeCommandOutcome.UsageRejected(new ArgumentRejection.RootCount(0));

    // Act
    var exitCode = CliTestFixtures.present(rejection, streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REJECTED, exitCode);
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
      var streams = new CliStreams();
      // Act
      var code = CliTestFixtures.present(new AnalyzeCommandOutcome.Executed(row.execution()), streams);

      // Assert
      assertEquals(row.exitCode(), code);
      var publicationFields = row.repositoryText().equals("NOT_ELIGIBLE") ? 5
          : row.repositoryText().equals("NOT_REQUESTED") && row.execution().run().representation().ast().result().isPresent() ? 9 : 4;
      assertEquals("Repository: " + row.repositoryText(), streams.outputLines().get(streams.outputLines().size() - publicationFields));
      assertEquals("Completion: " + row.execution().run().completion(), streams.outputLines().get(2));
    }
  }

  @Test
  void givenEqualDiagnosticValuesAtTwoSlots_whenExecutionIsPresented_thenBothSlotsRemainInOriginalOrder() {
    // Arrange
    var original = CliTestFixtures.completedRun();
    var warning = CliTestFixtures.warningDiagnostic();
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(warning, warning));
    var streams = new CliStreams();

    // Act
    var code = CliTestFixtures.present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)), streams);

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
    var streams = new CliStreams();
    // Act
    var code = CliTestFixtures.present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)), streams);
    streams.output().println("output remains open");
    streams.error().println("error remains open");
    // Assert
    assertEquals(0, code);
    var expectedOutput = new ArrayList<>(expectedOutput(run, "COMPLETE", "NOT_REQUESTED"));
    expectedOutput.add("output remains open");
    var expectedError = new ArrayList<>(expectedWarning(1));
    expectedError.add("error remains open");
    assertEquals(expectedOutput, streams.outputLines());
    assertEquals(expectedError, streams.errorLines());
  }

  @Test
  void givenArabicNumberFormatLocale_whenCompletedOutcomeIsPresented_thenSummaryUsesCanonicalDecimalDigits() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var outcome = new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run));
    var streams = new CliStreams();
    var previousFormatLocale = Locale.getDefault(Locale.Category.FORMAT);

    int code;
    try {
      Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG-u-nu-arab"));

      // Act
      code = CliTestFixtures.present(outcome, streams);
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, previousFormatLocale);
    }

    // Assert
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, code);
    assertEquals(expectedOutput(run, "COMPLETE", "NOT_REQUESTED"), streams.outputLines());
    assertEquals(expectedWarning(1), streams.errorLines());
  }

  @Test
  void givenProductionWithReversedExplicitSelection_whenPresented_thenSelectionAndCoverageUseTheirOwnOrders() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { static void first() {} static void middle() {} static void last() {} }");
    var source = ast.result().orElseThrow();
    var methods = source.program().methods();
    var first = methods.get(0).methodKey();
    var middle = methods.get(1).methodKey();
    var last = methods.get(2).methodKey();
    var selection = new CfgSelection(List.of(last, first), CfgProfile.JAVA_BOUNDED);
    var request = new AnalysisComputationRequest(
        new AstProductionRequest(source.snapshot(), source.environment().compilerConfiguration(), ast.profile()), Optional.of(selection));
    var run = AnalysisCapabilities.computation(ignored -> ast, CfgDerivers.standard()).analyze(request);
    var outcome = new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run));
    var streams = new CliStreams();

    // Act
    var code = CliTestFixtures.present(outcome, streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, code);
    var expectedOutput = new ArrayList<>(List.of("Run: \"" + run.identity().key() + "\"", "Software version: \"" + source.identity().softwareSignature() + "\"",
        "Completion: COMPLETE", "Java release: 25", "Java source files: 1", "AST: COMPLETE", "AST profile: \"java-source-bounded/1\"",
        "CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/3\"", "CFG selection: METHODS",
        "CFG binding: \"" + source.identity().astResultKey() + "\"", "Selected method: \"" + last + "\"", "Selected method: \"" + first + "\"",
        "Java types: 1", "Java methods: 3", "Analyzed method bodies: 2", "CFG operations: 0", "CFG nodes: 4", "CFG edges: 2",
        "Method CFG: \"" + first + "\" AVAILABLE", "Method CFG: \"" + middle + "\" NOT_REQUESTED",
        "Method CFG: \"" + last + "\" AVAILABLE", "Repository: NOT_REQUESTED"));
    expectedOutput.addAll(ReportTextExpectations.noPublication(run));
    assertEquals(expectedOutput, streams.outputLines());
    assertEquals(List.of(), streams.errorLines());
  }

  @Test
  void givenRepositoryFailureAfterCompletedRunWithWarning_whenPresented_thenFailureStatusAndDiagnosticFollowTheirReports() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var failure = new RepositoryException("database unavailable", new IllegalStateException("offline"));
    var outcome = new AnalyzeCommandOutcome.Executed(CliTestFixtures.withFailedPublication(run, failure));
    var streams = new CliStreams();
    var expectedError = new ArrayList<>(expectedWarning(1));
    expectedError.addAll(ReportTextExpectations.publicationFailure(run, "org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException", "not-applicable", "UNKNOWN"));

    // Act
    var code = CliTestFixtures.present(outcome, streams);

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REPOSITORY_FAILED, code);
    assertEquals(expectedOutput(run, "COMPLETE", "FAILED"), streams.outputLines());
    assertEquals(expectedError, streams.errorLines());
  }

  private static List<String> expectedOutput(AnalysisRun run, String completion, String repository) {
    var ast = run.representation().ast().result().orElseThrow();
    var key = ast.program().methods().getFirst().methodKey();
    var result = new ArrayList<>(List.of("Run: \"" + run.identity().key() + "\"", "Software version: \"" + SoftwareVersion.from(ast.snapshot(), ast.environment()).signature() + "\"",
        "Completion: " + completion, "Java release: 25", "Java source files: 1", "AST: COMPLETE", "AST profile: \"java-source-bounded/1\"",
        "CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/3\"", "CFG selection: ALL_BODIES",
        "CFG binding: \"" + ast.identity().astResultKey() + "\"", "Selected method: \"" + key + "\"", "Java types: 1", "Java methods: 1",
        "Analyzed method bodies: 1", "CFG operations: 0", "CFG nodes: 2", "CFG edges: 1", "Method CFG: \"" + key + "\" AVAILABLE",
        "Repository: " + repository));
    result.addAll(repository.equals("NOT_REQUESTED") ? ReportTextExpectations.noPublication(run) : ReportTextExpectations.completedPublication(run, repository.equals("SAVED") ? "KNOWN_COMMIT" : repository.equals("FAILED") ? "UNKNOWN" : "NOT_STARTED"));
    if (repository.equals("NOT_ELIGIBLE")) {
      result.add("Publication impediments: RUN_NOT_COMPLETE, ERROR_DIAGNOSTICS");
    }
    return result;
  }

  private static List<String> expectedWarning(int occurrence) {
    return List.of("Diagnostic: \"" + CliTestFixtures.warningDiagnostic().diagnosticKey() + "\"", "  Occurrence: " + occurrence,
        "  Owner: APPLICATION", "  Native position: " + (occurrence - 1), "  Severity: WARNING", "  Stage: FRONTEND", "  Cause: OTHER_ANALYSIS_ERROR", "  Code: \"cli.warning\"",
        "  Message: \"analysis warning\"", "  Origins: unavailable");
  }

  private record PresentationRow(AnalysisExecution execution, int exitCode, String repositoryText) {
  }
}
