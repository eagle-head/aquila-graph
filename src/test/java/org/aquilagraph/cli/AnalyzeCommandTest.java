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
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CompletedRunPublicationService;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeCommandTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenDiagnosedJavaSourceRoot_whenCommandRuns_thenIncompleteSummaryIsPrinted() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
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
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
    assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).contains("Completion: PARTIAL"));
    assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).contains("Java release: 25"));
    assertTrue(
        capturedOutput.toString(StandardCharsets.UTF_8).contains("Repository: NOT_REQUESTED"));
    assertTrue(capturedError.toString(StandardCharsets.UTF_8)
        .contains("Qualification: SOURCE PROFILE_RESTRICTION"));
  }

  @Test
  void givenExplicitSupportedRelease_whenCommandRuns_thenSelectedReleaseIsUsed() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString(), "--release", "25"},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
    assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).contains("Java release: 25"));
    assertTrue(capturedError.toString(StandardCharsets.UTF_8)
        .contains("Qualification: SOURCE PROFILE_RESTRICTION"));
  }

  @Test
  void givenUnsupportedRelease_whenCommandRuns_thenArgumentsAreRejected() {
    // Arrange
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString(), "--release", "26"},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals("", capturedOutput.toString(StandardCharsets.UTF_8));
    assertTrue(
        capturedError
            .toString(StandardCharsets.UTF_8)
            .contains("Unsupported Java release: 26. Supported releases: 25"));
  }

  @Test
  void givenMalformedRelease_whenCommandRuns_thenArgumentsAreRejected() {
    // Arrange
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString(), "--release", "latest"},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals("", capturedOutput.toString(StandardCharsets.UTF_8));
    assertTrue(
        capturedError
            .toString(StandardCharsets.UTF_8)
            .contains("Invalid Java release: latest"));
  }

  @Test
  void givenReleaseWithoutValue_whenCommandRuns_thenArgumentsAreRejected() {
    // Arrange
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString(), "--release"},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals("", capturedOutput.toString(StandardCharsets.UTF_8));
    assertTrue(
        capturedError
            .toString(StandardCharsets.UTF_8)
            .contains("--release requires a Java feature-release number"));
  }

  @Test
  void givenDuplicateReleaseOptions_whenCommandRuns_thenArgumentsAreRejected() {
    // Arrange
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {
              temporaryDirectory.toString(), "--release", "25", "--release", "25"
            },
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals("", capturedOutput.toString(StandardCharsets.UTF_8));
    assertTrue(
        capturedError
            .toString(StandardCharsets.UTF_8)
            .contains("--release may be supplied only once"));
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
            new String[] {temporaryDirectory.toString(), "--release", "25"},
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
  void givenIncompleteAnalysisAndRepositoryRequest_whenSaveRuns_thenRepositoryIsNotOpened()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
    var capturedOutput = new ByteArrayOutputStream();
    var capturedError = new ByteArrayOutputStream();
    var command = command(repositoryMustNotOpen());

    // Act
    var exitCode =
        command.run(
            new String[] {temporaryDirectory.toString(), "--release", "25", "--persist"},
            new PrintStream(capturedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(capturedError, true, StandardCharsets.UTF_8));

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
    assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).contains("Completion: PARTIAL"));
    assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).contains("Repository: NOT_ELIGIBLE"));
    assertTrue(capturedError.toString(StandardCharsets.UTF_8)
        .contains("Qualification: SOURCE PROFILE_RESTRICTION"));
  }

  private static AnalyzeCommand command(AnalysisRunRepositoryFactory repositoryFactory) {
    var analysisRunner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    return new AnalyzeCommand(new AnalysisApplicationService(analysisRunner, new AstRetentionService(context -> { throw new AssertionError("AST repository must not open"); }), new CompletedRunPublicationService(repositoryFactory)));
  }

  private static AnalysisRunRepositoryFactory repositoryMustNotOpen() {
    return () -> {
      throw new AssertionError("Repository must not be opened");
    };
  }
}
