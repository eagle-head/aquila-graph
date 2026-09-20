package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.DependencyKind;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisEnvironmentCaptureTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenDependencyChangesAfterCapture_whenEnvironmentIsVerified_thenAnalysisIsRejected()
      throws IOException {
    // Arrange
    var dependency = temporaryDirectory.resolve("dependency.jar");
    Files.writeString(dependency, "first");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependency), List.of(), List.of());
    var invocation = JavaCompilerInvocation.from(configuration);
    var compiler = ToolProvider.getSystemJavaCompiler();
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());
    var expected = capture.capture(compiler, configuration, invocation.analysisOptions());
    Files.writeString(dependency, "second");

    // Act
    var exception = assertThrows(IOException.class,
        () -> capture.verifyUnchanged(compiler, configuration, invocation.analysisOptions(), expected));

    // Assert
    assertEquals("Compiler dependencies changed during analysis", exception.getMessage());
  }

  @Test
  void givenMissingDependency_whenEnvironmentIsCaptured_thenMissingStateIsExplicit()
      throws IOException {
    // Arrange
    var missingDependency = temporaryDirectory.resolve("missing.jar");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(missingDependency), List.of(), List.of());
    var invocation = JavaCompilerInvocation.from(configuration);
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());

    // Act
    var environment = capture.capture(ToolProvider.getSystemJavaCompiler(), configuration, invocation.analysisOptions());

    // Assert
    var dependency = environment.classPath().getFirst();
    assertEquals(DependencyKind.MISSING, dependency.kind());
    assertEquals(0, dependency.byteCount());
    assertEquals(0, dependency.entryCount());
  }
}
