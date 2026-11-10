package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationEligibility.Impediment;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationOutcome;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.source.api.AstCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicationEligibilityReportingTest {
  @TempDir Path directory;

  @Test
  void givenCompleteSourceWithRefusedBody_whenActualCliRuns_thenOnlyRequestedPublicationReportsOrderedImpediments() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static int run(int value) { (value) = 1; return value; } }");
    var expected = List.of(Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE, Impediment.ERROR_DIAGNOSTICS);
    var service = CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen());
    var runner = new CommandRunner(new AnalyzeCommand(service));
    for (var persist : List.of(false, true)) {
      var streams = new CliStreams();
      var arguments = persist ? new String[] {directory.toString(), "--persist"} : new String[] {directory.toString()};

      // Act
      var invocation = runner.run(arguments, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error()));

      // Assert
      var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
      var run = execution.run();
      assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, invocation.exitCode());
      assertEquals(AstCompletion.COMPLETE, run.representation().ast().completion());
      assertEquals(expected, run.publicationEligibility().impediments());
      assertFalse(run.isPublishable());
      assertEquals(persist ? PublicationStatus.NOT_ELIGIBLE : PublicationStatus.NOT_REQUESTED, execution.publication().status());
      assertEquals(CommitKnowledge.NOT_STARTED, execution.publication().knowledge());
      assertTrue(execution.publication().failure().isEmpty());
      assertTrue(streams.outputLines().contains("CFG selection: " + CfgSelection.Scope.ALL_BODIES));
      assertEquals(persist ? List.of("Publication impediments: RUN_NOT_COMPLETE, CFG_NOT_COMPLETE, ERROR_DIAGNOSTICS") : List.of(),
          streams.outputLines().stream().filter(line -> line.startsWith("Publication impediments:")).toList());
      assertFalse(streams.errorText().contains("Publication impediments:"));
      assertTrue(streams.errorLines().contains("  Severity: ERROR"));
      var report = assertInstanceOf(CommandReport.Executed.class,
          new CommandReportProjector(failure -> Optional.empty()).project(invocation.outcome()));
      assertEquals(persist ? expected : List.of(), report.publication().impediments());
      assertThrows(UnsupportedOperationException.class, () -> report.publication().impediments().add(Impediment.SOURCE_UNAVAILABLE));
    }
  }

  @Test
  void givenEmptySourceRoot_whenActualCliRequestsPublication_thenUnavailableSourceIsExplainedWithoutInventedCoverage() {
    // Arrange
    var streams = new CliStreams();
    var runner = new CommandRunner(new AnalyzeCommand(
        CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen())));

    // Act
    var invocation = runner.run(new String[] {directory.toString(), "--persist"}, CommandDestination.printStream(streams.output()),
        CommandDestination.printStream(streams.error()));

    // Assert
    var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
    assertEquals(CommandExitPolicy.EXIT_REJECTED, invocation.exitCode());
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.SOURCE_UNAVAILABLE, Impediment.CFG_NOT_COMPLETE),
        execution.run().publicationEligibility().impediments());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, execution.publication().status());
    assertEquals(CommitKnowledge.NOT_STARTED, execution.publication().knowledge());
    assertTrue(execution.publication().failure().isEmpty());
    assertTrue(streams.outputLines().contains("CFG selection: ALL_BODIES"));
    assertTrue(streams.outputLines().contains("CFG binding: UNBOUND"));
    assertEquals("Publication impediments: RUN_NOT_COMPLETE, SOURCE_UNAVAILABLE, CFG_NOT_COMPLETE", streams.outputLines().getLast());
    assertFalse(streams.errorText().contains("Publication impediments:"));
  }

  @Test
  void givenCompleteSourceAndAcknowledgedRepository_whenActualCliPublishes_thenSavedOutputContainsNoRefusalField() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static int run() { return 1; } }");
    var stored = new ArrayList<AnalysisRun>();
    var closures = new AtomicInteger();
    var service = CliTestFixtures.service(CliTestFixtures.runner(), context -> new AnalysisRunRepository() {
      @Override public void save(AnalysisRun run) { stored.add(run); }
      @Override public void close() { closures.incrementAndGet(); }
    });
    var streams = new CliStreams();
    var runner = new CommandRunner(new AnalyzeCommand(service));

    // Act
    var invocation = runner.run(new String[] {directory.toString(), "--persist"}, CommandDestination.printStream(streams.output()),
        CommandDestination.printStream(streams.error()));

    // Assert
    var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, invocation.exitCode());
    assertEquals(List.of(execution.run()), stored);
    assertEquals(1, closures.get());
    assertTrue(execution.run().publicationEligibility().eligible());
    assertEquals(PublicationStatus.SAVED, execution.publication().status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, execution.publication().knowledge());
    assertEquals("Commit knowledge: KNOWN_COMMIT", streams.outputLines().getLast());
    assertFalse(streams.outputText().contains("Publication impediments:"));
    assertEquals("", streams.errorText());
    var report = assertInstanceOf(CommandReport.Executed.class,
        new CommandReportProjector(failure -> Optional.empty()).project(invocation.outcome()));
    assertEquals(List.of(), report.publication().impediments());
  }

  @Test
  void givenCallerOwnedImpediments_whenDetachedPublicationIsCreated_thenLaterMutationCannotChangeItsFacts() {
    // Arrange
    var supplied = new ArrayList<>(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE));

    // Act
    var publication = new CommandReport.Publication(PublicationIntent.SAVE_COMPLETED, Optional.empty(), PublicationStatus.NOT_ELIGIBLE,
        Optional.of(CommitKnowledge.NOT_STARTED), OptionalInt.empty(), Optional.empty(), supplied);
    supplied.clear();

    // Assert
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE), publication.impediments());
    assertThrows(UnsupportedOperationException.class, () -> publication.impediments().clear());
  }

  @Test
  void givenIndependentAstPublication_whenReportsArePrepared_thenCompletedRunRefusalReasonsAreNotProjected() {
    // Arrange
    var rejected = CliTestFixtures.rejectedRun();
    var refused = CliTestFixtures.analyze("interface Example { static int run(int value) { (value) = 1; return value; } }");
    var outcomes = List.of(
        new AnalysisExecution(rejected, new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, Optional.empty(),
            PublicationStatus.NOT_ELIGIBLE, CommitKnowledge.NOT_STARTED, Optional.empty())),
        new AnalysisExecution(refused, new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST,
            Optional.of(new PublicationSubjectIdentity.Ast(refused.representation().ast().result().orElseThrow().identity())),
            PublicationStatus.SAVED, CommitKnowledge.KNOWN_COMMIT, Optional.empty())));
    var projector = new CommandReportProjector(failure -> Optional.empty());

    for (var execution : outcomes) {
      // Act
      var report = assertInstanceOf(CommandReport.Executed.class, projector.project(new AnalyzeCommandOutcome.Executed(execution)));
      var prepared = new CommandTextRenderer(System.lineSeparator()).render(new CommandDocumentComposer().compose(report));

      // Assert
      assertFalse(execution.run().publicationEligibility().eligible());
      assertEquals(List.of(), report.publication().impediments());
      assertFalse(prepared.output().contains("Publication impediments:"));
      assertFalse(prepared.error().contains("Publication impediments:"));
    }
  }
}
