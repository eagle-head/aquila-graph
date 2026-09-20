package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AquilaGraphProcessIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenPackagedJarAndCalculator_whenProcessRuns_thenAnalysisCompletes() throws IOException, InterruptedException {
    // Arrange
    var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
    var jar = Path.of("target/aquila-graph-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
    var calculator =
        Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();
    var process =
        new ProcessBuilder(
                javaExecutable.toString(), "-jar", jar.toString(), calculator.toString())
            .redirectErrorStream(true)
            .start();

    // Act
    var completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    // Assert
    assertTrue(completed, "The packaged analyzer process did not finish within 30 seconds");
    assertEquals(0, process.exitValue(), output);
    assertTrue(output.contains("Completion: COMPLETED"), output);
    assertTrue(output.contains("Java source files: 3"), output);
  }

  @Test
  void givenPackagedJarAndMalformedUtf8Source_whenProcessRuns_thenInputIsRejected()
      throws IOException, InterruptedException {
    // Arrange
    Files.write(
        temporaryDirectory.resolve("Malformed.java"), new byte[] {(byte) 0xC3, (byte) 0x28});
    var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
    var jar = Path.of("target/aquila-graph-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
    var process =
        new ProcessBuilder(
                javaExecutable.toString(),
                "-jar",
                jar.toString(),
                temporaryDirectory.toString())
            .redirectErrorStream(true)
            .start();

    // Act
    var completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    // Assert
    assertTrue(completed, "The packaged analyzer process did not finish within 30 seconds");
    assertEquals(2, process.exitValue(), output);
    assertTrue(output.contains("Input rejected:"), output);
    assertTrue(output.contains("Malformed.java"), output);
  }
}
