package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationFailure;
import org.aquilagraph.application.PublicationOutcome;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cli.ChannelEmission.Status;
import org.aquilagraph.cli.CommandPresentationException.Phase;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRunnerTest {
  @TempDir Path directory;

  @Test
  void givenRealAnalysisAndAcknowledgedIsolatedPublication_whenWriterCompletesOrFails_thenOutcomeEffectsExactTextAndOriginalIoRemain()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    for (var mode : List.of("complete", "before-write", "prefix-write", "flush")) {
      var computations = new AtomicInteger();
      var saves = new AtomicInteger();
      var closures = new AtomicInteger();
      var original = new AtomicReference<AnalysisExecution>();
      var application = CliTestFixtures.service(CliTestFixtures.runner(), context -> new AnalysisRunRepository() {
        @Override public void save(AnalysisRun run) { saves.incrementAndGet(); }
        @Override public void close() { closures.incrementAndGet(); }
      });
      var runner = new CommandRunner(new AnalyzeCommand(request -> {
        computations.incrementAndGet();
        var result = application.execute(request);
        original.set(result);
        return result;
      }));
      var output = new EmissionTestWriter();
      var error = new EmissionTestWriter();
      var fault = new IOException("output observation");
      if (mode.endsWith("write")) { output.writeFailure = fault; }
      if (mode.equals("prefix-write")) { output.prefix = 12; }
      if (mode.equals("flush")) { output.flushFailure = fault; }

      // Act
      var invocation = runner.run(new String[] {directory.toString(), "--persist"},
          CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));

      // Assert
      var execution = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
      assertSame(original.get(), execution);
      assertEquals(PublicationStatus.SAVED, execution.publication().status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, execution.publication().knowledge());
      assertEquals(1, computations.get());
      assertEquals(1, saves.get());
      assertEquals(1, closures.get());
      assertEquals(1, output.writes);
      assertEquals(mode.endsWith("write") ? 0 : 1, output.flushes);
      assertEquals(0, output.closes);
      assertEquals(0, error.writes + error.flushes + error.closes);
      assertEquals(Status.UNUSED, invocation.emission().error());
      var expected = savedSummary(execution.run());
      if (mode.equals("complete")) {
        assertEquals(Status.COMPLETED, invocation.emission().output());
        assertEquals(0, invocation.exitCode());
        assertEquals(expected, output.text.toString());
      } else {
        var observed = assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().output());
        assertSame(fault, observed.cause());
        assertEquals(mode.equals("flush") ? Phase.FLUSH : Phase.WRITE, observed.phase());
        assertEquals(4, invocation.exitCode());
        assertEquals(mode.equals("flush") ? expected : expected.substring(0, output.prefix), output.text.toString());
      }
    }
  }

  @Test
  void givenBothRequiredChannels_whenIndependentCheckedWritesFail_thenEachOriginalFailureSurvivesAndOtherChannelStillCompletes() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRunWithWarning());
    for (var mask : List.of(0, 1, 2, 3)) {
      var output = new EmissionTestWriter();
      var error = new EmissionTestWriter();
      var stdoutFault = new IOException("stdout");
      var stderrFault = new IOException("stderr");
      if ((mask & 1) != 0) { output.writeFailure = stdoutFault; output.prefix = 7; }
      if ((mask & 2) != 0) { error.flushFailure = stderrFault; }
      var calls = new AtomicInteger();
      var runner = new CommandRunner(new AnalyzeCommand(request -> { calls.incrementAndGet(); return execution; }));

      // Act
      var invocation = runner.run(new String[] {"source"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));

      // Assert
      assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution());
      assertEquals(mask == 0 ? 0 : 4, invocation.exitCode());
      assertEquals(1, calls.get());
      assertEquals(1, output.writes);
      assertEquals(1, error.writes);
      assertEquals(1, error.flushes);
      assertEquals(0, output.closes + error.closes);
      if ((mask & 1) != 0) {
        var failure = assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().output());
        assertSame(stdoutFault, failure.cause());
        assertEquals(Phase.WRITE, failure.phase());
        assertEquals(0, output.flushes);
        assertEquals(7, output.text.length());
      } else { assertEquals(Status.COMPLETED, invocation.emission().output()); }
      if ((mask & 2) != 0) {
        var failure = assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().error());
        assertSame(stderrFault, failure.cause());
        assertEquals(Phase.FLUSH, failure.phase());
      } else { assertEquals(Status.COMPLETED, invocation.emission().error()); }
      assertTrue(error.text.toString().startsWith("Diagnostic: \""));
    }
  }

  @Test
  void givenSemanticNonzeroOutcomes_whenOutputFails_thenSemanticClassificationAndPublicationKnowledgeArePreserved() {
    // Arrange
    var completed = CliTestFixtures.completedRun();
    var failedPublication = CliTestFixtures.withFailedPublication(completed, new RepositoryException("publication", new IOException("port")));
    var incomplete = CliTestFixtures.notEligibleForRepository(CliTestFixtures.incompleteRun(), PublicationIntent.SAVE_COMPLETED);
    var rejected = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.rejectedRun());
    var environment = new AnalysisEnvironmentException("compiler unavailable");
    var input = new AnalysisInputException("input unavailable", new IOException("source boundary"));
    List<AnalysisUseCase> uses = List.of(request -> failedPublication, request -> incomplete, request -> rejected,
        request -> { throw environment; }, request -> { throw input; });
    var codes = List.of(3, 1, 2, 1, 2);
    for (var index = 0; index < uses.size(); index++) {
      var output = new EmissionTestWriter();
      var error = new EmissionTestWriter();
      var fault = new IOException("destination");
      output.flushFailure = fault;
      error.writeFailure = fault;
      var runner = new CommandRunner(new AnalyzeCommand(uses.get(index)));

      // Act
      var invocation = runner.run(new String[] {"source", "--persist"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));

      // Assert
      assertEquals(codes.get(index), invocation.exitCode());
      assertSame(fault, assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().error()).cause());
      if (index == 0) {
        var retained = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
        assertSame(failedPublication, retained);
        assertEquals(CommitKnowledge.UNKNOWN, retained.publication().knowledge());
        assertSame(fault, assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().output()).cause());
      }
      if (index == 3) { assertSame(environment, assertInstanceOf(AnalyzeCommandOutcome.EnvironmentFailed.class, invocation.outcome()).failure()); }
      if (index == 4) { assertSame(input, assertInstanceOf(AnalyzeCommandOutcome.InputRejected.class, invocation.outcome()).failure()); }
    }
  }

  @Test
  void givenRuntimeWriterFailuresOnEitherChannel_whenInvocationIsInterrupted_thenOriginalOutcomePhaseAndPriorProgressAreInspectable() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRunWithWarning());
    for (var channel : OutputChannel.values()) {
      for (var phase : List.of(Phase.WRITE, Phase.FLUSH)) {
        var primary = new AnalysisInputException("sink", new IOException("underlying"));
        var cleanup = new IllegalStateException("cleanup");
        primary.addSuppressed(cleanup);
        var output = new EmissionTestWriter();
        var error = new EmissionTestWriter();
        var sink = channel == OutputChannel.STDOUT ? output : error;
        if (phase == Phase.WRITE) { sink.writeFailure = primary; sink.prefix = 4; }
        else { sink.flushFailure = primary; }
        var calls = new AtomicInteger();
        var runner = new CommandRunner(new AnalyzeCommand(request -> { calls.incrementAndGet(); return execution; }));

        // Act
        var observed = assertThrows(CommandPresentationException.class,
            () -> runner.run(new String[] {"source"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error)));

        // Assert
        assertSame(primary, observed.getCause());
        assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
        assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, observed.outcome()).execution());
        assertEquals(phase, observed.phase());
        assertEquals(Optional.of(channel), observed.channel());
        assertTrue(observed.reportEmissionStarted());
        var progress = observed.progress().orElseThrow();
        var abrupt = assertInstanceOf(ChannelEmission.AbruptFailure.class, channel == OutputChannel.STDOUT ? progress.output() : progress.error());
        assertSame(primary, abrupt.cause());
        assertEquals(phase, abrupt.phase());
        assertEquals(1, calls.get());
        assertEquals(0, output.closes + error.closes);
        if (channel == OutputChannel.STDOUT) {
          assertEquals(Status.UNATTEMPTED, progress.error());
          assertEquals(0, error.writes + error.flushes);
        } else { assertEquals(Status.COMPLETED, progress.output()); }
      }
    }
  }

  @Test
  void givenPriorCheckedFailureAndLaterRuntimeFailure_whenSecondChannelInterrupts_thenBothFailuresAndTheirOrderRemain() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRunWithWarning());
    var output = new EmissionTestWriter();
    var error = new EmissionTestWriter();
    var checked = new IOException("stdout");
    var abrupt = new IllegalStateException("stderr");
    output.writeFailure = checked;
    error.flushFailure = abrupt;
    var runner = new CommandRunner(new AnalyzeCommand(request -> execution));

    // Act
    var observed = assertThrows(CommandPresentationException.class,
        () -> runner.run(new String[] {"source"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error)));

    // Assert
    var progress = observed.progress().orElseThrow();
    assertSame(checked, assertInstanceOf(ChannelEmission.IoFailure.class, progress.output()).cause());
    assertSame(abrupt, assertInstanceOf(ChannelEmission.AbruptFailure.class, progress.error()).cause());
    assertEquals(Optional.of(OutputChannel.STDERR), observed.channel());
    assertEquals(0, output.flushes);
    assertEquals(0, output.closes + error.closes);
  }

  @Test
  void givenFatalWriterFailure_whenEmissionStarts_thenOriginalErrorEscapesAndNoBorrowedDestinationCloses() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRunWithWarning());
    var output = new EmissionTestWriter();
    var error = new EmissionTestWriter();
    var fatal = new AssertionError("fatal destination");
    output.writeFailure = fatal;
    var runner = new CommandRunner(new AnalyzeCommand(request -> execution));

    // Act
    var observed = assertThrows(AssertionError.class,
        () -> runner.run(new String[] {"source"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error)));

    // Assert
    assertSame(fatal, observed);
    assertEquals(0, output.flushes + output.closes + error.writes + error.flushes + error.closes);
  }

  @Test
  void givenInterruptedIoAndSetThreadFlag_whenWriterFails_thenOriginalFailureAndInterruptionArePreserved() {
    // Arrange
    var output = new EmissionTestWriter();
    var error = new EmissionTestWriter();
    var original = new InterruptedIOException("interrupted writer");
    output.writeFailure = original;
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRun());
    var runner = new CommandRunner(new AnalyzeCommand(request -> execution));
    Thread.currentThread().interrupt();
    try {
      // Act
      var invocation = runner.run(new String[] {"source"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));

      // Assert
      assertSame(original, assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().output()).cause());
      assertEquals(4, invocation.exitCode());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
  }

  @Test
  void givenSuppressingWriterOrNullCallerDependencies_whenConstructingInvocation_thenRejectionPrecedesAnalysisAndDestinationOperations() {
    // Arrange
    var calls = new AtomicInteger();
    var command = new AnalyzeCommand(request -> { calls.incrementAndGet(); throw new AssertionError("No execution expected"); });
    var output = new EmissionTestWriter();
    var error = new EmissionTestWriter();
    var suppressing = new PrintWriter(output);
    var runner = new CommandRunner(command);

    // Act
    var refused = assertThrows(IllegalArgumentException.class,
        () -> runner.run(new String[] {"source"}, CommandDestination.throwingWriter(suppressing), CommandDestination.throwingWriter(error)));
    var nullCommand = assertThrows(NullPointerException.class, () -> new CommandRunner(null));
    var nullStream = assertThrows(NullPointerException.class, () -> CommandDestination.printStream(null));
    var nullWriter = assertThrows(NullPointerException.class, () -> CommandDestination.throwingWriter(null));

    // Assert
    assertTrue(refused.getMessage().contains("propagates checked I/O"));
    assertEquals("command", nullCommand.getMessage());
    assertEquals("stream", nullStream.getMessage());
    assertEquals("writer", nullWriter.getMessage());
    assertEquals(0, calls.get());
    assertEquals(0, output.writes + output.flushes + output.closes + error.writes + error.flushes + error.closes);
  }

  @Test
  void givenInterruptedEmissionProgress_whenConstructingOrdinaryInvocation_thenUnattemptedOrAbruptOutputIsRejected() {
    // Arrange
    var outcome = new AnalyzeCommandOutcome.UsageRejected(new ArgumentRejection.RootCount(0));
    var interrupted = new ChannelEmission.AbruptFailure(Phase.WRITE, new IllegalStateException("sink"));
    var states = List.of(new CommandEmission(Status.UNATTEMPTED, Status.UNUSED),
        new CommandEmission(Status.UNUSED, Status.UNATTEMPTED), new CommandEmission(interrupted, Status.UNUSED),
        new CommandEmission(Status.UNUSED, interrupted));
    for (var progress : states) {
      // Act
      var observed = assertThrows(IllegalArgumentException.class, () -> new CommandInvocation(outcome, progress));

      // Assert
      assertTrue(observed.getMessage().contains("ordinary invocation"));
    }
  }

  @Test
  void givenSuppliedPublicationKnowledge_whenDiagnosticEmissionFails_thenExactObservationAndOriginalPublicationFailureRemain() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    for (var knowledge : CommitKnowledge.values()) {
      var context = RepositoryOperationContext.saveCompletedRun(run.identity().key());
      var primary = new RepositoryException("publication", new IOException("port"));
      var phase = switch (knowledge) {
        case NOT_STARTED -> Neo4jRepositoryPhase.SCHEMA;
        case KNOWN_COMMIT -> Neo4jRepositoryPhase.REPOSITORY_CLOSE;
        case UNKNOWN, KNOWN_NONCOMMIT -> Neo4jRepositoryPhase.REPLACEMENT;
      };
      var carrier = new Neo4jRepositoryOperationException(context, phase, knowledge, primary);
      var publication = new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, Optional.of(context.subject()), PublicationStatus.FAILED,
          knowledge, Optional.of(new PublicationFailure(carrier)));
      var execution = new AnalysisExecution(run, publication);
      var output = new EmissionTestWriter();
      var error = new EmissionTestWriter();
      var io = new IOException("diagnostic destination");
      error.writeFailure = io;
      var calls = new AtomicInteger();
      var runner = new CommandRunner(new AnalyzeCommand(request -> { calls.incrementAndGet(); return execution; }));

      // Act
      var invocation = runner.run(new String[] {"source", "--persist"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));

      // Assert
      var retained = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution();
      assertSame(execution, retained);
      assertSame(publication, retained.publication());
      assertSame(primary, retained.publication().failure().orElseThrow().effectivePrimary());
      assertEquals(knowledge, retained.publication().knowledge());
      assertEquals(context, retained.publication().failure().orElseThrow().carrier().context());
      assertEquals(3, invocation.exitCode());
      assertEquals(Status.COMPLETED, invocation.emission().output());
      assertSame(io, assertInstanceOf(ChannelEmission.IoFailure.class, invocation.emission().error()).cause());
      assertTrue(output.text.toString().endsWith("Commit knowledge: " + knowledge + System.lineSeparator()));
      assertEquals(1, calls.get());
      assertEquals(0, error.flushes + output.closes + error.closes);
    }
  }

  @Test
  void givenOperationalEmissionFailures_whenNativeSerializationRestoresThem_thenMissingOperationContextIsExplicitlyRejected()
      throws IOException {
    // Arrange
    var original = new IOException("destination");
    var observations = List.of(new ChannelEmission.IoFailure(Phase.WRITE, original),
        new ChannelEmission.AbruptFailure(Phase.FLUSH, original));
    for (var failure : observations) {
      var bytes = new ByteArrayOutputStream();
      try (var output = new ObjectOutputStream(bytes)) { output.writeObject(failure); }
      try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        // Act
        var observed = assertThrows(InvalidObjectException.class, input::readObject);

        // Assert
        assertEquals("Destination failure requires its original operation context", observed.getMessage());
        assertSame(original, failure.getCause());
      }
    }
  }

  private static String savedSummary(AnalysisRun run) {
    var ast = run.representation().ast().result().orElseThrow();
    var method = ast.program().methods().getFirst().methodKey();
    return String.join(System.lineSeparator(), List.of("Run: \"" + run.identity().key() + "\"",
        "Software version: \"" + ast.identity().softwareSignature() + "\"", "Completion: COMPLETE", "Java release: 25",
        "Java source files: 1", "AST: COMPLETE", "AST profile: \"java-source-bounded/1\"", "CFG: COMPLETE",
        "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/2\"", "CFG selection: ALL_BODIES",
        "CFG binding: \"" + ast.identity().astResultKey() + "\"", "Selected method: \"" + method + "\"", "Java types: 1",
        "Java methods: 1", "Analyzed method bodies: 1", "CFG operations: 0", "CFG nodes: 2", "CFG edges: 1",
        "Method CFG: \"" + method + "\" AVAILABLE", "Repository: SAVED", "Publication intent: SAVE_COMPLETED",
        "Publication subject: COMPLETED_RUN \"" + run.identity().key() + "\"", "Commit knowledge: KNOWN_COMMIT")) + System.lineSeparator();
  }
}
