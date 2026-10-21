package org.aquilagraph.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

public final class SourceFidelityFixture {
  public static final String SOURCE = """
      import java.lang.annotation.Repeatable;
      import java.lang.annotation.Target;
      import java.lang.annotation.ElementType;
      import java.util.function.Supplier;
      @Repeatable(Tags.class) @interface Tag { String value(); }
      @Repeatable(Groups.class) @interface Tags { Tag[] value(); }
      @interface Groups { Tags[] value(); }
      @Target(ElementType.TYPE_USE) @interface Use { String value(); }
      @interface Defaults { String value() default "default" + "text"; int number() default -1; }
      sealed class Base permits Open {}
      @Tag("first" + "tag") @Tag("second" + "tag")
      non-sealed class Open extends Base {
        int minimum = -2147483648;
        long wide = -9223372036854775808L;
        static String staticText;
        static { staticText = "static" + "block"; }
        String instance;
        { instance = "instance" + "block"; }
        Supplier<String> fieldLambda = () -> "field" + "lambda";
        @Use("constructor" + "type") Open() {}
        String subject(String input) { return 1 + 2 + "a" + "b" + input; }
        String refused(int input) {
          return switch(input) { case -2 -> "branch" + "value"; default -> "fallback" + "value"; };
        }
        String label(String input) {
          return switch(input) { case "switch" + "label" -> "matched"; default -> input; };
        }
        void nested() {
          class Local { int number() { return -3; } }
          Object anonymous = new Object() { String text() { return "anonymous" + "type"; } };
          Supplier<String> local = () -> "local" + "lambda";
        }
      }
      sealed interface Contract permits Extension {}
      non-sealed interface Extension extends Contract {}
      enum Choice { FIRST("enum" + "argument"); Choice(String value) {} }
      """;

  private SourceFidelityFixture() {
  }

  public static InputSnapshot snapshot(Path root) {
    return InputSnapshot.fromSources(root, List.of(SourceFile.fromContent(Path.of("Hierarchy.java"), SOURCE)));
  }

  public static void assertFaithful(AstResult result) {
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertTrue(result.diagnostics().isEmpty());
    assertEquals(AstProfile.JAVA_SOURCE_FIDELITY, result.profile());
    assertEquals("java-source/21", result.identity().sourceModelVersion());
    assertEquals("javac-public/10", result.identity().semanticProductionRevision());
    assertEquals("java-source-expanded/7", result.identity().astProfileVersion());
    assertSourceFacts(result);
  }

  public static void assertSourceFacts(AstResult result) {
    var annotationValues = Stream.concat(
        result.aggregate().nodes().stream().flatMap(node -> node.children(SyntaxRole.DEFAULT_VALUE).stream()),
        result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT)
            .map(node -> node.requiredChild(SyntaxRole.VALUE)))
        .flatMap(node -> node.descendantsAndSelf().stream()).toList();
    assertFalse(annotationValues.isEmpty());
    assertTrue(annotationValues.stream().allMatch(node -> result.aggregate().executableLiteralText(node.nodeId()).isEmpty()));
    var modifiers = result.coverage().validationEvidence().outcomes().stream()
        .filter(value -> value.address().role().equals("TypeElement::Element#getModifiers()"))
        .flatMap(value -> value.encounteredValue().stream()).flatMap(value -> value.items().stream())
        .filter(value -> value.value().equals("non-sealed")).toList();
    assertEquals(2, modifiers.size());
    for (var modifier : modifiers) {
      assertEquals("non-sealed", modifier.identity());
      assertEquals(CoveragePropertyValue.Provenance.COMPILER_DERIVED, modifier.provenance());
    }
    var open = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Open")).findFirst().orElseThrow();
    assertEquals(List.of(new AnnotationScalar.StringValue("firsttag"), new AnnotationScalar.StringValue("secondtag")),
        open.children(SyntaxRole.ANNOTATION).stream().map(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
            .value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertEquals(2, open.children(SyntaxRole.ANNOTATION).stream().map(SyntaxNode::nodeId).distinct().count());
    for (var expression : List.of("-1", "-2", "-3", "-2147483648", "-9223372036854775808L")) {
      var unary = occurrence(result, expression);
      assertEquals(JavaSourceKind.UNARY_MINUS, unary.sourceKind());
      assertEquals(SyntaxKind.UNARY, unary.syntaxKind());
      var operand = unary.requiredChild(SyntaxRole.OPERAND);
      assertEquals(expression.substring(1), text(operand));
      assertEquals(unary.structuralPath().child(SyntaxRole.OPERAND, 0), operand.structuralPath());
    }
    for (var expression : List.of("-2147483648", "-9223372036854775808L")) {
      var operand = occurrence(result, expression).requiredChild(SyntaxRole.OPERAND);
      assertTrue(operand.value(SyntaxValue.Literal.class).restrictedDecimal().isPresent());
      assertTrue(result.aggregate().writtenSourceAssociation(operand.nodeId()).orElseThrow()
          .normalizedExpression().orElseThrow().derivedConstant().isEmpty());
    }
    for (var pair : List.of("default:text", "first:tag", "second:tag", "static:block", "instance:block", "field:lambda",
        "constructor:type", "branch:value", "fallback:value", "switch:label", "anonymous:type", "local:lambda", "enum:argument")) {
      var parts = pair.split(":");
      var expression = "\"" + parts[0] + "\" + \"" + parts[1] + "\"";
      var plus = occurrence(result, expression);
      assertEquals(JavaSourceKind.PLUS, plus.sourceKind(), expression);
      assertEquals("\"" + parts[0] + "\"", text(plus.requiredChild(SyntaxRole.LEFT_OPERAND)));
      assertEquals("\"" + parts[1] + "\"", text(plus.requiredChild(SyntaxRole.RIGHT_OPERAND)));
    }
    var chain = occurrence(result, "1 + 2 + \"a\" + \"b\" + input");
    assertEquals("input", text(chain.requiredChild(SyntaxRole.RIGHT_OPERAND)));
    var constantPrefix = chain.requiredChild(SyntaxRole.LEFT_OPERAND);
    assertEquals("1 + 2 + \"a\" + \"b\"", text(constantPrefix));
    var numeric = constantPrefix.requiredChild(SyntaxRole.LEFT_OPERAND).requiredChild(SyntaxRole.LEFT_OPERAND);
    assertEquals("1 + 2", text(numeric));
    assertEquals("int", numeric.resolvedType().orElseThrow().displayName());
    assertEquals("java.lang.String", chain.resolvedType().orElseThrow().displayName());
    assertEquals(new AnnotationScalar.StringValue("3ab"), result.aggregate().writtenSourceAssociation(constantPrefix.nodeId())
        .orElseThrow().normalizedExpression().orElseThrow().derivedConstant().orElseThrow());
    assertFalse(result.coverage().validationEvidence().writtenInventory().writtenExpressions().orElseThrow().isEmpty());
    assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
    for (var node : result.aggregate().nodes()) {
      assertEquals(node, result.node(new SourceOccurrence(result.identity(), node.nodeId())).orElseThrow());
      for (var child : node.children()) {
        assertEquals(node.structuralPath().child(child.role(), child.position()), child.node().structuralPath());
      }
    }
  }

  private static SyntaxNode occurrence(AstResult result, String expression) {
    var sourceKey = result.snapshot().sourceFiles().stream().filter(file -> file.relativePath().toString().equals("Hierarchy.java"))
        .findFirst().orElseThrow().sourceFileKey();
    var nodes = result.aggregate().nodes().stream().filter(node -> node.sourceFileKey().equals(sourceKey)
        && node.syntaxKind() != SyntaxKind.ANNOTATION_ARGUMENT
        && expression.equals(text(node))).toList();
    assertEquals(1, nodes.size(), expression);
    return nodes.getFirst();
  }

  private static String text(SyntaxNode node) {
    return node.origin().map(span -> SOURCE.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())))
        .orElse("");
  }
}
