package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutableLiteralInterpretationTest {
  private static final String SOURCE = """
      @interface Note {
        String value() default "a" + "b";
        char marker() default '\\n';
        int radix() default 0x10;
      }
      @Note("plain\\t")
      class Example {
        String subject() { @Note("c" + "d") String local = "e" + "f"; return local + "g"; }
        String direct() { return "plain\\t"; }
        char marker() { return '\\n'; }
        int radix() { return 0x10; }
        int minimum() { return -2147483648; }
      }
      """;

  @TempDir Path directory;

  @Test
  void givenAnnotationAndExecutionLiteralFamilies_whenQueriedAfterCompilerAndCodecClosure_thenOnlyExecutionRuntimeTextIsInterpreted() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), SOURCE)));
    var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_FIDELITY);
    var original = new JavaFrontend(new JavaSourceLoader()).produce(request).result().orElseThrow();
    var encoded = AstCodec.encode(original);

    // Act
    var decoded = AstCodec.decode(encoded, original.identity());

    // Assert
    for (var ast : List.of(original, decoded)) {
      assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
      var annotationValues = Stream.concat(ast.aggregate().nodes().stream().flatMap(node -> node.children(SyntaxRole.DEFAULT_VALUE).stream()),
          ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT)
              .map(node -> node.requiredChild(SyntaxRole.VALUE))).flatMap(node -> node.descendantsAndSelf().stream()).toList();
      assertFalse(annotationValues.isEmpty());
      assertEquals(List.of("\"a\"", "\"b\"", "'\\n'", "0x10", "\"plain\\t\"", "\"c\"", "\"d\"").stream().sorted().toList(),
          annotationValues.stream().filter(node -> node.value() instanceof SyntaxValue.Literal)
              .map(node -> node.value(SyntaxValue.Literal.class).text()).sorted().toList());
      for (var node : annotationValues) {
        assertEquals(Optional.empty(), ast.aggregate().executableLiteralText(node.nodeId()), node.value().toString());
      }
      assertEquals(Optional.of("plain\t"), ast.aggregate().executableLiteralText(methodLiteral(ast, "direct").nodeId()));
      assertEquals(Optional.of("\n"), ast.aggregate().executableLiteralText(methodLiteral(ast, "marker").nodeId()));
      assertEquals(Optional.of("16"), ast.aggregate().executableLiteralText(methodLiteral(ast, "radix").nodeId()));
      assertEquals(Optional.empty(), ast.aggregate().executableLiteralText(methodLiteral(ast, "minimum").nodeId()));
      var normalized = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY)
          .filter(node -> node.origin().map(span -> SOURCE.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())))
              .filter("\"e\" + \"f\""::equals).isPresent()).findFirst().orElseThrow();
      assertEquals(Optional.of("ef"), ast.aggregate().executableLiteralText(normalized.nodeId()));
      assertEquals(Optional.empty(), ast.aggregate().executableLiteralText("absent-occurrence"));
      assertArrayEquals(encoded, AstCodec.encode(ast));
    }
  }

  private static SyntaxNode methodLiteral(AstResult ast, String name) {
    var method = ast.program().methods().stream().filter(value -> value.name().equals(name) && value.hasBody()).findFirst().orElseThrow();
    return ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).findFirst().orElseThrow();
  }
}
