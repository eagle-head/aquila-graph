package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class WrittenExecutableTypeContractTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenQualifiedGenericCalls_whenProducedAndDecoded_thenWrittenTypeArgumentsKeepExactOrderRangesAndSelectedTypes() {
    var arguments = "@Mark List<@Mark String>, Map<String, List<Integer>>";
    return Stream.of("Base.<" + arguments + ">staticChoose(null, null)",
        "other.<" + arguments + ">choose(null, null)",
        "(other).<" + arguments + ">choose(null, null)",
        "super.<" + arguments + ">choose(null, null)",
        "Example.super.<" + arguments + ">choose(null, null)",
        "choose(\"text\", 1)")
        .map(expression -> DynamicTest.dynamicTest(expression, () -> {
          // Arrange
          var nested = expression.startsWith("Example.super");
          var method = nested ? "class Inner { Object subject() { return " + expression + "; } }"
              : "Object subject(Example other) { return " + expression + "; }";
          var source = "import java.util.List; import java.util.Map; import java.lang.annotation.*; "
              + "@Target(ElementType.TYPE_USE) @interface Mark {} "
              + "class Base { static <A, B> A staticChoose(A a, B b) { return a; } <A, B> A choose(A a, B b) { return a; } } "
              + "class Example extends Base { " + method + " }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var calls = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).toList();
            assertEquals(1, calls.size());
            var call = calls.getFirst();
            assertSpan(source, call, expression, source.indexOf(expression));
            var name = expression.startsWith("Base.") ? "staticChoose" : "choose";
            var baseKey = new JavaTypeDeclarationIdentity(Optional.empty(), "Base").declarationKey();
            assertEquals(baseKey + "#" + name + "(java.lang.Object,java.lang.Object)", call.requiredReference().symbolKey().orElseThrow());
            if (expression.startsWith("choose(")) {
              assertTrue(call.children(SyntaxRole.TYPE_ARGUMENT).isEmpty());
              assertEquals("java.lang.String", call.resolvedType().orElseThrow().erasedName());
              continue;
            }
            var types = call.children(SyntaxRole.TYPE_ARGUMENT);
            assertEquals(2, types.size());
            var start = source.indexOf(expression) + expression.indexOf('<') + 1;
            assertAnnotatedList(source, types.get(0), start);
            assertNestedMap(source, types.get(1), start + "@Mark List<@Mark String>, ".length());
            for (var index = 0; index < types.size(); index++) {
              assertEquals(call.structuralPath().child(SyntaxRole.TYPE_ARGUMENT, index), types.get(index).structuralPath());
            }
            assertEquals(List.of(0, 1), call.children().stream().filter(child -> child.role() == SyntaxRole.TYPE_ARGUMENT)
                .map(child -> child.position()).toList());
            assertEquals("java.util.List", call.resolvedType().orElseThrow().erasedName());
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenParameterizedWildcardAndIntersectionCasts_whenProducedAndDecoded_thenCompleteWrittenOperandsAndSelectedStructuresRemainExact() {
    return Stream.of(
        new CastCase("List<String>", "List<String>", false),
        new CastCase("List<?>", "List<String>", false),
        new CastCase("List<? extends Number>", "List<Number>", false),
        new CastCase("List<? super Integer>", "List<Number>", false),
        new CastCase("Map<String, List<Integer>>", "Map<String, List<Integer>>", false),
        new CastCase("Runnable & Serializable", "Object", false),
        new CastCase("Serializable & Runnable", "Object", false),
        new CastCase("List<String>", "List<String>", true),
        new CastCase("List<?>", "List<String>", true),
        new CastCase("List<? extends Number>", "List<Number>", true),
        new CastCase("List<? super Integer>", "List<Number>", true),
        new CastCase("Runnable & Serializable", "Object", true))
        .map(variant -> DynamicTest.dynamicTest(variant.toString(), () -> {
          // Arrange
          var castText = "(" + variant.type() + ") value";
          var expression = variant.parenthesized() ? "((" + castText + "))" : castText;
          var source = "import java.util.List; import java.util.Map; import java.io.Serializable; "
              + "class Example { Object subject(" + variant.parameter() + " value) { return " + expression + "; } }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var casts = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).toList();
            assertEquals(1, casts.size());
            var cast = casts.getFirst();
            var start = source.indexOf(castText);
            assertSpan(source, cast, castText, start);
            var operand = cast.requiredChild(SyntaxRole.TYPE_OPERAND);
            assertEquals(cast.structuralPath().child(SyntaxRole.TYPE_OPERAND, 0), operand.structuralPath());
            // The compiler intersection tree includes the cast's opening parenthesis.
            assertSpan(source, operand, (variant.type().contains(" & ") ? "(" : "") + variant.type(),
                start + (variant.type().contains(" & ") ? 0 : 1));
            assertSpan(source, cast.requiredChild(SyntaxRole.EXPRESSION), "value", start + variant.type().length() + 3);
            var expected = assertCastType(source, operand, variant.type(), start + 1);
            assertEquals(castResult(variant.type(), expected), structure(cast));
            var parentheses = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.PARENTHESIZED).toList();
            assertEquals(variant.parenthesized() ? 2 : 0, parentheses.size());
            if (variant.parenthesized()) {
              var root = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.RETURN).findFirst().orElseThrow()
                  .requiredChild(SyntaxRole.EXPRESSION);
              assertSpan(source, root, expression, source.indexOf(expression));
              assertEquals(cast, root.requiredChild(SyntaxRole.EXPRESSION).requiredChild(SyntaxRole.EXPRESSION));
            }
          }
        }));
  }

  private static JavaTypeStructure assertCastType(String source, SyntaxNode node, String text, int start) {
    if (text.contains(" & ")) {
      assertEquals(JavaSourceKind.INTERSECTION_TYPE, node.sourceKind());
      var names = List.of(text.split(" & "));
      var bounds = node.children(SyntaxRole.INTERSECTION_BOUND);
      assertEquals(2, bounds.size());
      var structures = names.stream().map(name -> declared(name.equals("Runnable") ? "java.lang.Runnable" : "java.io.Serializable")).toList();
      var offset = start;
      for (var index = 0; index < names.size(); index++) {
        assertNominal(source, bounds.get(index), names.get(index), offset,
            names.get(index).equals("Runnable") ? "java.lang.Runnable" : "java.io.Serializable");
        assertEquals(node.structuralPath().child(SyntaxRole.INTERSECTION_BOUND, index), bounds.get(index).structuralPath());
        offset += names.get(index).length() + 3;
      }
      var expected = new JavaTypeStructure.Intersection(List.copyOf(structures));
      assertEquals(expected, structure(node));
      return expected;
    }
    if (text.startsWith("Map")) {
      assertNestedMap(source, node, start);
      return nestedMap();
    }
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, node.sourceKind());
    assertNominal(source, node.requiredChild(SyntaxRole.TYPE_NAME), "List", start, "java.util.List");
    var argument = node.requiredChild(SyntaxRole.TYPE_ARGUMENT);
    var argumentStart = start + 5;
    JavaTypeStructure expectedArgument;
    if (text.equals("List<String>")) {
      assertNominal(source, argument, "String", argumentStart, "java.lang.String");
      expectedArgument = declared("java.lang.String");
    } else {
      var unbounded = text.equals("List<?>");
      var upper = text.equals("List<? extends Number>");
      var boundKind = unbounded ? JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED
          : upper ? JavaTypeStructure.Wildcard.BoundKind.EXTENDS : JavaTypeStructure.Wildcard.BoundKind.SUPER;
      assertEquals(unbounded ? JavaSourceKind.UNBOUNDED_WILDCARD : upper ? JavaSourceKind.EXTENDS_WILDCARD : JavaSourceKind.SUPER_WILDCARD,
          argument.sourceKind());
      assertSpan(source, argument, text.substring(5, text.length() - 1), argumentStart);
      var bound = unbounded ? Optional.<JavaTypeStructure>empty() : Optional.<JavaTypeStructure>of(declared(upper ? "java.lang.Number" : "java.lang.Integer"));
      expectedArgument = new JavaTypeStructure.Wildcard(boundKind, bound);
      assertEquals(expectedArgument, structure(argument));
      if (unbounded) {
        assertTrue(argument.children().isEmpty());
      } else {
        var role = upper ? SyntaxRole.UPPER_BOUND : SyntaxRole.LOWER_BOUND;
        assertEquals(List.of(role), argument.children().stream().map(child -> child.role()).toList());
        assertNominal(source, argument.requiredChild(role), upper ? "Number" : "Integer",
            argumentStart + (upper ? "? extends ".length() : "? super ".length()), upper ? "java.lang.Number" : "java.lang.Integer");
      }
    }
    assertEquals(List.of(SyntaxRole.TYPE_NAME, SyntaxRole.TYPE_ARGUMENT), node.children().stream().map(child -> child.role()).toList());
    assertEquals(node.structuralPath().child(SyntaxRole.TYPE_ARGUMENT, 0), argument.structuralPath());
    var expected = declared("java.util.List", expectedArgument);
    assertEquals(expected, structure(node));
    return expected;
  }

  private static void assertAnnotatedList(String source, SyntaxNode node, int start) {
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, node.sourceKind());
    assertSpan(source, node, "@Mark List<@Mark String>", start);
    assertEquals(List.of(SyntaxRole.TYPE_NAME, SyntaxRole.TYPE_ARGUMENT), node.children().stream().map(child -> child.role()).toList());
    var name = node.requiredChild(SyntaxRole.TYPE_NAME);
    assertEquals(JavaSourceKind.ANNOTATED_TYPE, name.sourceKind());
    assertSpan(source, name, "@Mark List", start);
    assertEquals(List.of(SyntaxRole.ANNOTATION, SyntaxRole.UNDERLYING_TYPE), name.children().stream().map(child -> child.role()).toList());
    assertSpan(source, name.requiredChild(SyntaxRole.ANNOTATION), "@Mark", start);
    assertNominal(source, name.requiredChild(SyntaxRole.UNDERLYING_TYPE), "List", start + 6, "java.util.List");
    var annotated = node.requiredChild(SyntaxRole.TYPE_ARGUMENT);
    assertEquals(JavaSourceKind.ANNOTATED_TYPE, annotated.sourceKind());
    assertSpan(source, annotated, "@Mark String", start + 11);
    assertEquals(List.of(SyntaxRole.ANNOTATION, SyntaxRole.UNDERLYING_TYPE), annotated.children().stream().map(child -> child.role()).toList());
    assertSpan(source, annotated.requiredChild(SyntaxRole.ANNOTATION), "@Mark", start + 11);
    assertNominal(source, annotated.requiredChild(SyntaxRole.UNDERLYING_TYPE), "String", start + 17, "java.lang.String");
    var expected = declared("java.util.List", declared("java.lang.String"));
    assertEquals(expected, structure(node));
    assertEquals(declared("java.lang.String"), structure(annotated));
  }

  private static JavaTypeStructure castResult(String text, JavaTypeStructure target) {
    return switch (text) {
      case "List<?>" -> declared("java.util.List", new JavaTypeStructure.Captured(0, declared("java.lang.Object"), Optional.empty()));
      case "List<? extends Number>" -> declared("java.util.List", new JavaTypeStructure.Captured(0, declared("java.lang.Number"), Optional.empty()));
      case "List<? super Integer>" -> declared("java.util.List", new JavaTypeStructure.Captured(0, declared("java.lang.Object"), Optional.of(declared("java.lang.Integer"))));
      default -> target;
    };
  }

  private static void assertNestedMap(String source, SyntaxNode node, int start) {
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, node.sourceKind());
    assertSpan(source, node, "Map<String, List<Integer>>", start);
    assertEquals(List.of(SyntaxRole.TYPE_NAME, SyntaxRole.TYPE_ARGUMENT, SyntaxRole.TYPE_ARGUMENT), node.children().stream().map(child -> child.role()).toList());
    assertNominal(source, node.requiredChild(SyntaxRole.TYPE_NAME), "Map", start, "java.util.Map");
    var arguments = node.children(SyntaxRole.TYPE_ARGUMENT);
    assertNominal(source, arguments.get(0), "String", start + 4, "java.lang.String");
    var list = arguments.get(1);
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, list.sourceKind());
    assertSpan(source, list, "List<Integer>", start + 12);
    assertNominal(source, list.requiredChild(SyntaxRole.TYPE_NAME), "List", start + 12, "java.util.List");
    assertNominal(source, list.requiredChild(SyntaxRole.TYPE_ARGUMENT), "Integer", start + 17, "java.lang.Integer");
    assertEquals(declared("java.util.List", declared("java.lang.Integer")), structure(list));
    assertEquals(nestedMap(), structure(node));
    for (var index = 0; index < arguments.size(); index++) {
      assertEquals(node.structuralPath().child(SyntaxRole.TYPE_ARGUMENT, index), arguments.get(index).structuralPath());
    }
  }

  private static void assertNominal(String source, SyntaxNode node, String text, int start, String binaryName) {
    assertEquals(SyntaxKind.TYPE_USE, node.syntaxKind());
    assertEquals(JavaSourceKind.IDENTIFIER, node.sourceKind());
    assertSpan(source, node, text, start);
    assertEquals(binaryName, node.resolvedType().orElseThrow().erasedName());
    assertEquals(platformKey(binaryName), node.requiredReference().symbolKey().orElseThrow());
  }

  private List<AstResult> produceAndDecode(String source) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    var outcome = JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, decoded.profile());
    return List.of(original, decoded);
  }

  private static JavaTypeStructure.Declared nestedMap() {
    return declared("java.util.Map", declared("java.lang.String"), declared("java.util.List", declared("java.lang.Integer")));
  }

  private static JavaTypeStructure.Declared declared(String binaryName, JavaTypeStructure... arguments) {
    return new JavaTypeStructure.Declared(platformKey(binaryName), Optional.empty(), List.of(arguments));
  }

  private static String platformKey(String binaryName) {
    return new JavaTypeDeclarationIdentity(Optional.of("java.base"), binaryName).declarationKey();
  }

  private static JavaTypeStructure structure(SyntaxNode node) {
    return assertInstanceOf(SyntaxType.Resolved.class, node.type()).structure().orElseThrow();
  }

  private static void assertSpan(String source, SyntaxNode node, String text, int start) {
    assertTrue(start >= 0, text);
    assertEquals(new SourceSpan(node.sourceFileKey(), start, start + text.length()), node.origin().orElseThrow());
    assertEquals(text, source.substring(start, start + text.length()));
  }

  private record CastCase(String type, String parameter, boolean parenthesized) {}
}
