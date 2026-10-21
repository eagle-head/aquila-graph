package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

final class ExecutableAstRetentionFixture {
  private ExecutableAstRetentionFixture() {
  }

  static JavaFrontend frontend() {
    return new JavaFrontend(new JavaSourceLoader());
  }

  static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }

  static InputSnapshot snapshot(Path directory) {
    var body = """
        @Local final int[] values = new int[]{seed, seed + 1};
        int total = seed;
        outer: for (int index = 0; index < values.length; index++) {
          total += values[index];
          if (total == 0) continue outer;
          if (total > 20) break outer;
        }
        while (total < 1) { ++total; }
        do { total--; } while (total > 30);
        for (int value : values) { total ^= value; }
        synchronized (values) { assert total >= 0 : "negative"; }
        Supplier<IntSupplier> deferred = () -> () -> { int[] nested = {seed}; return nested[0]; };
        Supplier<Class<?>> token = () -> int[][].class;
        Function<String, Integer> length = String::length;
        IntFunction<int[]> arrays = int[]::new;
        Runnable action = (Runnable & Serializable) () -> {};
        if (input instanceof Pair(String text, int count)) { total += text.length() + count; }
        switch (total) { case 0: total += 1; break; default: }
        try (var reader = new StringReader("value")) {
          total += switch (input) {
            case String text when !text.isEmpty() -> text.length();
            case null, default -> seed;
          };
          total += reader.read();
        } catch (IOException | IllegalArgumentException failure) { total = seed; }
        finally { total += 1; }
        total += length.apply("v") + arrays.apply(seed).length + deferred.get().getAsInt();
        """;
    var source = """
        import java.io.IOException;
        import java.io.Serializable;
        import java.io.StringReader;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.function.Function;
        import java.util.function.IntFunction;
        import java.util.function.IntSupplier;
        import java.util.function.Supplier;
        @Target(ElementType.LOCAL_VARIABLE) @interface Local {}
        class Stored {
          record Pair(Object text, int count) {}
          Stored(Object input, int seed) throws IOException { %s }
          int subject(Object input, int seed) throws IOException { %s return total; }
          int supported(int seed) { return seed + 1; }
        }
        """.formatted(body, body);
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
  }

  static void assertPreserved(AstResult original, AstResult retained) {
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(AstResult.ENVIRONMENT_IDENTITY_MODEL_VERSION, retained.identity().sourceModelVersion());
    assertEquals(original.identity(), retained.identity());
    assertEquals(original.profile(), retained.profile());
    assertEquals(original.snapshot(), retained.snapshot());
    assertEquals(original.environment(), retained.environment());
    assertEquals(original.program(), retained.program());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertEquals(original.diagnostics(), retained.diagnostics());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));

    var owner = retained.program().types().stream().filter(type -> type.qualifiedName().equals("Stored")).findFirst().orElseThrow();
    var methods = retained.program().methods().stream().filter(method -> method.ownerTypeKey().equals(owner.typeKey()))
        .filter(method -> method.name().equals("subject") || method.constructor()).toList();
    assertEquals(2, methods.size());
    for (var method : methods) {
      var body = retained.body(method.methodKey()).orElseThrow();
      var nodes = body.root().descendantsAndSelf();
      for (var kind : List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.ARRAY_CREATION, SyntaxKind.LAMBDA_EXPRESSION,
          SyntaxKind.MEMBER_REFERENCE, SyntaxKind.CLASS_LITERAL, SyntaxKind.FOR_LOOP, SyntaxKind.WHILE_LOOP, SyntaxKind.DO_WHILE_LOOP,
          SyntaxKind.ENHANCED_FOR_LOOP, SyntaxKind.LABELED_STATEMENT, SyntaxKind.BREAK, SyntaxKind.CONTINUE,
          SyntaxKind.ASSERT, SyntaxKind.SYNCHRONIZED, SyntaxKind.COMPOUND_ASSIGNMENT, SyntaxKind.UPDATE,
          SyntaxKind.TYPE_CAST, SyntaxKind.SWITCH_STATEMENT, SyntaxKind.SWITCH_EXPRESSION,
          SyntaxKind.INSTANCE_OF, SyntaxKind.DECONSTRUCTION_PATTERN, SyntaxKind.BINDING_PATTERN, SyntaxKind.TRY, SyntaxKind.CATCH)) {
        assertTrue(nodes.stream().anyMatch(node -> node.syntaxKind() == kind), kind.toString());
      }
      var values = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value
          && value.name().equals("values")).findFirst().orElseThrow();
      assertEquals(List.of("final"), values.value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
      assertEquals(1, values.children(SyntaxRole.ANNOTATION).size());
      assertTrue(values.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().isPresent());
      assertEquals(2, values.requiredChild(SyntaxRole.INITIALIZER).children(SyntaxRole.ARRAY_ELEMENT).size());
      var cast = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
      var literal = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL).findFirst().orElseThrow();
      assertEquals("java.lang.Class<int[][]>", literal.resolvedType().orElseThrow().displayName());
      assertEquals(SyntaxKind.TYPE_USE, literal.requiredChild(SyntaxRole.TYPE_OPERAND).syntaxKind());
      assertEquals(2, literal.requiredChild(SyntaxRole.TYPE_OPERAND).descendantsAndSelf().stream()
          .filter(node -> node.sourceKind().name().equals("ARRAY_TYPE")).count());
      assertEquals(2, cast.requiredChild(SyntaxRole.TYPE_OPERAND).children(SyntaxRole.INTERSECTION_BOUND).size());
      var deferred = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value
          && value.name().equals("deferred")).findFirst().orElseThrow().requiredChild(SyntaxRole.INITIALIZER);
      assertEquals(SyntaxKind.LAMBDA_EXPRESSION, deferred.requiredChild(SyntaxRole.BODY).syntaxKind());
      assertEquals(SyntaxKind.BLOCK, deferred.requiredChild(SyntaxRole.BODY).requiredChild(SyntaxRole.BODY).syntaxKind());
      var statement = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TRY).findFirst().orElseThrow();
      assertEquals(List.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK),
          statement.children().stream().map(child -> child.role()).toList());
      assertEquals(2, statement.requiredChild(SyntaxRole.CATCH_CLAUSE).requiredChild(SyntaxRole.PARAMETER)
          .requiredChild(SyntaxRole.DECLARED_TYPE).children(SyntaxRole.UNION_ALTERNATIVE).size());
      var selection = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION).findFirst().orElseThrow();
      assertTrue(selection.children(SyntaxRole.CASE).getFirst().optionalChild(SyntaxRole.GUARD).isPresent());
      assertEquals(List.of(new SyntaxValue.SwitchLabel.Expression(0), SyntaxValue.SwitchLabel.Default.INSTANCE),
          selection.children(SyntaxRole.CASE).getLast().value(SyntaxValue.SwitchCase.class).labels());
      for (var node : nodes) {
        assertSame(node, retained.aggregate().node(node.nodeId()).orElseThrow());
        assertEquals(1, retained.aggregate().structuralEdges().stream().filter(edge -> edge.childKey().equals(node.nodeId())).count());
        assertTrue(node.origin().isPresent());
      }
    }
  }
}
