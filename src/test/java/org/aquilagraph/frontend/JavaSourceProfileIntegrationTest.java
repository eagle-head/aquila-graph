package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceProfileIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenGrammarCrossingsOnTheUnitProfile_whenProducedAndDecoded_thenExactWrittenRolesAndOwnersSurvive() {
    // Arrange
    var cases = List.of(
        new SourceCase("conditional functional field",
            "class Example { java.util.function.IntUnaryOperator action = true ? (int x) -> x + 1 : x -> x - 1; }",
            SyntaxKind.CONDITIONAL_EXPRESSION, "true ? (int x) -> x + 1 : x -> x - 1",
            Map.of(SyntaxRole.CONDITION, List.of("true"), SyntaxRole.TRUE_EXPRESSION, List.of("(int x) -> x + 1"),
                SyntaxRole.FALSE_EXPRESSION, List.of("x -> x - 1"))),
        new SourceCase("qualified anonymous field",
            "class Outer<T> { class Inner { Inner(int n) {} } } class Example { Outer<String> owner = new Outer<>(); "
                + "Object value = owner.new Inner(3) { int[] values = {1}; }; }",
            SyntaxKind.OBJECT_CREATION, "owner.new Inner(3) { int[] values = {1}; }",
            Map.of(SyntaxRole.ENCLOSING_INSTANCE, List.of("owner"), SyntaxRole.ARGUMENT, List.of("3"),
                SyntaxRole.CLASS_BODY, List.of("{ int[] values = {1}; }"))),
        new SourceCase("record pattern in a field lambda",
            "record Pair(String text, int number) {} class Example { java.util.function.Function<Object, Integer> action = input -> "
                + "switch (input) { case Pair(String text, _) when !text.isEmpty() -> text.length(); case null, default -> 0; }; }",
            SyntaxKind.DECONSTRUCTION_PATTERN, "Pair(String text, _)",
            Map.of(SyntaxRole.PATTERN_COMPONENT, List.of("String text", "_"))),
        new SourceCase("constructor prologue and epilogue",
            "class Base { Base(int n) {} } class Example extends Base { Example() { int n = 1; super(n); n++; } }",
            SyntaxKind.BLOCK, "{ int n = 1; super(n); n++; }",
            Map.of(SyntaxRole.STATEMENT, List.of("int n = 1;", "super(n);", "n++;"))),
        new SourceCase("generic default method",
            "interface Example { default <T extends Number> T value(T input) { return input; } }",
            SyntaxKind.METHOD_DECLARATION, "default <T extends Number> T value(T input) { return input; }",
            Map.of(SyntaxRole.TYPE_PARAMETER, List.of("T extends Number"), SyntaxRole.PARAMETER, List.of("T input"),
                SyntaxRole.RETURN_TYPE, List.of("T"), SyntaxRole.BODY, List.of("{ return input; }"))),
        new SourceCase("resource access in an initializer lambda",
            "class Example { Runnable action = () -> { java.io.StringReader reader = new java.io.StringReader(\"x\"); "
                + "try (reader) { reader.read(); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); } "
                + "finally { assert true; } }; }",
            SyntaxKind.TRY, "try (reader) { reader.read(); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); } finally { assert true; }",
            Map.of(SyntaxRole.RESOURCE, List.of("reader"), SyntaxRole.TRY_BLOCK, List.of("{ reader.read(); }"),
                SyntaxRole.FINALLY_BLOCK, List.of("{ assert true; }"))));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.name() + "_whenProducedAndDecoded_thenExactWrittenRolesAndOwnersSurvive", () -> {
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), value.source())));
          var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS);
          var original = new JavaFrontend(new JavaSourceLoader()).produce(request).result().orElseThrow();
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          assertEquals(AstProfile.JAVA_SOURCE_UNITS, retained.profile());
          assertEquals("java-source/21", retained.identity().sourceModelVersion());
          assertEquals("javac-public/10", retained.identity().semanticProductionRevision());
          assertEquals(original.aggregate(), retained.aggregate());
          assertEquals(original.program(), retained.program());
          assertEquals(original.coverage(), retained.coverage());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
          assertFalse(retained.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
          var selected = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == value.kind()
              && written(node, value.source()).equals(value.occurrence())).toList();
          assertEquals(1, selected.size(), value.occurrence());
          value.children().forEach((role, expected) -> assertEquals(expected,
              selected.getFirst().children(role).stream().map(node -> written(node, value.source())).toList(), role.name()));
          for (var node : retained.aggregate().nodes()) {
            assertSame(node, retained.node(new SourceOccurrence(retained.identity(), node.nodeId())).orElseThrow());
            assertEquals(retained.aggregate().roots().contains(node) ? 0 : 1,
                retained.aggregate().structuralEdges().stream().filter(edge -> edge.childKey().equals(node.nodeId())).count());
            for (var child : node.children()) {
              assertEquals(node.structuralPath().child(child.role(), child.position()), child.node().structuralPath());
            }
          }
          assertTrue(retained.coverage().validationEvidence().unmatchedFacts().isEmpty());
        }));

    // Assert
    return observations;
  }

  private static String written(SyntaxNode node, String source) {
    var span = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }

  @TestFactory
  Stream<DynamicTest> givenInstantiatedAnonymousSuperclass_whenConstructorFactsAreCopied_thenWrittenArgumentsUseSubstitutedTargets() {
    // Arrange
    var qualifiers = Stream.of("owner", "(owner)", "owner()", "((Outer<String>) owner)", "(owner = other)");
    var sources = Stream.concat(qualifiers.map(qualifier ->
        "class Outer<T> { class Inner { Inner(T value) {} } } class Example { "
            + "Outer<String> owner = new Outer<>(), other = owner; Outer<String> owner() { return owner; } "
            + "Object value = " + qualifier + ".new Inner(\"x\") { String text = \"retained\"; }; }"), Stream.of(
        "class Outer<T> { class Inner { Inner(T value) {} } } class Child extends Outer<String> {} "
            + "class Example { Child owner = new Child(); Object subject() { return owner.new Inner(\"x\") {}; } }",
        "class Outer<T> { class Inner { @SafeVarargs Inner(T... values) {} } } "
            + "class Example { Outer<String> owner = new Outer<>(); Object value = owner.new Inner(\"x\", \"y\") {}; }",
        "class Outer<T> { class Inner { <U> Inner(U value) {} } } "
            + "class Example { Outer<String> owner = new Outer<>(); Runnable action = () -> { Object value = owner.new Inner(\"x\") {}; }; }"));

    // Act
    var observations = sources.map(source -> DynamicTest.dynamicTest(
        "given" + source + "_whenConstructorFactsAreCopied_thenWrittenArgumentsUseSubstitutedTargets", () -> {
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
          var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          var creation = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION
              && !node.children(SyntaxRole.CLASS_BODY).isEmpty()).findFirst().orElseThrow();
          var arguments = creation.children(SyntaxRole.ARGUMENT);
          assertEquals(source.contains("\"y\"") ? List.of("\"x\"", "\"y\"") : List.of("\"x\""),
              arguments.stream().map(node -> written(node, source)).toList());
          for (var index = 0; index < arguments.size(); index++) {
            var position = index;
            var evidence = retained.aggregate().executionScopes().stream()
                .flatMap(scope -> retained.aggregate().executionSyntax(scope.key()).orElseThrow()
                    .useEvidence(creation.nodeId(), SyntaxRole.ARGUMENT, position).stream()).toList();
            assertEquals(1, evidence.size());
            assertEquals("java.lang.String", evidence.getFirst().selectedTarget().displayName());
          }
          assertEquals(original.aggregate(), retained.aggregate());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
        }));

    // Assert
    return observations;
  }

  private record SourceCase(String name, String source, SyntaxKind kind, String occurrence, Map<SyntaxRole, List<String>> children) {}

  @Test
  void givenCompilerRejectedGenericAnonymousCreation_whenProduced_thenActualCompilerErrorsPreventCompleteness() throws Exception {
    // Arrange
    var source = "class Outer<T> { class Inner { <U extends T> Inner(U value) {} } } "
        + "class Example { Outer<String> owner = new Outer<>(); Runnable action = () -> { Object value = owner.new Inner(\"x\") {}; }; }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertTrue(result.diagnostics().stream().anyMatch(value -> value.isError() && value.code().startsWith("compiler.err.")));
  }
}
