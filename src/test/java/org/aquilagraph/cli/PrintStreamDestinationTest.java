package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cli.ChannelEmission.Status;
import org.aquilagraph.cli.CommandPresentationException.Phase;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.junit.jupiter.api.Test;

class PrintStreamDestinationTest {
  @Test
  void givenActualStreamWithPreexistingFlag_whenRequiredOrUnused_thenOnlyRequiredChannelIsInspectedAndSkipped() {
    // Arrange
    for (var required : List.of(false, true)) {
      var sink = new ObservingSink();
      sink.writeFailure = new IOException("earlier write");
      try (var stream = new PrintStream(sink, false, StandardCharsets.UTF_8)) {
        stream.print("earlier");
        assertTrue(stream.checkError());
        sink.writeFailure = null;
        var writesBefore = sink.writes;
        var flushesBefore = sink.flushes;
        var error = new EmissionTestWriter();
        var complete = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRun());
        var runner = new CommandRunner(new AnalyzeCommand(request -> complete));

        // Act
        var invocation = runner.run(required ? new String[] {"source"} : new String[] {"--unknown"},
            CommandDestination.printStream(stream), CommandDestination.throwingWriter(error));

        // Assert
        assertEquals(writesBefore, sink.writes);
        assertEquals(flushesBefore + (required ? 1 : 0), sink.flushes);
        assertEquals(0, sink.closes);
        assertEquals(required ? 4 : 2, invocation.exitCode());
        if (required) {
          assertEquals(new ChannelEmission.StreamError(Phase.PREFLIGHT), invocation.emission().output());
          assertEquals(Status.UNUSED, invocation.emission().error());
          assertEquals(0, error.writes + error.flushes);
        } else {
          assertEquals(Status.UNUSED, invocation.emission().output());
          assertEquals(Status.COMPLETED, invocation.emission().error());
          assertEquals("Unknown option: \"--unknown\"" + System.lineSeparator() + AnalyzeCommandParser.USAGE + System.lineSeparator(), error.text.toString());
        }
      }
    }
  }

  @Test
  void givenActualStreamWriteOrFlushingInspectionFault_whenReportIsEmitted_thenFlagObservationNamesInspectionWithoutInventedCause() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRunWithWarning());
    for (var failOnWrite : List.of(true, false)) {
      var sink = new ObservingSink();
      if (failOnWrite) { sink.writeFailure = new IOException("write"); sink.prefix = 9; }
      else { sink.flushFailureAt = 2; sink.flushFailure = new IOException("inspection flush"); }
      var error = new EmissionTestWriter();
      var runner = new CommandRunner(new AnalyzeCommand(request -> execution));
      try (var stream = new PrintStream(sink, false, StandardCharsets.UTF_8)) {
        // Act
        var invocation = runner.run(new String[] {"source"}, CommandDestination.printStream(stream), CommandDestination.throwingWriter(error));

        // Assert
        var failure = assertInstanceOf(ChannelEmission.StreamError.class, invocation.emission().output());
        assertEquals(Phase.POST_WRITE_INSPECTION, failure.phase());
        assertEquals(Status.COMPLETED, invocation.emission().error());
        assertEquals(4, invocation.exitCode());
        assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution());
        assertEquals(2, sink.flushes);
        assertEquals(0, sink.closes);
        assertEquals(1, error.writes);
        assertEquals(1, error.flushes);
        if (failOnWrite) { assertEquals(9, sink.bytes.size()); }
        else { assertTrue(sink.bytes.toString(StandardCharsets.UTF_8).startsWith("Run: \"")); }
      }
    }
  }

  @Test
  void givenBorrowedStreamEncoding_whenUnicodeUsageIsEmitted_thenAdapterPreservesEncodingAndOwnership() {
    // Arrange
    var sink = new ObservingSink();
    var output = new EmissionTestWriter();
    var runner = new CommandRunner(new AnalyzeCommand(request -> { throw new AssertionError("Usage must not execute"); }));
    try (var stream = new PrintStream(sink, false, StandardCharsets.UTF_16LE)) {
      // Act
      var invocation = runner.run(new String[] {"--é雪"}, CommandDestination.throwingWriter(output), CommandDestination.printStream(stream));

      // Assert
      assertEquals(2, invocation.exitCode());
      assertEquals(Status.UNUSED, invocation.emission().output());
      assertEquals(Status.COMPLETED, invocation.emission().error());
      assertEquals("Unknown option: \"--é雪\"" + System.lineSeparator() + AnalyzeCommandParser.USAGE + System.lineSeparator(),
          sink.bytes.toString(StandardCharsets.UTF_16LE));
      assertEquals(2, sink.flushes);
      assertEquals(0, sink.closes);
      stream.print("caller");
      assertTrue(sink.bytes.toString(StandardCharsets.UTF_16LE).endsWith("caller"));
    }
  }

  @Test
  void givenRuntimeFailureAtEachStreamOperation_whenInvocationIsInterrupted_thenActualPhaseAndRequiredLaterWorkRemain() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRunWithWarning());
    for (var phase : List.of(Phase.PREFLIGHT, Phase.WRITE, Phase.POST_WRITE_INSPECTION)) {
      var original = new AnalysisEnvironmentException("destination operation", new IOException("underlying"));
      var cleanup = new IllegalStateException("cleanup");
      original.addSuppressed(cleanup);
      var sink = new ObservingSink();
      if (phase == Phase.WRITE) { sink.writeFailure = original; sink.prefix = 5; }
      else { sink.flushFailureAt = phase == Phase.PREFLIGHT ? 1 : 2; sink.flushFailure = original; }
      var error = new EmissionTestWriter();
      var runner = new CommandRunner(new AnalyzeCommand(request -> execution));
      try (var stream = new PrintStream(sink, false, StandardCharsets.UTF_8)) {
        // Act
        var observed = assertThrows(CommandPresentationException.class,
            () -> runner.run(new String[] {"source"}, CommandDestination.printStream(stream), CommandDestination.throwingWriter(error)));

        // Assert
        assertEquals(phase, observed.phase());
        assertEquals(Optional.of(OutputChannel.STDOUT), observed.channel());
        assertSame(original, observed.getCause());
        assertEquals(List.of(cleanup), List.of(original.getSuppressed()));
        assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, observed.outcome()).execution());
        var progress = observed.progress().orElseThrow();
        var failure = assertInstanceOf(ChannelEmission.AbruptFailure.class, progress.output());
        assertEquals(phase, failure.phase());
        assertSame(original, failure.cause());
        assertEquals(Status.UNATTEMPTED, progress.error());
        assertEquals(0, error.writes + error.flushes + error.closes);
        assertEquals(0, sink.closes);
        if (phase == Phase.PREFLIGHT) { assertEquals(0, sink.writes); }
        sink.flushFailure = null;
      }
    }
  }

  @Test
  void givenActualFlagFailuresOnBothStreams_whenInvocationReturns_thenBothIndependentObservationsSurvive() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRunWithWarning());
    var stdoutSink = new ObservingSink();
    var stderrSink = new ObservingSink();
    stdoutSink.writeFailure = new IOException("stdout");
    stderrSink.flushFailureAt = 2;
    stderrSink.flushFailure = new IOException("stderr");
    var runner = new CommandRunner(new AnalyzeCommand(request -> execution));
    try (var output = new PrintStream(stdoutSink, false, StandardCharsets.UTF_8);
        var error = new PrintStream(stderrSink, false, StandardCharsets.UTF_8)) {
      // Act
      var invocation = runner.run(new String[] {"source"}, CommandDestination.printStream(output), CommandDestination.printStream(error));

      // Assert
      assertEquals(new ChannelEmission.StreamError(Phase.POST_WRITE_INSPECTION), invocation.emission().output());
      assertEquals(new ChannelEmission.StreamError(Phase.POST_WRITE_INSPECTION), invocation.emission().error());
      assertEquals(4, invocation.exitCode());
      assertEquals(2, stdoutSink.flushes);
      assertEquals(2, stderrSink.flushes);
      assertEquals(0, stdoutSink.closes + stderrSink.closes);
      assertTrue(error.checkError());
      assertFalse(stdoutSink.bytes.toString(StandardCharsets.UTF_8).contains("failed"));
    }
  }

  private static final class ObservingSink extends OutputStream {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Throwable writeFailure;
    Throwable flushFailure;
    int prefix;
    int flushFailureAt;
    int writes;
    int flushes;
    int closes;

    @Override public void write(int value) throws IOException { write(new byte[] {(byte) value}, 0, 1); }
    @Override public void write(byte[] value, int offset, int length) throws IOException {
      writes++;
      if (writeFailure != null) {
        bytes.write(value, offset, Math.min(prefix, length));
        fail(writeFailure);
      }
      bytes.write(value, offset, length);
    }
    @Override public void flush() throws IOException {
      flushes++;
      if (flushFailure != null && flushes == flushFailureAt) { fail(flushFailure); }
    }
    @Override public void close() { closes++; }
    private static void fail(Throwable failure) throws IOException {
      if (failure instanceof IOException checked) { throw checked; }
      throw (RuntimeException) failure;
    }
  }
}
