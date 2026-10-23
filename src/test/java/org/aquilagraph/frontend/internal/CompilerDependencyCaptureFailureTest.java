package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilerDependencyCaptureFailureTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenAnExistingEntryWithUnsupportedProviderKind_whenDependencyIsCaptured_thenNamedFailureHasNoContentAcquisition() throws IOException {
    // Arrange
    var file = Files.write(temporaryDirectory.resolve("entry.bin"), new byte[] {0, (byte) 0xff});
    try (var observation = new DependencyFileSystemObservation(file)) {
      observation.reportOtherEntry = true;
      var location = observation.observe(file);

      // Act
      var failure = assertThrows(IOException.class, () -> new CompilerDependencyCapture().capture(List.of(location)));
      Files.delete(file);

      // Assert
      assertEquals("Unsupported compiler dependency entry: " + file, failure.getMessage());
      assertEquals(0, observation.inputOpenAttempts);
      assertEquals(0, observation.directoryOpenAttempts);
      assertFalse(Files.exists(file));
    }
  }

  @Test
  void givenContentReadAndCloseBothFail_whenDependencyIsCaptured_thenReadRemainsPrimaryAndOwnedStreamIsClosedBeforeRemoval() throws IOException {
    // Arrange
    var file = Files.write(temporaryDirectory.resolve("entry.bin"), new byte[] {0, (byte) 0xff});
    var readFailure = new IOException("Content read failed");
    var closeFailure = new IOException("Content close failed");
    try (var observation = new DependencyFileSystemObservation(file)) {
      observation.inputReadFailureAttempt = 1;
      observation.inputReadFailure = readFailure;
      observation.inputCloseFailureAttempt = 1;
      observation.inputCloseFailure = closeFailure;

      // Act
      var failure = assertThrows(IOException.class, () -> new CompilerDependencyCapture().capture(List.of(observation.observe(file))));
      Files.delete(file);

      // Assert
      assertSame(readFailure, failure);
      assertArrayEquals(new Throwable[] {closeFailure}, failure.getSuppressed());
      assertEquals(1, observation.inputOpenAttempts);
      assertEquals(1, observation.inputCloseAttempts);
      assertFalse(Files.exists(file));
    }
  }

  @Test
  void givenContentOpenFailsBeforeReturningAHandle_whenDependencyIsCaptured_thenOriginalFailureHasNoOwnedStreamClose() throws IOException {
    // Arrange
    var file = Files.write(temporaryDirectory.resolve("entry.bin"), new byte[] {0});
    var openFailure = new IOException("Content open failed");
    try (var observation = new DependencyFileSystemObservation(file)) {
      observation.inputOpenFailureAttempt = 1;
      observation.inputOpenFailure = openFailure;

      // Act
      var failure = assertThrows(IOException.class, () -> new CompilerDependencyCapture().capture(List.of(observation.observe(file))));
      Files.delete(file);

      // Assert
      assertSame(openFailure, failure);
      assertEquals(1, observation.inputOpenAttempts);
      assertEquals(0, observation.inputCloseAttempts);
      assertEquals(0, failure.getSuppressed().length);
      assertFalse(Files.exists(file));
    }
  }

  @Test
  void givenContentReadSucceedsButCloseFails_whenDependencyIsCaptured_thenCloseFailurePreventsFingerprintReturn() throws IOException {
    // Arrange
    var file = Files.write(temporaryDirectory.resolve("entry.bin"), new byte[] {0, (byte) 0xff});
    var closeFailure = new IOException("Content close failed");
    try (var observation = new DependencyFileSystemObservation(file)) {
      observation.inputCloseFailureAttempt = 1;
      observation.inputCloseFailure = closeFailure;

      // Act
      var failure = assertThrows(IOException.class, () -> new CompilerDependencyCapture().capture(List.of(observation.observe(file))));
      Files.delete(file);

      // Assert
      assertSame(closeFailure, failure);
      assertEquals(1, observation.inputCloseAttempts);
      assertEquals(0, failure.getSuppressed().length);
      assertFalse(Files.exists(file));
    }
  }

  @Test
  void givenProviderSizeDiffersFromBytesReadForFileOrDirectory_whenDependencyIsCaptured_thenSizeMismatchRejectsTheWholeValue() throws IOException {
    for (var directoryEntry : List.of(false, true)) {
      // Arrange
      var root = Files.createDirectory(temporaryDirectory.resolve(directoryEntry ? "directory-case" : "file-case"));
      var file = Files.write(root.resolve("entry.bin"), new byte[] {0, (byte) 0xff});
      var location = directoryEntry ? root : file;
      try (var observation = new DependencyFileSystemObservation(location)) {
        observation.reportedSizeOffset = 1;

        // Act
        var failure = assertThrows(IOException.class, () -> new CompilerDependencyCapture().capture(List.of(observation.observe(location))));
        Files.delete(file);
        Files.delete(root);

        // Assert
        assertEquals("Compiler dependency changed while being captured: " + file, failure.getMessage());
        assertEquals(1, observation.inputOpenAttempts);
        assertEquals(1, observation.inputCloseAttempts);
        assertEquals(directoryEntry ? 1 : 0, observation.directoryCloseAttempts);
        assertFalse(Files.exists(root));
      }
    }
  }

  @Test
  void givenDirectoryWalkCannotOpen_whenDependencyIsCaptured_thenCheckedFailureRetainsTheNoHandleBoundary() throws IOException {
    // Arrange
    var directory = Files.createDirectory(temporaryDirectory.resolve("entry"));
    var openFailure = new IOException("Directory open failed");
    try (var observation = new DependencyFileSystemObservation(directory)) {
      observation.directoryOpenFailure = openFailure;

      // Act
      var failure = assertThrows(IOException.class, () -> new CompilerDependencyCapture().capture(List.of(observation.observe(directory))));
      Files.delete(directory);

      // Assert
      assertSame(openFailure, failure);
      assertEquals(1, observation.directoryOpenAttempts);
      assertEquals(0, observation.directoryCloseAttempts);
      assertEquals(0, observation.inputOpenAttempts);
      assertFalse(Files.exists(directory));
    }
  }

  @Test
  void givenOpenedDirectoryFailsDuringLazyIteration_whenDependencyIsCaptured_thenUncheckedTraversalIsTranslatedAndDirectoryIsClosed() throws IOException {
    // Arrange
    var directory = Files.createDirectory(temporaryDirectory.resolve("entry"));
    var file = Files.write(directory.resolve("data.bin"), new byte[] {0});
    var iterationFailure = new IOException("Directory iteration failed");
    try (var observation = new DependencyFileSystemObservation(directory)) {
      observation.directoryIterationFailure = iterationFailure;

      // Act
      var failure = assertThrows(IOException.class, () -> new CompilerDependencyCapture().capture(List.of(observation.observe(directory))));
      Files.delete(file);
      Files.delete(directory);

      // Assert
      assertEquals("Failed to enumerate compiler dependency directory: " + directory, failure.getMessage());
      var unchecked = assertInstanceOf(UncheckedIOException.class, failure.getCause());
      assertSame(iterationFailure, unchecked.getCause());
      assertEquals(1, observation.directoryOpenAttempts);
      assertEquals(1, observation.directoryCloseAttempts);
      assertEquals(0, observation.inputOpenAttempts);
      assertFalse(Files.exists(directory));
    }
  }

  @Test
  void givenNullLocationListOrEntry_whenDependenciesAreCaptured_thenRequiredArgumentsRejectNullBeforeProviderWork() {
    // Arrange
    var capture = new CompilerDependencyCapture();

    // Act
    var nullList = assertThrows(NullPointerException.class, () -> capture.capture(null));
    var nullEntry = assertThrows(NullPointerException.class, () -> capture.capture(Collections.singletonList(null)));

    // Assert
    assertEquals("locations", nullList.getMessage());
    assertEquals("location", nullEntry.getMessage());
  }
}
