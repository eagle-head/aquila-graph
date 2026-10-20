package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Sha256AvailabilityTest {
  @Test
  void givenNoDigestProviders_whenIdentityDigestsAreRequestedInAnIsolatedProcess_thenEveryEntryPointPreservesTheUnavailableAlgorithmCause(
      @TempDir Path directory) throws IOException, InterruptedException {
    // Arrange
    var executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var classPath = Path.of("target", "partitions", "identity", "classes") + File.pathSeparator + Path.of("target", "test-classes");
    var command = new ArrayList<String>();
    command.add(executable.toString());
    command.add("-Xms32m");
    command.add("-Xmx128m");
    ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(argument -> argument.startsWith("-javaagent:"))
        .forEach(command::add);
    command.add("-cp");
    command.add(classPath);
    command.add("org.aquilagraph.identity.api.Sha256AvailabilityProbe");
    var output = directory.resolve("sha256-availability.log");

    // Act
    var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      var completed = process.waitFor(30, TimeUnit.SECONDS);

      // Assert
      assertTrue(completed, "Digest availability process did not finish: " + output);
      var text = Files.readString(output).strip();
      assertEquals(0, process.exitValue(), text);
      assertEquals("sha256-unavailable-ok", text);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Digest availability process did not terminate");
      }
    }
  }
}
