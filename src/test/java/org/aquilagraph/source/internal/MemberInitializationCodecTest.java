package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberInitializationCodecTest {
  @TempDir Path directory;

  @Test
  void givenWrittenInitializerAndExecutionScope_whenRetained_thenTheirProofAndQueryValuesSurvive() throws IOException {
    // Arrange
    var original = produce("class Example { int field = Math.abs(-1); { field++; } }", AstProfile.JAVA_SOURCE_INITIALIZATION);
    var bytes = AstCodec.encode(original);

    // Act
    var retained = AstCodec.decode(bytes, original.identity());
    var projection = AstCodec.materialize(retained);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(original.identity(), retained.identity());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertFalse(retained.coverage().validationEvidence().writtenInventory().writtenExpressions().orElseThrow().isEmpty());
    assertArrayEquals(bytes, AstCodec.encode(retained));
    assertTrue(projection.links().stream().anyMatch(link -> link.field().equals("scopes")));
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

}
