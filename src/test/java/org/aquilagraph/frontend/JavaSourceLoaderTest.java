package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceLoaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenMixedSourceTree_whenSourcesAreDiscovered_thenSnapshotIsOrderedAndContentAddressed()
      throws IOException {
    // Arrange
    var packageDirectory = Files.createDirectories(temporaryDirectory.resolve("example"));
    Files.writeString(packageDirectory.resolve("Second.java"), "class Second {}");
    Files.writeString(packageDirectory.resolve("First.java"), "class First {}");
    Files.writeString(packageDirectory.resolve("notes.txt"), "not Java source");
    var loader = new JavaSourceLoader();

    // Act
    var firstSnapshot = loader.discover(temporaryDirectory);
    Files.writeString(
        packageDirectory.resolve("Second.java"),
        System.lineSeparator(),
        StandardOpenOption.APPEND);
    var changedSnapshot = loader.discover(temporaryDirectory);

    // Assert
    assertEquals(
        List.of(Path.of("example/First.java"), Path.of("example/Second.java")),
        firstSnapshot.sourceFiles().stream().map(source -> source.relativePath()).toList());
    assertNotEquals(firstSnapshot.snapshotDigest(), changedSnapshot.snapshotDigest());
    assertEquals(2, firstSnapshot.sourceFileCount());
    assertThrows(
        UnsupportedOperationException.class,
        () -> firstSnapshot.sourceFiles().add(firstSnapshot.sourceFiles().getFirst()));
  }

  @Test
  void givenMissingSourceRoot_whenSourcesAreDiscovered_thenInputIsRejected() {
    // Arrange
    var missingRoot = temporaryDirectory.resolve("missing");
    var loader = new JavaSourceLoader();

    // Act
    var exception =
        assertThrows(IllegalArgumentException.class, () -> loader.discover(missingRoot));

    // Assert
    assertEquals(
        "Source root is not a directory: " + missingRoot.toAbsolutePath().normalize(),
        exception.getMessage());
  }

  @Test
  void givenMalformedUtf8Source_whenSourcesAreDiscovered_thenDecodingFailureIsPropagated()
      throws IOException {
    // Arrange
    Files.write(
        temporaryDirectory.resolve("Malformed.java"), new byte[] {(byte) 0xC3, (byte) 0x28});
    var loader = new JavaSourceLoader();

    // Act
    var exception =
        assertThrows(IOException.class, () -> loader.discover(temporaryDirectory));

    // Assert
    assertInstanceOf(MalformedInputException.class, exception.getCause());
  }
}
