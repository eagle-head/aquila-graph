package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AquilaGraphProcessIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenPackagedJarWithUnrepresentedCompilerKinds_whenProcessRuns_thenCoverageBlocksCompletion()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.writeString(
        sourceRoot.resolve("Supported.java"),
        "class Supported { int value(int input) { return input + 1; } }");
    // Act
    var result = runPackagedJar(sourceRoot, "--release", "25");

    // Assert
    assertTrue(result.completed(), "The packaged analyzer process did not finish within 30 seconds: " + result.output());
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertFalse(result.output().contains("frontend.unsupported-tree-kind.compilation-unit"), result.output());
    assertTrue(result.output().contains("frontend.unsupported-tree-kind.primitive-type"), result.output());
    assertTrue(result.output().contains("Java release: 25"), result.output());
    assertTrue(result.output().contains("Java source files: 1"), result.output());
    assertTrue(result.output().contains("Repository: NOT_ELIGIBLE"), result.output());
  }

  @Test
  void givenPackagedJarCalculator_whenProcessRuns_thenUnsupportedInitializersAreReported()
      throws IOException, InterruptedException {
    // Arrange
    var calculator =
        Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();

    // Act
    var result = runPackagedJar(calculator);

    // Assert
    assertTrue(result.completed(), "The packaged analyzer process did not finish within 30 seconds: " + result.output());
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("Completion: INCOMPLETE"), result.output());
    assertTrue(result.output().contains("frontend.unsupported-field-initializer"), result.output());
    assertTrue(result.output().contains("frontend.unsupported-enum-constant-initialization"), result.output());
    assertTrue(result.output().contains("Repository: NOT_ELIGIBLE"), result.output());
  }

  @Test
  void givenPackagedJarAndMalformedUtf8Source_whenProcessRuns_thenInputIsRejected()
      throws IOException, InterruptedException {
    // Arrange
    var sourceRoot = Files.createDirectories(temporaryDirectory.resolve("sources"));
    Files.write(
        sourceRoot.resolve("Malformed.java"), new byte[] {(byte) 0xC3, (byte) 0x28});
    // Act
    var result = runPackagedJar(sourceRoot);

    // Assert
    assertTrue(result.completed(), "The packaged analyzer process did not finish within 30 seconds: " + result.output());
    assertEquals(2, result.exitCode(), result.output());
    assertTrue(result.output().contains("Input rejected:"), result.output());
    assertTrue(result.output().contains("Malformed.java"), result.output());
  }

  private ProcessResult runPackagedJar(Path sourceRoot, String... options) throws IOException, InterruptedException {
    var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
    var jar = Path.of("target/aquila-graph-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
    var command = new ArrayList<>(List.of(javaExecutable.toString(), "-jar", jar.toString(), sourceRoot.toString()));
    command.addAll(List.of(options));
    var outputFile = temporaryDirectory.resolve("process-output.txt");
    var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
    try {
      var completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
      if (!completed) {
        process.destroyForcibly().waitFor();
      }
      return new ProcessResult(completed, process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly().waitFor();
      }
    }
  }

  private record ProcessResult(boolean completed, int exitCode, String output) {
  }
}
