package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("process")
class IdentityRuntimeProcessIT {
  @Test
  void givenActualIdentityValues_whenJavaBaseOnlyProcessRunsWithoutNullnessDefinitions_thenCaptureAndQualificationSucceed()
      throws IOException, InterruptedException {
    // Arrange
    var executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var classPath = Path.of("target", "test-classes").toString();
    var process = new ProcessBuilder(executable.toString(), "--limit-modules", "java.base", "--module-path",
        Path.of("target", "classes", "org.aquilagraph.identity").toString(), "--add-modules", "org.aquilagraph.identity", "-cp", classPath,
        "org.aquilagraph.identity.consumer.IdentityRuntimeProbe").redirectErrorStream(true).start();

    try {
      // Act
      var completed = process.waitFor(30, TimeUnit.SECONDS);

      // Assert
      assertTrue(completed, "Java-base-only identity process did not finish");
      try (var output = process.getInputStream()) {
        var text = new String(output.readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, process.exitValue(), text);
        assertEquals("identity-runtime-ok", text);
      }
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(30, TimeUnit.SECONDS);
      }
    }
  }
}
