package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.repository.AnalysisRunRepository;
import org.aquilagraph.repository.AnalysisRunRepositoryFactory;
import org.aquilagraph.repository.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeCommandTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenJavaSourceRoot_whenCommandRuns_thenCompletedSummaryIsPrinted() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString()},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_COMPLETED, exitCode);
    assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).contains("Completion: COMPLETED"));
    assertTrue(
        capturedOutput.toString(StandardCharsets.UTF_8).contains("Repository: NOT_REQUESTED"));
    assertEquals("", capturedError.toString(StandardCharsets.UTF_8));
  }

  @Test
  void givenNoSourceRoot_whenCommandRuns_thenUsageExitCodeIsReturned() {
    // Arrange
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[0],
            System.out,
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertTrue(capturedError.toString(StandardCharsets.UTF_8).contains("Usage:"));
  }

  @Test
  void givenMalformedUtf8Source_whenCommandRuns_thenInputIsRejected() throws IOException {
    // Arrange
    var malformedSource = temporaryDirectory.resolve("Malformed.java");
    Files.write(
        malformedSource, new byte[] {(byte) 0xC3, (byte) 0x28});
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString()},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals("", capturedOutput.toString(StandardCharsets.UTF_8));
    assertTrue(capturedError.toString(StandardCharsets.UTF_8).contains("Input rejected:"));
    assertTrue(capturedError.toString(StandardCharsets.UTF_8).contains(malformedSource.toString()));
    assertFalse(capturedError.toString(StandardCharsets.UTF_8).contains("Analysis failed:"));
  }

  @Test
  void givenCompletedAnalysisAndUnavailableRepository_whenSaveRuns_thenCompletionRemainsSeparate()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command =
        command(
            () ->
                new AnalysisRunRepository() {
                  @Override
                  public void save(AnalysisRun run) {
                    throw new RepositoryException("database unavailable", new IllegalStateException("connection refused"));
                  }

                  @Override
                  public void close() {}
                });

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString(), "--persist"},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REPOSITORY_FAILED, exitCode);
    assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).contains("Completion: COMPLETED"));
    assertTrue(
        capturedError.toString(StandardCharsets.UTF_8)
            .contains("Repository save failed: database unavailable"));
  }

  private static AnalyzeCommand command(AnalysisRunRepositoryFactory repositoryFactory) {
    return new AnalyzeCommand(
        new AnalysisApplicationService(
            new JavaFrontend(new JavaSourceLoader()),
            new AnalysisEngine(),
            repositoryFactory));
  }

  private static AnalysisRunRepositoryFactory repositoryMustNotOpen() {
    return () -> {
      throw new AssertionError("Repository must not be opened");
    };
  }
}
