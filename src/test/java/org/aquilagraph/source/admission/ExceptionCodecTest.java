package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
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
  void givenCompatibleSwitchFrame_whenDecoded_thenOriginalIdentityAndClosedSwitchValuesRemainReadable() throws IOException {
    // Arrange
    var current = produce("int subject(int value) { return switch(value) { case 1: yield 2; default: yield 3; }; }");
    var previous = SourceAnalysisFixture.switchResult(current);
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(previous), previous.identity());
    // Assert
    assertEquals("java-source/11", decoded.identity().sourceModelVersion());
    assertEquals(previous.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals("java-language-feature-coverage:6c67a26fcdbe5f3d7fe6302796e917a38886a348f5619c98817ac3c4958d9bf4",
        decoded.coverage().coverage().contractIdentity());
  }

  @TestFactory
  Stream<DynamicTest> givenExpandedExceptionNodes_whenOlderFramesReadOrWrite_thenNewClausesCannotMasqueradeAsOlderSyntax() {
    return Stream.of("void subject() { try {} finally {} }", "void subject() throws Exception { try (AutoCloseable resource = null) {} }",
        "void subject() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} }")
        .flatMap(method -> IntStream.rangeClosed(2, 11).mapToObj(version -> DynamicTest.dynamicTest("java-source/" + version + " " + method, () -> {
          // Arrange
          var result = produce(method);
          var node = result.aggregate().nodes().stream().filter(value -> method.contains(" | ")
              ? value.sourceKind() == JavaSourceKind.UNION_TYPE : value.syntaxKind() == SyntaxKind.TRY).findFirst().orElseThrow();
          var format = "java-source/" + version;
          var codec = new RetainedValueCodec(false, format);
          var sourceEnvelope = new CanonicalValueWriter(AstResult.SOURCE_ENVELOPE_MODEL_VERSION);
          new RetainedValueCodec(false, AstResult.SOURCE_ENVELOPE_MODEL_VERSION).write(sourceEnvelope, node);
          // Act
          var write = assertThrows(IllegalArgumentException.class, () -> codec.write(new CanonicalValueWriter(format), node));
          var read = assertThrows(IllegalArgumentException.class,
              () -> codec.read(new CanonicalValueReader(sourceEnvelope.toByteArray(), AstResult.SOURCE_ENVELOPE_MODEL_VERSION)));
          // Assert
          assertEquals("Expanded exception syntax requires retained AST format java-source/12", write.getMessage());
          assertEquals(version == 2 && method.contains("try (")
              ? "Copied binding values require the current retained AST format: syntax-value/local-binding"
              : write.getMessage(), read.getMessage());
          assertEquals(AstCompletion.COMPLETE, result.completion());
        })));
  }

  private AstResult produce(String method) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { " + method + " }")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
  }
}
