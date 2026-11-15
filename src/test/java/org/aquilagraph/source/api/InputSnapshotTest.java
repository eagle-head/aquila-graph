package org.aquilagraph.source.api;

import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.junit.jupiter.api.Test;

class InputSnapshotTest {
  @Test
  void givenSourcesInDifferentEncounterOrders_whenCaptured_thenCanonicalOrderAndIdentityAreEqual() {
    // Arrange
    var names = List.of("Sample.java", "a/B.java", "a0.java", "module-info.java", "package-info.java", "sample.java", "É.java");
    var sources = names.stream().map(name -> SourceFile.fromContent(capturedPath(Path.of(name)), "class Sample {}")).toList();
    var input = new ArrayList<>(sources.reversed());

    // Act
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), input);
    var repeated = InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), sources);

    // Assert
    assertEquals(names, snapshot.sourceFiles().stream().map(SourceFile::canonicalRelativePath).toList());
    assertEquals(snapshot, repeated);
    assertEquals(sources.reversed(), input);
    input.clear();
    assertEquals(sources, snapshot.sourceFiles());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.sourceFiles().clear());
  }

  @Test
  void givenKnownContentAndAnotherCheckoutRoot_whenCaptured_thenExistingSnapshotDigestIsPreserved() {
    // Arrange
    var sources = List.of(SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}"),
        SourceFile.fromContent(capturedPath(Path.of("package-info.java")), "package sample;"));

    // Act
    var original = InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), sources);
    var relocated = InputSnapshot.fromSources(capturedAbsolute(Path.of("another-checkout")), sources.reversed());

    // Assert
    assertEquals("SHA-256", original.digestAlgorithm());
    assertEquals("snapshot:59ea0f249b25412396bb902ec3fc0ab7cc48048f7a75d1484a86288fe005fa14", original.snapshotDigest());
    assertEquals(original.snapshotDigest(), relocated.snapshotDigest());
    assertEquals(original.sourceFiles(), relocated.sourceFiles());
    assertNotEquals(original.sourceRoot(), relocated.sourceRoot());
  }

  @Test
  void givenCanonicalAndReversedSources_whenConstructedDirectly_thenOnlyCanonicalOrderIsAccepted() {
    // Arrange
    var sources = List.of(SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}"),
        SourceFile.fromContent(capturedPath(Path.of("package-info.java")), "package sample;"));
    var input = new ArrayList<>(sources);

    // Act
    var snapshot = new InputSnapshot(capturedAbsolute(Path.of("sources")), "SHA-256", "captured-digest", input);
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new InputSnapshot(capturedAbsolute(Path.of("sources")), "SHA-256", "captured-digest", sources.reversed()));

    // Assert
    assertEquals(sources, snapshot.sourceFiles());
    assertEquals("Source files must have deterministic relative-path order", failure.getMessage());
    input.clear();
    assertEquals(sources, snapshot.sourceFiles());
  }

  @Test
  void givenDuplicateCanonicalPathsWithDifferentKeys_whenCaptured_thenAmbiguousSourceOwnershipIsRejected() {
    // Arrange
    var first = SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}");
    var second = SourceFile.fromContent(capturedPath(Path.of("nested/../Sample.java")), "class Sample { int value; }");

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), List.of(first, second)));

    // Assert
    assertNotEquals(first.sourceFileKey(), second.sourceFileKey());
    assertEquals("Source files must have deterministic relative-path order", failure.getMessage());
  }

  @Test
  void givenDuplicateKeysWithDifferentPaths_whenCaptured_thenDuplicateSourceIdentityIsRejected() {
    // Arrange
    var first = SourceFile.fromContent(capturedPath(Path.of("First.java")), "class First {}");
    var second = new SourceFile(first.sourceFileKey(), capturedPath(Path.of("Second.java")), first.contentDigest(), first.content());

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), List.of(first, second)));

    // Assert
    assertEquals("Source-file keys must be unique", failure.getMessage());
  }

  @Test
  void givenEmptyAndSingletonSourceSets_whenCaptured_thenBoundarySnapshotsRemainValid() {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}");

    // Act
    var empty = InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), List.of());
    var singleton = InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), List.of(source));

    // Assert
    assertEquals(0, empty.sourceFileCount());
    assertEquals("snapshot:a78836d7d13c349521257fc078212ff6c9cda3bbffe0e66ac08c113da6b6de2a", empty.snapshotDigest());
    assertEquals(List.of(source), singleton.sourceFiles());
    assertEquals(1, singleton.sourceFileCount());
    assertNotEquals(empty.snapshotDigest(), singleton.snapshotDigest());
  }

  @Test
  void givenChangedSourceContentOrPath_whenCaptured_thenSnapshotIdentityChanges() {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}");
    var original = InputSnapshot.fromSources(capturedAbsolute(Path.of("sources")), List.of(source));

    // Act
    var edited = InputSnapshot.fromSources(original.sourceRoot(),
        List.of(SourceFile.fromContent(source.relativePath(), "class Sample { int value; }")));
    var renamed = InputSnapshot.fromSources(original.sourceRoot(),
        List.of(SourceFile.fromContent(capturedPath(Path.of("sample.java")), source.content())));

    // Assert
    assertNotEquals(original.snapshotDigest(), edited.snapshotDigest());
    assertNotEquals(original.snapshotDigest(), renamed.snapshotDigest());
  }
}
