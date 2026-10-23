package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencyKind;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class AnalysisEnvironmentCaptureTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenIdenticalCapturedEnvironment_whenEnvironmentIsVerified_thenFullRecordComparisonSucceeds() throws IOException {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var invocation = JavaCompilerInvocation.from(configuration);
    var compiler = ToolProvider.getSystemJavaCompiler();
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());
    var expected = capture.capture(compiler, configuration, invocation.analysisOptions());

    // Act
    var actual = capture.capture(compiler, configuration, invocation.analysisOptions());

    // Assert
    assertEquals(expected, actual);
    assertDoesNotThrow(() -> capture.verifyUnchanged(compiler, configuration, invocation.analysisOptions(), expected));
  }

  @Test
  void givenEqualDependencyContentsAtDifferentLocations_whenEnvironmentIsVerified_thenEqualDigestDoesNotBypassFullRecordComparison()
      throws IOException {
    // Arrange
    var originalPath = temporaryDirectory.resolve("original.jar");
    var relocatedPath = temporaryDirectory.resolve("relocated.jar");
    Files.write(originalPath, new byte[] {0, (byte) 0xff});
    Files.write(relocatedPath, new byte[] {0, (byte) 0xff});
    var original = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(originalPath), List.of(), List.of());
    var relocated = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(relocatedPath), List.of(), List.of());
    var compiler = ToolProvider.getSystemJavaCompiler();
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());
    var originalOptions = JavaCompilerInvocation.from(original).analysisOptions();
    var relocatedOptions = JavaCompilerInvocation.from(relocated).analysisOptions();
    var expected = capture.capture(compiler, original, originalOptions);

    // Act
    var actual = capture.capture(compiler, relocated, relocatedOptions);
    var failure = assertThrows(IOException.class, () -> capture.verifyUnchanged(compiler, relocated, relocatedOptions, expected));

    // Assert
    assertEquals(expected.environmentDigest(), actual.environmentDigest());
    assertNotEquals(expected, actual);
    assertEquals("Compiler dependencies changed during analysis", failure.getMessage());
  }

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

  @Test
  void givenDependencyDirectoryChangedDuringARealCompilerSession_whenFullEnvironmentIsRecapturedAfterClose_thenLateChangePreventsSuccessfulVerification()
      throws IOException {
    // Arrange
    var dependency = Files.createDirectory(temporaryDirectory.resolve("dependency"));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependency), List.of(), List.of());
    var invocation = JavaCompilerInvocation.from(configuration);
    var compiler = ToolProvider.getSystemJavaCompiler();
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());
    var expected = capture.capture(compiler, configuration, invocation.analysisOptions());
    var snapshot = InputSnapshot.fromSources(temporaryDirectory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), "class Sample { int read() { return 1; } }")));
    try (var session = CompilerSessionAcquisition.open(compiler, invocation, new SnapshotSourceCatalog(snapshot))) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      assertEquals(List.of(), compilation.diagnostics());
      Files.write(dependency.resolve("late.bin"), new byte[] {0, (byte) 0xff});
    }

    // Act
    var actual = capture.capture(compiler, configuration, invocation.analysisOptions());
    var failure = assertThrows(IOException.class, () -> capture.verifyUnchanged(compiler, configuration, invocation.analysisOptions(), expected));

    // Assert
    assertEquals(0, expected.classPath().getFirst().entryCount());
    assertEquals(1, actual.classPath().getFirst().entryCount());
    assertEquals(2, actual.classPath().getFirst().byteCount());
    assertNotEquals(expected, actual);
    assertEquals("Compiler dependencies changed during analysis", failure.getMessage());
  }

  @Test
  void givenRuntimeProvenanceChangedAfterRealCompilerWork_whenFullEnvironmentIsRecaptured_thenUnchangedDependenciesDoNotBypassTheMismatch()
      throws IOException {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var invocation = JavaCompilerInvocation.from(configuration);
    var compiler = ToolProvider.getSystemJavaCompiler();
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());
    var expected = capture.capture(compiler, configuration, invocation.analysisOptions());
    var snapshot = InputSnapshot.fromSources(temporaryDirectory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}")));
    try (var session = CompilerSessionAcquisition.open(compiler, invocation, new SnapshotSourceCatalog(snapshot))) {
      session.analyze(session.parse(), JavaTranslationPolicy.legacyAnalysis());
    }
    var originalRuntime = System.getProperty("java.runtime.version");
    System.setProperty("java.runtime.version", originalRuntime + "-changed");
    try {
      // Act
      var actual = capture.capture(compiler, configuration, invocation.analysisOptions());
      var failure = assertThrows(IOException.class, () -> capture.verifyUnchanged(compiler, configuration, invocation.analysisOptions(), expected));

      // Assert
      assertEquals(expected.classPath(), actual.classPath());
      assertEquals(expected.modulePath(), actual.modulePath());
      assertNotEquals(expected, actual);
      assertEquals(originalRuntime + "-changed", actual.compilerVersion());
      assertEquals("Compiler dependencies changed during analysis", failure.getMessage());
    } finally {
      System.setProperty("java.runtime.version", originalRuntime);
    }
  }
}
