package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationPreparationTest {
  @TempDir
  Path directory;

  @AfterEach
  void closeCapturedInputs() throws IOException {
    PreparationFiles.clear(directory.toRealPath(), directory.resolve("target/compilation"));
  }

  @Test
  void givenCapturedResourceDirectory_whenReplacedByItsInventoryBytes_thenRootKindChangesIdentityAndReconciliationRefuses() throws Exception {
    // Arrange
    var request = fixture("public class Subject {}");
    Files.writeString(request.resources().resolve("entry.txt"), "payload");
    var inputs = new CompilationInputs(request);
    inputs.capture();
    var original = PreparationFiles.digest(request.resources());
    Files.delete(request.resources().resolve("entry.txt"));
    Files.delete(request.resources());

    // Act
    Files.writeString(request.resources(), "0:9:directory9:entry.txt4:file1:7payload");

    // Assert
    assertNotEquals(original, PreparationFiles.digest(request.resources()));
    assertThrows(IOException.class, inputs::requireUnchanged);
  }

  private CompilationRequest fixture(String source) throws IOException {
    var root = directory.toRealPath();
    var sources = Files.createDirectories(root.resolve("src/main/java"));
    var resources = Files.createDirectories(root.resolve("target/resource-staging/production"));
    var file = sources.resolve("Subject.java");
    Files.writeString(file, source);
    var output = root.resolve("target/classes");
    var environment = new HashMap<>(System.getenv());
    environment.remove("CLASSPATH");
    return new CompilationRequest(root, "production", sources, resources, output, false,
        List.of("--release", "25", "-d", output.toString(), file.toString()), environment);
  }
}
