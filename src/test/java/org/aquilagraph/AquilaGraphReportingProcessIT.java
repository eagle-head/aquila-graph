package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aquilagraph.application.PublicationStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("process")
class AquilaGraphReportingProcessIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenClosedNativeReaders_whenPackagedReportsAreWritten_thenOutputFailureChangesOnlySuccessfulExit() throws Exception {
    // Arrange
    var root = Files.createDirectories(temporaryDirectory.resolve("complete"));
    Files.writeString(root.resolve("Example.java"), "class Example { int value() { return 7; } }");
    var environment = Map.of("AQUILA_GRAPH_NEO4J_PASSWORD", "", "NEO4J_PASSWORD", "");
    var failureSummary = PackagedAnalysisSummary.expected(root, PublicationStatus.FAILED);

    // Act
    var successful = new PackagedAnalyzerProcess(temporaryDirectory.resolve("success")).run(
        PackagedAnalyzerProcess.ClosedReader.OUTPUT, List.of(), environment, root);
    var failedOutput = new PackagedAnalyzerProcess(temporaryDirectory.resolve("failed-output")).run(
        PackagedAnalyzerProcess.ClosedReader.OUTPUT, List.of(), environment, root, "--persist");
    var failedError = new PackagedAnalyzerProcess(temporaryDirectory.resolve("failed-error")).run(
        PackagedAnalyzerProcess.ClosedReader.ERROR, List.of(), environment, root, "--persist");
    var usage = new PackagedAnalyzerProcess(temporaryDirectory.resolve("usage")).run(
        PackagedAnalyzerProcess.ClosedReader.ERROR, List.of(), Map.of(), root, "--unknown");

    // Assert
    for (var result : List.of(successful, failedOutput, failedError, usage)) {
      assertTrue(result.completed(), result.error());
    }
    assertEquals(4, successful.exitCode());
    assertEquals("", successful.error());
    assertEquals(3, failedOutput.exitCode());
    assertEquals(PackagedAnalysisSummary.invalidConfigurationError(failureSummary.runId()), failedOutput.error());
    assertEquals(3, failedError.exitCode());
    assertEquals(failureSummary.output(), failedError.output());
    assertEquals(2, usage.exitCode());
    assertEquals("", usage.output());
  }

  @Test
  void givenPartialOrRejectedAnalysis_whenNativeOutputReaderCloses_thenSemanticExitAndOtherChannelSurvive() throws Exception {
    // Arrange
    var partial = Files.createDirectories(temporaryDirectory.resolve("partial"));
    Files.writeString(partial.resolve("Example.java"), "class Example { void run() { while (true) {} } }");
    var empty = Files.createDirectories(temporaryDirectory.resolve("empty"));
    var partialReport = PackagedQualificationExpectations.unsupportedLoop(partial);
    var rejectedReport = PackagedQualificationExpectations.emptyInput(empty);
    var runner = new PackagedAnalyzerProcess(temporaryDirectory.resolve("partial-process"));

    // Act
    var baseline = runner.run(partial);
    var rejectedBaseline = new PackagedAnalyzerProcess(temporaryDirectory.resolve("rejected-baseline")).run(empty);
    var failed = runner.run(PackagedAnalyzerProcess.ClosedReader.OUTPUT, List.of(), Map.of(), partial);
    var rejected = new PackagedAnalyzerProcess(temporaryDirectory.resolve("rejected-process")).run(
        PackagedAnalyzerProcess.ClosedReader.OUTPUT, List.of(), Map.of(), empty);

    // Assert
    assertTrue(baseline.completed(), baseline.error());
    assertTrue(rejectedBaseline.completed(), rejectedBaseline.error());
    assertTrue(failed.completed(), failed.error());
    assertTrue(rejected.completed(), rejected.error());
    assertEquals(1, failed.exitCode());
    assertEquals(1, baseline.exitCode());
    assertEquals(partialReport.output(), baseline.output());
    assertEquals(partialReport.error(), baseline.error());
    assertEquals(partialReport.error(), failed.error());
    assertEquals(2, rejectedBaseline.exitCode());
    assertEquals(rejectedReport.output(), rejectedBaseline.output());
    assertEquals(rejectedReport.error(), rejectedBaseline.error());
    assertEquals(2, rejected.exitCode());
    assertEquals(rejectedReport.error(), rejected.error());
  }

  @Test
  void givenUnicodeAndControlArgument_whenPackagedUsageRuns_thenUtf8AndEscapingAreExact() throws Exception {
    // Arrange
    var root = temporaryDirectory.resolve("unread");
    // The accented argument exercises non-ASCII text within the native argument code page.
    var option = "--café\t";

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(
        List.of("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"), Map.of(), root, option);

    // Assert
    assertTrue(result.completed());
    assertEquals(2, result.exitCode());
    assertEquals("", result.output());
    assertEquals(String.join(System.lineSeparator(), "Unknown option: \"--café\\t\"",
        "Usage: aquila-graph <source-root> [--release <feature>] [--persist]") + System.lineSeparator(), result.error());
  }

  @Test
  void givenUnicodeSourceNameAndMalformedBytes_whenPackagedInputIsRead_thenUtf8PreservesTheCapturedName() throws Exception {
    // Arrange
    var root = Files.createDirectories(temporaryDirectory.resolve("source-names"));
    // This compatibility fixture obtains Unicode from file metadata, independently of native argument encoding.
    var source = root.resolve("Ω.java");
    Files.write(source, new byte[] {(byte) 0xC3, (byte) 0x28});

    // Act
    var result = new PackagedAnalyzerProcess(temporaryDirectory).run(
        List.of("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"), Map.of(), root);

    // Assert
    assertTrue(result.completed());
    assertEquals(2, result.exitCode());
    assertEquals("", result.output());
    assertEquals(PackagedFailureExpectations.malformedUtf8(source), result.error());
  }

}
