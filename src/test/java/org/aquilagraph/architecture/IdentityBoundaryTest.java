package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IdentityBoundaryTest {
  @Test
  void givenCompiledIdentityPartition_whenEveryClassfileIsInspected_thenOnlyIdentityAndJavaBaseDependenciesRemain() throws IOException {
    // Arrange
    var identityRoot = Path.of("target", "partitions", "identity", "classes");
    var baseModule = ModuleFinder.ofSystem().find("java.base").orElseThrow();
    Set<String> baseClasses;
    try (var reader = baseModule.open(); var resources = reader.list()) {
      baseClasses = resources.filter(name -> name.endsWith(".class")).map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
          .collect(Collectors.toUnmodifiableSet());
    }
    var nullnessMetadata = Set.of("org.jspecify.annotations.NullMarked", "org.jspecify.annotations.Nullable");

    // Act
    try (var files = Files.walk(identityRoot)) {
      var classFiles = files.filter(path -> path.toString().endsWith(".class")).toList();

      // Assert
      assertFalse(classFiles.isEmpty());
      for (var classFile : classFiles) {
        var references = ClassfileDependencies.read(Files.readAllBytes(classFile));
        for (var type : references.types()) {
          assertTrue(baseClasses.contains(type) || type.startsWith("org.aquilagraph.identity.api."), classFile + " depends on " + type);
        }
        for (var annotation : references.annotations()) {
          assertTrue(baseClasses.contains(annotation) || nullnessMetadata.contains(annotation), classFile + " retains " + annotation);
        }
      }
    }
  }

  @Test
  void givenActualIdentityValues_whenJavaBaseOnlyProcessRunsWithoutNullnessDefinitions_thenCaptureAndQualificationSucceed()
      throws IOException, InterruptedException {
    // Arrange
    var executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var classPath = Path.of("target", "partitions", "identity", "classes") + File.pathSeparator + Path.of("target", "test-classes");
    var process = new ProcessBuilder(executable.toString(), "--limit-modules", "java.base", "-cp", classPath,
        "org.aquilagraph.identity.api.IdentityRuntimeProbe").redirectErrorStream(true).start();

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
