package org.aquilagraph.source.internal;

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
  void givenCompatiblePatternFormat_whenClosedValuesAreDecoded_thenOriginalIdentityAndBindingsRemainReadable() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { boolean subject(Object value) { return value instanceof String text && text.length() > 0; } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var previous = SourceAnalysisFixture.patternResult(current);
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(previous), previous.identity());
    // Assert
    assertEquals("java-source/10", decoded.identity().sourceModelVersion());
    assertEquals(previous.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
  }

  @TestFactory
  Stream<DynamicTest> givenExpandedSwitchValuesInOlderFrames_whenReadOrWritten_thenEveryCompatibleOldFrameRejectsNewValues() {
    return Stream.of("java-source/2", "java-source/3", "java-source/4", "java-source/5", "java-source/6", "java-source/7", "java-source/8", "java-source/9", "java-source/10")
        .flatMap(format -> List.of(SyntaxKind.SWITCH_STATEMENT, CoverageEvidenceValue.Tag.PATTERN_LABEL, new SyntaxValue.SwitchLabel.Pattern(0))
            .stream().map(value -> DynamicTest.dynamicTest(format + " " + value, () -> {
              // Arrange
              var codec = new RetainedValueCodec(false, format);
              var current = new CanonicalValueWriter(AstResult.SOURCE_MODEL_VERSION);
              new RetainedValueCodec(false).write(current, value);
              var old = new CanonicalValueWriter(format);
              // Act
              var write = assertThrows(IllegalArgumentException.class, () -> codec.write(old, value));
              var read = assertThrows(IllegalArgumentException.class, () -> codec.read(new CanonicalValueReader(current.toByteArray(), AstResult.SOURCE_MODEL_VERSION)));
              // Assert
              assertEquals(write.getMessage(), read.getMessage());
            })));
  }

  @TestFactory
  Stream<DynamicTest> givenExpandedCaseShapes_whenEncodedAgainstThePatternFrame_thenNewCaseFormsCannotMasqueradeAsOlderSyntax() {
    return Stream.of(
        "int subject(int value) { return switch (value) { case 1: yield 2; default: yield 3; }; }",
        "int subject(int value) { return switch (value) { case 1 + 2 -> 3; default -> 4; }; }",
        "int subject(String value) { return switch (value) { case null, default -> 3; }; }")
        .map(method -> DynamicTest.dynamicTest(method, () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { " + method + " }")));
          var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
          var selected = current.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE).findFirst().orElseThrow();
          var writer = new CanonicalValueWriter(AstResult.PATTERN_SOURCE_MODEL_VERSION);
          // Act
          var error = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false, AstResult.PATTERN_SOURCE_MODEL_VERSION).write(writer, selected));
          // Assert
          assertEquals("Expanded switch cases require retained AST format java-source/11", error.getMessage());
        }));
  }
}
