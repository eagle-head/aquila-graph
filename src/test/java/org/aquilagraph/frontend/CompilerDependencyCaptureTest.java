package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.model.DependencyKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilerDependencyCaptureTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenMissingFileEmptyAndOrderedDirectoryDependencies_whenCaptured_thenLiteralEightByteFramedFingerprintsArePreserved()
      throws IOException {
    // Arrange
    var missing = temporaryDirectory.resolve("missing.jar");
    var file = temporaryDirectory.resolve("raw.jar");
    Files.write(file, new byte[] {0, (byte) 0xff});
    var empty = Files.createDirectory(temporaryDirectory.resolve("empty"));
    var directory = Files.createDirectory(temporaryDirectory.resolve("directory"));
    Files.createDirectory(directory.resolve("a"));
    Files.write(directory.resolve("a/A.jar"), new byte[] {0, (byte) 0xff});
    Files.write(directory.resolve("B.jar"), new byte[0]);
    var locations = new ArrayList<>(List.of(missing, file, empty, directory, file));

    // Act
    var dependencies = new CompilerDependencyCapture().capture(locations);
    locations.clear();

    // Assert
    assertEquals(List.of(DependencyKind.MISSING, DependencyKind.REGULAR_FILE, DependencyKind.DIRECTORY, DependencyKind.DIRECTORY,
        DependencyKind.REGULAR_FILE), dependencies.stream().map(dependency -> dependency.kind()).toList());
    assertEquals(List.of(0L, 2L, 0L, 2L, 2L), dependencies.stream().map(dependency -> dependency.byteCount()).toList());
    assertEquals(List.of(0, 1, 0, 2, 1), dependencies.stream().map(dependency -> dependency.entryCount()).toList());
    assertEquals(List.of("sha256:1bfc398d69dbf589b45c5ca18c1800804bb3b2972179d7845caeeb211306d77a",
        "sha256:612eac982e79c10fd799c7814d2f50a5fdfeb4e0c0a38a4b8b421753d8f5c3de",
        "sha256:534fc0e5cf47792ff195ec69183f5e064860ace960056cd8f989ba095c0049ce",
        "sha256:82c6be1e7801230aa51f8a50d7b2a5238d3bc06f95894ef4d910d9259a003d8a",
        "sha256:612eac982e79c10fd799c7814d2f50a5fdfeb4e0c0a38a4b8b421753d8f5c3de"),
        dependencies.stream().map(dependency -> dependency.fingerprint()).toList());
    assertEquals(dependencies.get(1), dependencies.get(4));
    assertThrows(UnsupportedOperationException.class, dependencies::clear);
  }
}
