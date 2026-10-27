package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationCleanupTest {
  @TempDir Path directory;
  private final List<Path> markers = new ArrayList<>();

  @AfterEach
  void releaseControlledMarkers() {
    for (var marker : markers) {
      assertTrue(marker.toFile().setWritable(true));
    }
  }

  @Test
  void givenReadOnlyHardLinksInOwnedTrees_whenTheyAreCleared_thenOutsideBytesAndProtectionRemain() throws Exception {
    for (var scope : List.of("compilation/production/capture", "compilation/production/staged", "classes", "test-classes",
        "resource-staging/production", "resource-staging/tests")) {
      // Arrange
      var root = Files.createDirectories(directory.resolve(scope.replace('/', '-'))).toRealPath();
      var selected = Files.createDirectories(root.resolve("target").resolve(scope));
      var outside = root.resolve("outside-selected-tree.txt");
      Files.writeString(outside, "unrelated controlled bytes");
      markers.add(outside);
      var link = Files.createLink(selected.resolve("linked.txt"), outside);
      assertTrue(outside.toFile().setReadOnly());
      var before = protection(outside);
      assertTrue(Files.isSameFile(link, outside));

      // Act
      PreparationFiles.clear(root, selected);

      // Assert
      assertEquals("unrelated controlled bytes", Files.readString(outside));
      assertEquals(before, protection(outside));
      try (var files = Files.list(selected)) {
        assertEquals(List.of(), files.toList());
      }
    }
  }

  private static String protection(Path file) throws IOException {
    return BuildRequest.windows()
        ? "dos.readOnly=" + Files.readAttributes(file, DosFileAttributes.class).isReadOnly()
        : "posix.permissions=" + Files.getPosixFilePermissions(file).stream().sorted().toList();
  }

  @Test
  void givenResourceStorageWithUnrelatedOutput_whenOneBatchResets_thenOnlyItsStagingIsCleared() throws Exception {
    for (var operation : List.of("production-resources", "test-resources")) {
      // Arrange
      var root = Files.createDirectory(directory.resolve(operation)).toRealPath();
      var production = Files.createDirectories(root.resolve("target/resource-staging/production"));
      var tests = Files.createDirectories(root.resolve("target/resource-staging/tests"));
      Files.writeString(production.resolve("stale.txt"), "production");
      Files.writeString(tests.resolve("stale.txt"), "tests");
      var unrelated = root.resolve("target/unrelated.txt");
      Files.writeString(unrelated, "unrelated output");

      // Act
      CompilationCleanup.clear(root, operation);

      // Assert
      var selected = "production-resources".equals(operation) ? production : tests;
      var retained = selected.equals(production) ? tests : production;
      try (var files = Files.list(selected)) {
        assertEquals(List.of(), files.toList());
      }
      assertTrue(Files.isRegularFile(retained.resolve("stale.txt")));
      assertEquals("unrelated output", Files.readString(unrelated));
    }
  }

  @Test
  void givenImmutableCapturesAndCachedArtifacts_whenCleanResetsPreparation_thenOnlyPreparationStorageIsCleared() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var capture = Files.createDirectories(root.resolve("target/compilation/production/capture")).resolve("dependency.jar");
    Files.writeString(capture, "captured bytes");
    assertTrue(capture.toFile().setReadOnly());
    var resources = Files.createDirectories(root.resolve("target/resource-staging/production")).resolve("resource.txt");
    Files.writeString(resources, "effective resources");
    var artifact = Files.createDirectories(root.resolve("target/maven-repository")).resolve("dependency.jar");
    Files.writeString(artifact, "cached artifact");

    // Act
    CompilationCleanup.clear(root, "clean");

    // Assert
    for (var name : List.of("compilation", "resource-staging")) {
      try (var files = Files.list(root.resolve("target").resolve(name))) {
        assertEquals(List.of(), files.toList());
      }
    }
    assertEquals("cached artifact", Files.readString(artifact));
  }

  @Test
  void givenAnUnsupportedStorageOperation_whenCleanupIsRequested_thenItRefusesWithoutMutation() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var marker = Files.createDirectories(root.resolve("target/compilation")).resolve("preserved.txt");
    Files.writeString(marker, "preserved");

    // Act
    var failure = assertThrows(IOException.class, () -> CompilationCleanup.clear(root, "../unrelated"));

    // Assert
    assertTrue(failure.getMessage().contains("Unsupported compilation storage operation"));
    assertEquals("preserved", Files.readString(marker));
  }
}
