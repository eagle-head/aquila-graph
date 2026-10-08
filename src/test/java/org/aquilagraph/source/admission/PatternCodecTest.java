package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.io.IOException;
import java.nio.file.Path;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatternCodecTest {
  @TempDir Path directory;

  @Test
  void givenCompatibleReferenceFormat_whenClosedValuesAreDecoded_thenOriginalIdentityAndReferenceStructureRemainReadable() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { java.util.function.IntFunction<int[]> subject() { return int[]::new; } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var previous = SourceAnalysisFixture.referenceResult(current);
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(previous), previous.identity());
    // Assert
    assertEquals("java-source/9", decoded.identity().sourceModelVersion());
    assertEquals(previous.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
  }

  @TestFactory
  Stream<DynamicTest> givenPatternValuesInOlderFormats_whenReadOrWritten_thenEveryOldFrameRejectsNewSyntaxAndScope() {
    return Stream.of("java-source/2", "java-source/3", "java-source/4", "java-source/5", "java-source/6", "java-source/7", "java-source/8", "java-source/9")
        .flatMap(format -> List.of(SyntaxKind.INSTANCE_OF, SyntaxKind.BINDING_PATTERN, SyntaxKind.DECONSTRUCTION_PATTERN, SyntaxKind.ANY_PATTERN,
            SyntaxRole.PATTERN, SyntaxRole.PATTERN_COMPONENT, CoverageEvidenceValue.Tag.PATTERN_BINDING, CoverageEvidenceValue.Tag.INFERRED_RECORD_TYPE,
            new SyntaxValue.PatternBinding(Optional.of(List.of())), new SyntaxValue.InferredRecordType("Box"))
            .stream().map(value -> DynamicTest.dynamicTest(format + " " + value, () -> {
              // Arrange
              var codec = new RetainedValueCodec(false, format);
              var current = new CanonicalValueWriter("java-source/10");
              new RetainedValueCodec(false, "java-source/10").write(current, value);
              var old = new CanonicalValueWriter(format);
              // Act
              var write = assertThrows(IllegalArgumentException.class, () -> codec.write(old, value));
              var read = assertThrows(IllegalArgumentException.class, () -> codec.read(new CanonicalValueReader(current.toByteArray(), "java-source/10")));
              // Assert
              assertEquals(write.getMessage(), read.getMessage());
            })));
  }
}
