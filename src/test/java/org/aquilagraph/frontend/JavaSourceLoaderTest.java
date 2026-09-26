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
  void givenMixedCaseAndNestedSourcePaths_whenDiscovered_thenCanonicalOrderIsIndependentOfNativePathComparison() throws IOException {
    // Arrange
    var expected = List.of("Sample.java", "a/B.java", "a0.java", "module-info.java", "package-info.java", "z.java", "É.java");
    for (var name : expected.reversed()) {
      var file = temporaryDirectory.resolve(name);
      Files.createDirectories(file.getParent());
      Files.writeString(file, "class Sample {}");
    }

    // Act
    var snapshot = new JavaSourceLoader().discover(temporaryDirectory);

    // Assert
    assertEquals(expected.stream().map(Path::of).toList(), snapshot.sourceFiles().stream().map(source -> source.relativePath()).toList());
  }

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
  void givenKnownMixedCaseSources_whenDiscoveredRepeatedly_thenCanonicalSnapshotRetainsExistingIdentities() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("package-info.java"), "package sample;");
    Files.writeString(temporaryDirectory.resolve("Sample.java"), "class Sample {}");
    var loader = new JavaSourceLoader();

    // Act
    var snapshot = loader.discover(temporaryDirectory);
    var repeated = loader.discover(temporaryDirectory);

    // Assert
    assertEquals(snapshot, repeated);
    assertEquals(List.of(Path.of("Sample.java"), Path.of("package-info.java")),
        snapshot.sourceFiles().stream().map(source -> source.relativePath()).toList());
    assertEquals(List.of("source:5b7d302b83ed255231899a484fac0ec5daf17b2311738c6a8d6eb46f7687faf7",
        "source:eb15457babaaed21bcf793195907afce837a598b5f4019297fd79d8eabe45ce4"),
        snapshot.sourceFiles().stream().map(source -> source.sourceFileKey()).toList());
    assertEquals("snapshot:59ea0f249b25412396bb902ec3fc0ab7cc48048f7a75d1484a86288fe005fa14", snapshot.snapshotDigest());
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
