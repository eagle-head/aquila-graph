package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

final class DeclarationAstRetentionFixture {
  private DeclarationAstRetentionFixture() {}

  static AstOutcome produce(Path root, String filename, String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(root,
        List.of(SourceFile.fromContent(Path.of(filename), source))), configuration(), AstProfile.JAVA_SOURCE_UNITS);
  }

  static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }

  static AstOutcome mixed(Path root) throws IOException {
    return produce(root, "Stored.java", """
        import java.io.IOException;
        import java.io.StringReader;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.function.IntUnaryOperator;
        import java.util.function.Supplier;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Mark { int value() default 3; }
        class Stored {
          ; @Mark int first = Math.abs(-1), second[] = new int[]{2, 3};
          Supplier<Supplier<int[]>> chain = () -> () -> new int[]{4, 5};
          Object nested = new Object() {
            int value = 6;
            int read() { return value; }
          };
          static { int[] values = {7}; synchronized(values) { assert values.length > 0 : "values"; } }
          { first++; }
          Stored() { class Local { int value = 8; } new Local(); }
          int supported() { return 9; }
          int subject(Object input) {
            class Local { int[] values = {10, 11}; }
            IntUnaryOperator operation = Math::abs;
            int sum = 0;
            outer: for (int index = 0; index < 2; index++) { sum += index; if (index == 0) continue outer; break outer; }
            while (sum < 2) { ++sum; }
            do { sum--; } while (sum > 3);
            for (int value : new Local().values) { sum ^= value; }
            try (var reader = new StringReader("x")) {
              int selected = switch(input) { case String text when !text.isEmpty() -> text.length(); case null, default -> 0; };
              if (input instanceof String text) { selected += text.length(); }
              return operation.applyAsInt(selected + reader.read());
            } catch (IOException | IllegalArgumentException failure) { return 0; }
            finally { first++; }
          }
          enum Choice {
            FIRST(Math.abs(-12)) { int value = 13; { value++; } int read() { return value; } }, SECOND();
            Choice(int... values) {}
          }
          record Part(int value) { Part {} }
        }
        """);
  }

  static void assertPreserved(AstResult original, AstResult retained) {
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(original.identity(), retained.identity());
    assertEquals(original.profile(), retained.profile());
    assertEquals(original.snapshot(), retained.snapshot());
    assertEquals(original.environment(), retained.environment());
    assertEquals(original.program(), retained.program());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertEquals(original.diagnostics(), retained.diagnostics());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    assertEquals(AstCodec.materialize(original), AstCodec.materialize(retained));
    for (var node : retained.aggregate().nodes()) {
      assertSame(node, retained.node(new SourceOccurrence(retained.identity(), node.nodeId())).orElseThrow());
      for (var child : node.children()) {
        assertEquals(node.structuralPath().child(child.role(), child.position()), child.node().structuralPath());
      }
    }
  }

  static void assertMixed(AstResult retained) {
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals("java-source/21", retained.identity().sourceModelVersion());
    assertEquals(AstProfile.JAVA_SOURCE_UNITS, retained.profile());
    var first = field(retained, "first");
    var scope = retained.aggregate().executionSyntax(first.value(SyntaxValue.VariableDeclaration.class).declarationKey()).orElseThrow();
    assertTrue(scope.scope() instanceof ExecutionScope.Field);
    assertEquals("@Mark int", scope.useEvidence(first.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().displayName());
    assertTrue(scope.invocationEvidence(first.requiredChild(SyntaxRole.INITIALIZER).nodeId()).isPresent());
    var second = field(retained, "second").requiredChild(SyntaxRole.INITIALIZER);
    assertEquals(List.of("2", "3"), second.children(SyntaxRole.ARRAY_ELEMENT).stream()
        .map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
    var chain = field(retained, "chain").requiredChild(SyntaxRole.INITIALIZER);
    assertEquals(SyntaxKind.LAMBDA_EXPRESSION, chain.syntaxKind());
    assertEquals(SyntaxKind.LAMBDA_EXPRESSION, chain.requiredChild(SyntaxRole.BODY).syntaxKind());
    var constant = field(retained, "FIRST");
    assertEquals(List.of(SyntaxKind.INVOCATION), constant.children(SyntaxRole.ARGUMENT).stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.INSTANCE_INITIALIZER_BLOCK, SyntaxKind.METHOD_DECLARATION),
        constant.requiredChild(SyntaxRole.CLASS_BODY).children(SyntaxRole.MEMBER).stream().map(SyntaxNode::syntaxKind).toList());
    assertTrue(retained.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.ANNOTATION));
    for (var required : List.of(SyntaxKind.WHILE_LOOP, SyntaxKind.DO_WHILE_LOOP, SyntaxKind.ENHANCED_FOR_LOOP,
        SyntaxKind.SWITCH_EXPRESSION, SyntaxKind.BINDING_PATTERN, SyntaxKind.TRY, SyntaxKind.MEMBER_REFERENCE,
        SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK, SyntaxKind.EMPTY)) {
      assertTrue(retained.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == required), required.name());
    }
    assertFalse(retained.aggregate().executionScopes().stream().filter(value -> !(value instanceof ExecutionScope.Method)).toList().isEmpty());
  }

  private static SyntaxNode field(AstResult result, String name) {
    return result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.VariableDeclaration value
        && value.name().equals(name)).findFirst().orElseThrow();
  }
}
