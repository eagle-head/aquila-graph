package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.ApplicationRepositoryOperationException;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.cli.ChannelEmission.AbruptFailure;
import org.aquilagraph.cli.ChannelEmission.IoFailure;
import org.aquilagraph.cli.ChannelEmission.Status;
import org.aquilagraph.cli.ChannelEmission.StreamError;
import org.aquilagraph.cli.CommandPresentationException.Phase;
import org.junit.jupiter.api.Test;

class PublicationDiagnosticEmissionTest {
  @Test
  void givenAbruptFailureAtEachPublicationBoundary_whenRunnerEmits_thenAvailableCommitKnowledgeIsPreserved() {
    // Arrange
    record Expected(Boundary boundary, Optional<CommitKnowledge> knowledge, String renderedKnowledge) {}
    var run = CliTestFixtures.completedRun();
    var cases = List.of(new Expected(Boundary.ACQUISITION, Optional.of(CommitKnowledge.NOT_STARTED), "NOT_STARTED"),
        new Expected(Boundary.OPERATION, Optional.empty(), "unavailable"),
        new Expected(Boundary.CLOSURE, Optional.of(CommitKnowledge.KNOWN_COMMIT), "KNOWN_COMMIT"));
    for (var expected : cases) {
      var primary = new IllegalStateException("repository interrupted at " + expected.boundary());
      var application = CliTestFixtures.service(request -> run, context -> {
        if (expected.boundary() == Boundary.ACQUISITION) {
          throw primary;
        }
        return new AnalysisRunRepository() {
          @Override
          public void save(AnalysisRun result) {
            if (expected.boundary() == Boundary.OPERATION) {
              throw primary;
            }
          }

          @Override
          public void close() {
            if (expected.boundary() == Boundary.CLOSURE) {
              throw primary;
            }
          }
        };
      });
      var original = new AtomicReference<AnalysisPublicationException>();
      var runner = new CommandRunner(new AnalyzeCommand(request -> {
        try {
          return application.execute(request);
        } catch (AnalysisPublicationException failure) {
          original.set(failure);
          throw failure;
        }
      }));
      var output = new EmissionTestWriter();
      var error = new EmissionTestWriter();

      // Act
      var observed = assertThrows(AnalysisPublicationException.class, () -> run(runner, output, error));

      // Assert
      assertSame(original.get(), observed);
      assertSame(primary, observed.getCause());
      var carrier = assertInstanceOf(ApplicationRepositoryOperationException.class, observed.failure());
      assertEquals(expected.boundary(), carrier.boundary());
      assertEquals(expected.knowledge(), carrier.knowledge());
      var evidence = error.text.toString().lines()
          .filter(line -> line.startsWith("  Commit knowledge: ") || line.startsWith("  Application boundary: "))
          .toList();
      assertEquals(List.of("  Commit knowledge: " + expected.renderedKnowledge(), "  Application boundary: " + expected.boundary()),
          evidence);
      assertEquals(1, error.writes);
      assertEquals(1, error.flushes);
      assertEquals(0, error.closes);
      assertUntouched(output);
    }
  }

  @Test
  void givenCheckedDiagnosticWriteFailures_whenRunnerEmits_thenExactCauseAndObservedPrefixArePreserved() {
    // Arrange
    var original = publicationFailure(CliTestFixtures.completedRun(), "save failed");
    for (var prefix : List.of("", "Publication")) {
      var runner = failingRunner(original);
      var output = new EmissionTestWriter();
      var error = new EmissionTestWriter();
      var failure = new IOException("diagnostic write");
      error.writeFailure = failure;
      error.prefix = prefix.length();

      // Act
      var observed = assertThrows(PublicationDiagnosticException.class, () -> run(runner, output, error));

      // Assert
      var diagnostic = assertInstanceOf(IoFailure.class, diagnosticObservation(observed, original, Phase.WRITE));
      assertEquals(Phase.WRITE, diagnostic.phase());
      assertSame(failure, diagnostic.cause());
      assertSame(failure, observed.getCause());
      assertSame(failure, observed.presentationFailure().orElseThrow());
      assertEquals(prefix, error.text.toString());
      assertEquals(1, error.writes);
      assertEquals(0, error.flushes + error.closes);
      assertUntouched(output);
    }
  }

  @Test
  void givenCheckedDiagnosticFlushFailure_whenRunnerEmits_thenWrittenEvidenceAndFlushFailureArePreserved() {
    // Arrange
    var original = publicationFailure(CliTestFixtures.completedRun(), "save failed");
    var runner = failingRunner(original);
    var output = new EmissionTestWriter();
    var error = new EmissionTestWriter();
    var failure = new IOException("diagnostic flush");
    error.flushFailure = failure;

    // Act
    var observed = assertThrows(PublicationDiagnosticException.class, () -> run(runner, output, error));

    // Assert
    var diagnostic = assertInstanceOf(IoFailure.class, diagnosticObservation(observed, original, Phase.FLUSH));
    assertEquals(Phase.FLUSH, diagnostic.phase());
    assertSame(failure, diagnostic.cause());
    assertSame(failure, observed.getCause());
    assertSame(failure, observed.presentationFailure().orElseThrow());
    assertTrue(error.text.toString().startsWith("Publication aborted"));
    assertTrue(error.text.toString().contains("save failed"));
    assertEquals(1, error.writes);
    assertEquals(1, error.flushes);
    assertEquals(0, error.closes);
    assertUntouched(output);
  }

  @Test
  void givenStreamErrorFlagBeforeOrAfterDiagnosticWrite_whenRunnerEmits_thenPhaseSurvivesWithoutInventedCause() {
    // Arrange
    var original = publicationFailure(CliTestFixtures.completedRun(), "save failed");
    for (var phase : List.of(Phase.PREFLIGHT, Phase.POST_WRITE_INSPECTION)) {
      var runner = failingRunner(original);
      var output = new EmissionTestWriter();
      var sink = new FailingStreamSink();
      try (var error = new PrintStream(sink, false, StandardCharsets.UTF_8)) {
        if (phase == Phase.PREFLIGHT) {
          error.print("earlier write");
        }
        var writesBefore = sink.writes;
        var flushesBefore = sink.flushes;

        // Act
        var observed = assertThrows(PublicationDiagnosticException.class, () -> runner.run(new String[] {"source", "--persist"},
            CommandDestination.throwingWriter(output), CommandDestination.printStream(error)));

        // Assert
        assertEquals(new StreamError(phase), diagnosticObservation(observed, original, phase));
        assertTrue(observed.presentationFailure().isEmpty());
        assertNull(observed.getCause());
        assertEquals(flushesBefore + (phase == Phase.PREFLIGHT ? 1 : 2), sink.flushes);
        if (phase == Phase.PREFLIGHT) {
          assertEquals(writesBefore, sink.writes);
        } else {
          assertTrue(sink.writes > writesBefore);
        }
        assertEquals(0, sink.closes);
        assertUntouched(output);
      }
    }
  }

  @Test
  void givenRuntimeDiagnosticWriteFailures_whenRunnerEmits_thenOriginalPublicationAndSecondaryFailureRemainDistinct() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var original = publicationFailure(run, "original save failed");
    List<RuntimeException> failures = List.of(new IllegalStateException("diagnostic write"),
        publicationFailure(run, "secondary save failed"));
    for (var failure : failures) {
      var runner = failingRunner(original);
      var output = new EmissionTestWriter();
      var error = new EmissionTestWriter();
      var cleanup = new IllegalStateException("secondary cleanup");
      failure.addSuppressed(cleanup);
      var secondaryCause = failure.getCause();
      var originalCause = original.getCause();
      var originalCleanup = List.of(original.getSuppressed());
      var originalCarrierCleanup = List.of(original.failure().getSuppressed());
      error.writeFailure = failure;

      // Act
      var observed = assertThrows(PublicationDiagnosticException.class, () -> run(runner, output, error));

      // Assert
      var diagnostic = assertInstanceOf(AbruptFailure.class, diagnosticObservation(observed, original, Phase.WRITE));
      assertEquals(Phase.WRITE, diagnostic.phase());
      assertSame(failure, diagnostic.cause());
      assertSame(failure, observed.getCause());
      assertSame(failure, observed.presentationFailure().orElseThrow());
      assertSame(secondaryCause, failure.getCause());
      assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
      assertSame(originalCause, original.getCause());
      assertEquals(originalCleanup, List.of(original.getSuppressed()));
      assertEquals(originalCarrierCleanup, List.of(original.failure().getSuppressed()));
      assertEquals(1, error.writes);
      assertEquals(0, error.flushes + error.closes);
      assertUntouched(output);
    }
  }

  @Test
  void givenFatalDiagnosticWriteFailure_whenRunnerEmits_thenExactErrorEscapesAndDestinationsRemainBorrowed() {
    // Arrange
    var original = publicationFailure(CliTestFixtures.completedRun(), "save failed");
    var runner = failingRunner(original);
    var output = new EmissionTestWriter();
    var error = new EmissionTestWriter();
    var fatal = new AssertionError("diagnostic write");
    error.writeFailure = fatal;

    // Act
    var observed = assertThrows(AssertionError.class, () -> run(runner, output, error));

    // Assert
    assertSame(fatal, observed);
    assertEquals(1, error.writes);
    assertEquals(0, error.flushes + error.closes);
    assertUntouched(output);
  }

  private static ChannelEmission diagnosticObservation(PublicationDiagnosticException observed, AnalysisPublicationException original,
      Phase phase) {
    assertSame(original, observed.publicationFailure());
    assertEquals(0, observed.getSuppressed().length);
    assertEquals(phase, observed.phase());
    assertEquals(Optional.of(OutputChannel.STDERR), observed.channel());
    var progress = observed.progress().orElseThrow();
    assertEquals(Status.UNUSED, progress.output());
    return progress.error();
  }

  private static void assertUntouched(EmissionTestWriter output) {
    assertEquals(0, output.writes + output.flushes + output.closes);
  }

  private static CommandRunner failingRunner(AnalysisPublicationException failure) {
    return new CommandRunner(new AnalyzeCommand(request -> {
      throw failure;
    }));
  }

  private static AnalysisPublicationException publicationFailure(AnalysisRun run, String message) {
    var primary = new IllegalStateException(message);
    var application = CliTestFixtures.service(request -> run, context -> new AnalysisRunRepository() {
      @Override
      public void save(AnalysisRun result) {
        throw primary;
      }

      @Override
      public void close() {
      }
    });
    return assertThrows(AnalysisPublicationException.class, () -> new AnalyzeCommand(application).execute("source", "--persist"));
  }

  private static void run(CommandRunner runner, EmissionTestWriter output, EmissionTestWriter error) {
    runner.run(new String[] {"source", "--persist"}, CommandDestination.throwingWriter(output), CommandDestination.throwingWriter(error));
  }

  private static final class FailingStreamSink extends OutputStream {
    private int writes;
    private int flushes;
    private int closes;

    @Override
    public void write(int value) throws IOException {
      writes++;
      throw new IOException("diagnostic stream write");
    }

    @Override
    public void flush() {
      flushes++;
    }

    @Override
    public void close() {
      closes++;
    }
  }
}
