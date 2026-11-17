package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ExceptionCodecTest {
  @TempDir Path directory;

  @Test
  void givenTryWithFinally_whenRetained_thenTheExceptionSyntaxAndQualificationRemain() throws IOException {
    // Arrange
    var original = produce("void subject() { try {} finally {} }");
    var bytes = AstCodec.encode(original);

    // Act
    var retained = AstCodec.decode(bytes, original.identity());

    // Assert
    assertEquals("java-source/24", retained.identity().sourceModelVersion());
    assertEquals(original.identity(), retained.identity());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(1, retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TRY).count());
  }

  private AstResult produce(String method) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example { " + method + " }")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
  }
}
