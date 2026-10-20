package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CapturedValueEncodingTest {
  @Test
  void givenMissingClassPathAndModulePathDependencies_whenEnvironmentIsEncoded_thenMissingMarkersAndMetadataAreRetained() {
    // Arrange
    var classPath = Path.of("missing-library.jar").toAbsolutePath().normalize();
    var modulePath = Path.of("missing-module.jar").toAbsolutePath().normalize();
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classPath), List.of(modulePath), List.of());
    var classDependency = new DependencySnapshot(classPath, DependencyKind.MISSING, 0, 0, "missing-library-fingerprint");
    var moduleDependency = new DependencySnapshot(modulePath, DependencyKind.MISSING, 0, 0, "missing-module-fingerprint");
    var environment = new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "25", List.of(), List.of(classDependency),
        List.of(moduleDependency));

    // Act
    var encoded = CapturedValueEncoding.encode(Optional.empty(), Optional.of(environment));

    // Assert
    var reader = new CanonicalValueReader(encoded, "captured-values/1");
    assertEquals("input", reader.text());
    assertFalse(reader.flag());
    assertEquals("environment", reader.text());
    assertTrue(reader.flag());
    assertEquals("SHA-256", reader.text());
    assertEquals(environment.environmentDigest(), reader.text());
    assertEquals(25, reader.integer());
    assertEquals(1, reader.count());
    assertEquals(classPath.toString(), reader.text());
    assertEquals(1, reader.count());
    assertEquals(modulePath.toString(), reader.text());
    assertEquals(0, reader.count());
    assertEquals(configuration.canonicalForm(), reader.text());
    assertEquals("compiler", reader.text());
    assertEquals("vendor", reader.text());
    assertEquals("25", reader.text());
    assertEquals(0, reader.count());
    assertEquals("class-path", reader.text());
    assertEquals(1, reader.count());
    assertEquals("missing", reader.text());
    assertEquals(0L, reader.longInteger());
    assertEquals(0, reader.integer());
    assertEquals("missing-library-fingerprint", reader.text());
    assertEquals("module-path", reader.text());
    assertEquals(1, reader.count());
    assertEquals("missing", reader.text());
    assertEquals(0L, reader.longInteger());
    assertEquals(0, reader.integer());
    assertEquals("missing-module-fingerprint", reader.text());
    assertEquals("software", reader.text());
    assertFalse(reader.flag());
    assertEquals(0, reader.remaining());
  }
}
