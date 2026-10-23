package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaSourceTraversalFailureTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenValidRootWhoseWalkOpenFails_whenSourcesAreDiscovered_thenOriginalCheckedFailureHasNoAcquiredDirectoryHandle() throws IOException {
    // Arrange
    var root = Files.createDirectory(temporaryDirectory.resolve("sources"));
    var openFailure = new IOException("Source walk open failed");
    try (var observation = new DependencyFileSystemObservation(root)) {
      observation.directoryOpenFailure = openFailure;

      // Act
      var failure = assertThrows(IOException.class, () -> new JavaSourceLoader().discover(observation.observe(root)));
      Files.delete(root);

      // Assert
      assertSame(openFailure, failure);
      assertEquals(1, observation.directoryOpenAttempts);
      assertEquals(0, observation.directoryCloseAttempts);
      assertFalse(Files.exists(root));
    }
  }

  @Test
  void givenOpenedSourceWalkFailsDuringLazyIteration_whenLoaderAndPathEntryRun_thenUncheckedFailureIsNotTranslatedAndEachWalkCloses() throws IOException {
    // Arrange
    var root = Files.createDirectory(temporaryDirectory.resolve("sources"));
    var source = Files.writeString(root.resolve("Sample.java"), "class Sample {}");
    var iterationFailure = new IOException("Source walk iteration failed");
    try (var observation = new DependencyFileSystemObservation(root)) {
      observation.directoryIterationFailure = iterationFailure;
      var observedRoot = observation.observe(root);
      var frontend = new JavaFrontend(new JavaSourceLoader());
      var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

      // Act
      var loaderFailure = assertThrows(UncheckedIOException.class, () -> new JavaSourceLoader().discover(observedRoot));
      var pathFailure = assertThrows(UncheckedIOException.class, () -> new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(observedRoot), configuration, JavaTranslationPolicy.legacyAnalysis()));
      Files.delete(source);
      Files.delete(root);

      // Assert
      assertSame(iterationFailure, loaderFailure.getCause());
      assertSame(iterationFailure, pathFailure.getCause());
      assertEquals(2, observation.directoryOpenAttempts);
      assertEquals(2, observation.directoryCloseAttempts);
      assertEquals(0, observation.inputOpenAttempts);
      assertFalse(Files.exists(root));
    }
  }

  @Test
  void givenMalformedRealSourceAfterSuccessfulTraversal_whenSourcesAreDiscovered_thenReadFailureNamesTheFileAndTheWalkCloses() throws IOException {
    // Arrange
    var root = Files.createDirectory(temporaryDirectory.resolve("sources"));
    var source = Files.write(root.resolve("Sample.java"), new byte[] {(byte) 0xc3, 0x28});
    try (var observation = new DependencyFileSystemObservation(root)) {
      // Act
      var failure = assertThrows(IOException.class, () -> new JavaSourceLoader().discover(observation.observe(root)));
      Files.delete(source);
      Files.delete(root);

      // Assert
      assertEquals("Cannot read Java source: " + source, failure.getMessage());
      assertInstanceOf(MalformedInputException.class, failure.getCause());
      assertEquals(1, observation.directoryOpenAttempts);
      assertEquals(1, observation.directoryCloseAttempts);
      assertFalse(Files.exists(root));
    }
  }
}
