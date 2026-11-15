package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CapturedValueEncodingTest {
  @Test
  void givenAllDependencyKindsAcrossClassAndModulePaths_whenEnvironmentIsEncoded_thenExactMarkersAndMetadataAreRetained() {
    // Arrange
    var classPath = CapturedLocation.parse(Syntax.POSIX, "/libs/missing-library.jar");
    var modulePath = CapturedLocation.parse(Syntax.POSIX, "/libs/missing-module.jar");
    var directoryPath = CapturedLocation.parse(Syntax.POSIX, "/libs/classes");
    var filePath = CapturedLocation.parse(Syntax.POSIX, "/libs/module.jar");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classPath, directoryPath), List.of(modulePath, filePath),
        List.of());
    var classDependency = new DependencySnapshot(classPath, DependencyKind.MISSING, 0, 0, "missing-library-fingerprint",
        new DependencySemantics(DependencySemantics.State.CLASS_PATH, List.of()));
    var moduleDependency = new DependencySnapshot(modulePath, DependencyKind.MISSING, 0, 0, "missing-module-fingerprint",
        new DependencySemantics(DependencySemantics.State.MISSING, List.of()));
    var directoryDependency = new DependencySnapshot(directoryPath, DependencyKind.DIRECTORY, 123, 2, "directory-fingerprint",
        new DependencySemantics(DependencySemantics.State.CLASS_PATH, List.of()));
    var fileDependency = new DependencySnapshot(filePath, DependencyKind.REGULAR_FILE, 456, 1, "file-fingerprint",
        new DependencySemantics(DependencySemantics.State.MODULES, List.of()));
    var environment = new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "25", List.of(),
        List.of(classDependency, directoryDependency), List.of(moduleDependency, fileDependency));

    // Act
    var encoded = CapturedValueEncoding.encode(Optional.empty(), Optional.of(environment));

    // Assert
    var reader = new CanonicalValueReader(encoded, "captured-values/3");
    assertEquals("input", reader.text());
    assertFalse(reader.flag());
    assertEquals("environment", reader.text());
    assertTrue(reader.flag());
    assertEquals("SHA-256", reader.text());
    assertEquals(environment.environmentDigest(), reader.text());
    assertEquals("analysis-environment/2", reader.text());
    assertEquals(25, reader.integer());
    assertEquals(2, reader.count());
    assertPosixLibraryLocation(reader, "missing-library.jar");
    assertPosixLibraryLocation(reader, "classes");
    assertEquals(2, reader.count());
    assertPosixLibraryLocation(reader, "missing-module.jar");
    assertPosixLibraryLocation(reader, "module.jar");
    assertEquals(0, reader.count());
    assertEquals(configuration.canonicalForm(), reader.text());
    assertEquals("compiler", reader.text());
    assertEquals("vendor", reader.text());
    assertEquals("25", reader.text());
    assertEquals(0, reader.count());
    assertEquals("class-path", reader.text());
    assertEquals(2, reader.count());
    assertEquals("missing", reader.text());
    assertEquals(0L, reader.longInteger());
    assertEquals(0, reader.integer());
    assertEquals("missing-library-fingerprint", reader.text());
    assertEquals("CLASS_PATH", reader.text());
    assertEquals(0, reader.count());
    assertEquals("directory", reader.text());
    assertEquals(123L, reader.longInteger());
    assertEquals(2, reader.integer());
    assertEquals("directory-fingerprint", reader.text());
    assertEquals("CLASS_PATH", reader.text());
    assertEquals(0, reader.count());
    assertEquals("module-path", reader.text());
    assertEquals(2, reader.count());
    assertEquals("missing", reader.text());
    assertEquals(0L, reader.longInteger());
    assertEquals(0, reader.integer());
    assertEquals("missing-module-fingerprint", reader.text());
    assertEquals("MISSING", reader.text());
    assertEquals(0, reader.count());
    assertEquals("file", reader.text());
    assertEquals(456L, reader.longInteger());
    assertEquals(1, reader.integer());
    assertEquals("file-fingerprint", reader.text());
    assertEquals("MODULES", reader.text());
    assertEquals(0, reader.count());
    assertEquals("software", reader.text());
    assertFalse(reader.flag());
    assertEquals(0, reader.remaining());
  }
  private static void assertPosixLibraryLocation(CanonicalValueReader reader, String file) {
    assertEquals("POSIX", reader.text());
    assertEquals("POSIX_ABSOLUTE", reader.text());
    assertEquals(0, reader.count());
    assertEquals(2, reader.count());
    assertEquals("libs", reader.text());
    assertEquals(file, reader.text());
    assertEquals("/libs/" + file, reader.text());
  }
}
