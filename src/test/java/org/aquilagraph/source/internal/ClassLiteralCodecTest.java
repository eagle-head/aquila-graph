package org.aquilagraph.source.internal;

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
import org.aquilagraph.source.api.SyntaxKind;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ClassLiteralCodecTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenExecutableClassLiteral_whenOlderSchemasReadOrWrite_thenTheNewKindCannotMasqueradeAsOlderSyntax() {
    return IntStream.rangeClosed(2, 12).mapToObj(version -> DynamicTest.dynamicTest("java-source/" + version, () -> {
      // Arrange
      var result = produce("Class<?> subject() { return int[].class; }");
      var literal = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL).findFirst().orElseThrow();
      var format = "java-source/" + version;
      var previous = new RetainedValueCodec(false, format);
      var current = new CanonicalValueWriter(AstResult.SOURCE_MODEL_VERSION);
      new RetainedValueCodec(false).write(current, literal);

      // Act
      var write = assertThrows(IllegalArgumentException.class, () -> previous.write(new CanonicalValueWriter(format), literal));
      var read = assertThrows(IllegalArgumentException.class,
          () -> previous.read(new CanonicalValueReader(current.toByteArray(), AstResult.SOURCE_MODEL_VERSION)));

      // Assert
      assertEquals("Executable class literals require retained AST format java-source/13", write.getMessage());
      assertEquals(write.getMessage(), read.getMessage());
      assertEquals(AstCompletion.COMPLETE, result.completion());
    }));
  }

  @Test
  void givenCompatibleExceptionSource_whenRetainedWithItsOriginalSchema_thenIdentityAndQualificationRemainUnchanged() throws IOException {
    // Arrange
    var current = produce("int subject(int value) { try { value += 1; } finally { value += 2; } return value; }");
    var previous = SourceAnalysisFixture.exceptionResult(current);

    // Act
    var retained = AstCodec.decode(AstCodec.encode(previous), previous.identity());

    // Assert
    assertEquals("java-source/12", retained.identity().sourceModelVersion());
    assertEquals(previous.identity(), retained.identity());
    assertEquals(current.aggregate(), retained.aggregate());
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals("java-language-feature-coverage:f39568916d81bfafc9f3300c9d8cb9a69aacf1073874cea3855a628d77d5f54c",
        retained.coverage().coverage().contractIdentity());
  }

  private AstResult produce(String method) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { " + method + " }")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
  }
}
