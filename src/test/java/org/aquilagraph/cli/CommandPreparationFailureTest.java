package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InvalidObjectException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.cli.CommandPresentationException.Phase;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AnalysisInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandPreparationFailureTest {
  @TempDir Path directory;

  @Test
  void givenAcknowledgedPublicationInIsolatedRepository_whenEachPreparationCollaborationFails_thenOriginalOutcomeSurvivesWithZeroChannelOperations()
      throws IOException {
    // Arrange
    for (var phase : List.of(Phase.PROJECTION, Phase.COMPOSITION, Phase.FORMATTING)) {
      var root = Files.createDirectory(directory.resolve(phase.name()));
      Files.writeString(root.resolve("Example.java"), "interface Example { static void run() {} }");
      var saves = new AtomicInteger();
      var closures = new AtomicInteger();
      var computations = new AtomicInteger();
      var observed = new AtomicReference<AnalysisExecution>();
      var service = CliTestFixtures.service(CliTestFixtures.runner(), context -> new AnalysisRunRepository() {
        @Override public void save(AnalysisRun run) { saves.incrementAndGet(); }
        @Override public void close() { closures.incrementAndGet(); }
      });
      AnalysisUseCase execution = request -> {
        computations.incrementAndGet();
        var result = service.execute(request);
        observed.set(result);
        return result;
      };
      var cause = new AnalysisInputException("presentation detail", new IOException("preparation cause"));
      var command = failingCommand(execution, phase, cause);
      var interactions = new AtomicInteger();
      try (var output = channel(interactions); var error = channel(interactions)) {
        // Act
        var failure = assertThrows(CommandPresentationException.class,
            () -> new CommandRunner(command).run(new String[] {root.toString(), "--persist"}, CommandDestination.printStream(output), CommandDestination.printStream(error)).exitCode());

        // Assert
        assertSame(cause, failure.getCause());
        assertEquals(phase, failure.phase());
        assertFalse(failure.reportEmissionStarted());
        assertEquals(Optional.empty(), failure.channel());
        assertEquals(Optional.empty(), failure.progress());
        var retained = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, failure.outcome());
        assertSame(observed.get(), retained.execution());
        assertEquals(PublicationStatus.SAVED, retained.execution().publication().status());
        assertEquals(CommitKnowledge.KNOWN_COMMIT, retained.execution().publication().knowledge());
        assertEquals(1, computations.get());
        assertEquals(1, saves.get());
        assertEquals(1, closures.get());
        assertEquals(0, interactions.get());
        assertEquals(0, cause.getSuppressed().length);
      }
    }
  }

  @Test
  void givenFatalFailureAtEachPreparationCollaboration_whenCommandRuns_thenOriginalAbruptFamilyEscapesWithoutChannelOperations() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRun());
    for (var phase : List.of(Phase.PROJECTION, Phase.COMPOSITION, Phase.FORMATTING)) {
      var fatal = new AssertionError("preparation interrupted");
      var command = failingCommand(request -> execution, phase, fatal);
      var interactions = new AtomicInteger();
      try (var output = channel(interactions); var error = channel(interactions)) {
        // Act
        var observed = assertThrows(AssertionError.class, () -> new CommandRunner(command).run(new String[] {"source"}, CommandDestination.printStream(output), CommandDestination.printStream(error)).exitCode());

        // Assert
        assertSame(fatal, observed);
        assertEquals(0, interactions.get());
      }
    }
  }

  private static AnalyzeCommand failingCommand(AnalysisUseCase execution, Phase phase, Throwable failure) {
    var projector = new CommandReportProjector(ignored -> Optional.empty()) {
      @Override CommandReport project(AnalyzeCommandOutcome outcome) {
        if (phase == Phase.PROJECTION) { fail(failure); }
        return super.project(outcome);
      }
    };
    var composer = new CommandDocumentComposer() {
      @Override ReportDocument compose(CommandReport report) {
        if (phase == Phase.COMPOSITION) { fail(failure); }
        return super.compose(report);
      }
    };
    var renderer = new CommandTextRenderer(System.lineSeparator()) {
      @Override PreparedCommandOutput render(ReportDocument document) {
        if (phase == Phase.FORMATTING) { fail(failure); }
        return super.render(document);
      }
    };
    return new AnalyzeCommand(new AnalyzeCommandParser(JavaRelease.JAVA_25), new AnalyzeCommandExecutor(execution), projector, composer, renderer);
  }

  @Test
  void givenPreparationFailureWithTransientOperationalContext_whenNativeSerializationRestoresIt_thenRestorationIsExplicitlyRejected()
      throws IOException {
    // Arrange
    var outcome = new AnalyzeCommandOutcome.UsageRejected(new ArgumentRejection.RootCount(0));
    var failure = new CommandPresentationException(outcome, Phase.FORMATTING, new IllegalStateException("formatter"));
    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) { output.writeObject(failure); }

    // Act
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      var observed = assertThrows(InvalidObjectException.class, input::readObject);

      // Assert
      assertEquals("Presentation failure requires an established command outcome", observed.getMessage());
      assertSame(outcome, failure.outcome());
    }
  }

  private static void fail(Throwable failure) {
    if (failure instanceof RuntimeException runtime) { throw runtime; }
    throw (AssertionError) failure;
  }

  private static PrintStream channel(AtomicInteger interactions) {
    return new PrintStream(new OutputStream() {
      @Override public void write(int value) { interactions.incrementAndGet(); }
      @Override public void flush() { interactions.incrementAndGet(); }
      @Override public void close() { interactions.incrementAndGet(); }
    }, true, StandardCharsets.UTF_8);
  }
}
