package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class SwitchCodecTest {
  @TempDir Path directory;

  @Test
  void givenPatternBindings_whenRetained_thenExactIdentityAndBindingsRemainReadable() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example { boolean subject(Object value) { return value instanceof String text && text.length() > 0; } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(current), current.identity());
    // Assert
    assertEquals("java-source/23", decoded.identity().sourceModelVersion());
    assertEquals(current.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
  }

  @TestFactory
  Stream<DynamicTest> givenExpandedCaseShapes_whenRetained_thenEachCaseKeepsItsExactValue() {
    return Stream.of(
        "int subject(int value) { return switch (value) { case 1: yield 2; default: yield 3; }; }",
        "int subject(int value) { return switch (value) { case 1 + 2 -> 3; default -> 4; }; }",
        "int subject(String value) { return switch (value) { case null, default -> 3; }; }")
        .map(method -> DynamicTest.dynamicTest(method, () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example { " + method + " }")));
          var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
          var selected = current.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE).findFirst().orElseThrow();
          // Act
          var restored = AstCodec.decode(AstCodec.encode(current), current.identity());
          // Assert
          assertEquals(selected, restored.aggregate().node(selected.nodeId()).orElseThrow());
        }));
  }
}
