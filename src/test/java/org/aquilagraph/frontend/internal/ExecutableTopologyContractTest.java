package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ExecutableTopologyContractTest {
  @TempDir Path directory;

  @Test
  void givenCrossGroupLocalAndLabeledSwitch_whenProducedAndDecoded_thenLaterWriteAndReadSelectTheEarlierDeclaration() {
    // Arrange
    var source = "class Example { int subject(int value) { outer: switch (value) { "
        + "case 0: int count = 1; break outer; case 1: count = 2; return count; default: break; } return 0; } }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var selection = only(ast, SyntaxKind.SWITCH_STATEMENT);
      var groups = selection.children(SyntaxRole.CASE);
      assertEquals(3, groups.size());
      assertNotEquals(groups.get(0).nodeId(), groups.get(1).nodeId());
      var declaration = groups.get(0).children(SyntaxRole.STATEMENT).getFirst();
      assertEquals(SyntaxKind.LOCAL_DECLARATION, declaration.syntaxKind());
      assertEquals("count", declaration.value(SyntaxValue.LocalDeclaration.class).name());
      var key = declaration.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow()
          .declarationKey().orElseThrow();
      var statements = groups.get(1).children(SyntaxRole.STATEMENT);
      assertEquals(List.of(SyntaxKind.EXPRESSION_STATEMENT, SyntaxKind.RETURN), statements.stream().map(SyntaxNode::syntaxKind).toList());
      var assignment = statements.getFirst().requiredChild(SyntaxRole.EXPRESSION);
      assertEquals(SyntaxKind.ASSIGNMENT, assignment.syntaxKind());
      var write = assignment.requiredChild(SyntaxRole.LOCATION);
      var read = statements.getLast().requiredChild(SyntaxRole.EXPRESSION);
      for (var reference : List.of(write, read)) {
        assertEquals(SyntaxKind.VARIABLE_ACCESS, reference.syntaxKind());
        assertEquals(key, reference.requiredReference().symbolKey().orElseThrow());
      }
      assertEquals(groups.get(0).structuralPath().child(SyntaxRole.STATEMENT, 0), declaration.structuralPath());
      assertEquals(groups.get(1).structuralPath().child(SyntaxRole.STATEMENT, 0), statements.getFirst().structuralPath());
      assertEquals(groups.get(1).structuralPath().child(SyntaxRole.STATEMENT, 1), statements.getLast().structuralPath());
      assertSpan(source, write, "count", source.indexOf("count = 2"));
      assertSpan(source, read, "count", source.indexOf("count; default"));
      var transfer = groups.getFirst().children(SyntaxRole.STATEMENT).getLast();
      assertEquals(SyntaxKind.BREAK, transfer.syntaxKind());
      assertEquals(Optional.of("outer"), transfer.value(SyntaxValue.StatementLabel.class).name());
      assertSpan(source, transfer, "break outer;", source.indexOf("break outer;"));
      var labeled = only(ast, SyntaxKind.LABELED_STATEMENT);
      assertEquals(Optional.of("outer"), labeled.value(SyntaxValue.StatementLabel.class).name());
      assertEquals(selection, labeled.requiredChild(SyntaxRole.BODY));
      assertEquals(Optional.empty(), groups.getLast().children(SyntaxRole.STATEMENT).getFirst()
          .value(SyntaxValue.StatementLabel.class).name());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenNestedIfAndNoShortIfWrappers_whenProducedAndDecoded_thenEveryElseHasItsWrittenParent() {
    return Stream.of(
        new BranchCase("if (a) if (b) sink(1); else sink(2);", SyntaxKind.IF, false, true),
        new BranchCase("if (a) { if (b) sink(1); } else sink(3);", SyntaxKind.BLOCK, true, false),
        new BranchCase("if (a) if (b) sink(1); else sink(2); else sink(3);", SyntaxKind.IF, true, true),
        new BranchCase("if (a) label: if (b) sink(1); else sink(2);", SyntaxKind.LABELED_STATEMENT, false, true),
        new BranchCase("if (a) label: if (b) sink(1); else sink(2); else sink(3);", SyntaxKind.LABELED_STATEMENT, true, true),
        new BranchCase("if (a) while (c) if (b) sink(1); else sink(2);", SyntaxKind.WHILE_LOOP, false, true),
        new BranchCase("if (a) while (c) if (b) sink(1); else sink(2); else sink(3);", SyntaxKind.WHILE_LOOP, true, true),
        new BranchCase("if (a) for (;c;) if (b) sink(1); else sink(2);", SyntaxKind.FOR_LOOP, false, true),
        new BranchCase("if (a) for (;c;) if (b) sink(1); else sink(2); else sink(3);", SyntaxKind.FOR_LOOP, true, true),
        new BranchCase("if (a) for (int x : values) if (b) sink(1); else sink(2);", SyntaxKind.ENHANCED_FOR_LOOP, false, true),
        new BranchCase("if (a) for (int x : values) if (b) sink(1); else sink(2); else sink(3);", SyntaxKind.ENHANCED_FOR_LOOP, true, true),
        new BranchCase("if (a) do if (b) sink(1); else sink(2); while (c); else sink(3);", SyntaxKind.DO_WHILE_LOOP, true, true),
        new BranchCase("if (a) synchronized (this) { if (b) sink(1); } else sink(3);", SyntaxKind.SYNCHRONIZED, true, false),
        new BranchCase("if (a) try { if (b) sink(1); } finally {} else sink(3);", SyntaxKind.TRY, true, false),
        new BranchCase("if (a) switch (0) { default: if (b) sink(1); else sink(2); } else sink(3);", SyntaxKind.SWITCH_STATEMENT, true, true))
        .map(variant -> DynamicTest.dynamicTest(variant.statement(), () -> {
          // Arrange
          var source = "class Example { void sink(int n) {} void subject(boolean a, boolean b, boolean c, int[] values) { "
              + variant.statement() + " } }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var outer = conditional(ast, source, "(a)");
            var inner = conditional(ast, source, "(b)");
            var wrapper = outer.requiredChild(SyntaxRole.THEN_BRANCH);
            assertEquals(variant.wrapper(), wrapper.syntaxKind());
            assertEquals(outer.structuralPath().child(SyntaxRole.THEN_BRANCH, 0), wrapper.structuralPath());
            var nested = switch (variant.wrapper()) {
              case IF -> wrapper;
              case BLOCK -> wrapper.children(SyntaxRole.STATEMENT).getFirst();
              case SYNCHRONIZED -> wrapper.requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT).getFirst();
              case TRY -> wrapper.requiredChild(SyntaxRole.TRY_BLOCK).children(SyntaxRole.STATEMENT).getFirst();
              case SWITCH_STATEMENT -> wrapper.children(SyntaxRole.CASE).getFirst().children(SyntaxRole.STATEMENT).getFirst();
              default -> wrapper.requiredChild(SyntaxRole.BODY);
            };
            assertEquals(inner, nested);
            assertBranch(source, inner, SyntaxRole.THEN_BRANCH, "sink(1);");
            assertEquals(variant.innerElse(), inner.optionalChild(SyntaxRole.ELSE_BRANCH).isPresent());
            assertEquals(variant.outerElse(), outer.optionalChild(SyntaxRole.ELSE_BRANCH).isPresent());
            if (variant.innerElse()) assertBranch(source, inner, SyntaxRole.ELSE_BRANCH, "sink(2);");
            if (variant.outerElse()) assertBranch(source, outer, SyntaxRole.ELSE_BRANCH, "sink(3);");
            assertEquals((variant.innerElse() ? 1 : 0) + (variant.outerElse() ? 1 : 0),
                ast.aggregate().nodes().stream().flatMap(node -> node.children().stream())
                    .filter(child -> child.role() == SyntaxRole.ELSE_BRANCH).count());
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenLabeledLoopNoShortIfBodies_whenProducedAndDecoded_thenNestedAndOuterElseBranchesKeepSeparateOwners() {
    return Stream.of(SyntaxKind.WHILE_LOOP, SyntaxKind.FOR_LOOP, SyntaxKind.ENHANCED_FOR_LOOP)
        .map(kind -> DynamicTest.dynamicTest("givenLabeled" + kind + "_whenProducedAndDecoded_thenElseOwnersRemainDistinct", () -> {
          // Arrange
          var loop = switch (kind) {
            case WHILE_LOOP -> "while (c)";
            case FOR_LOOP -> "for (;c;)";
            case ENHANCED_FOR_LOOP -> "for (int x : values)";
            default -> throw new IllegalArgumentException("Unexpected loop fixture kind");
          };
          var source = "class Example { void sink(int n) {} void subject(boolean a, boolean b, boolean c, int[] values) { "
              + "if (a) label: " + loop + " if (b) sink(1); else sink(2); else sink(3); } }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var outer = conditional(ast, source, "(a)");
            var label = outer.requiredChild(SyntaxRole.THEN_BRANCH);
            assertEquals(SyntaxKind.LABELED_STATEMENT, label.syntaxKind());
            assertEquals(Optional.of("label"), label.value(SyntaxValue.StatementLabel.class).name());
            var body = label.requiredChild(SyntaxRole.BODY);
            assertEquals(kind, body.syntaxKind());
            assertEquals(label.structuralPath().child(SyntaxRole.BODY, 0), body.structuralPath());
            var inner = body.requiredChild(SyntaxRole.BODY);
            assertEquals(conditional(ast, source, "(b)"), inner);
            assertEquals(body.structuralPath().child(SyntaxRole.BODY, 0), inner.structuralPath());
            assertBranch(source, inner, SyntaxRole.THEN_BRANCH, "sink(1);");
            assertBranch(source, inner, SyntaxRole.ELSE_BRANCH, "sink(2);");
            assertBranch(source, outer, SyntaxRole.ELSE_BRANCH, "sink(3);");
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenArrayReceiversInMethodsAndLambdas_whenProducedAndDecoded_thenLengthIsAnIntrinsicIntWithExactQualifier() {
    return Stream.of(
        new ArrayCase("int subject(String[] values) { return values.length; }", "values.length", "values", SyntaxKind.VARIABLE_ACCESS),
        new ArrayCase("int subject(String[] values) { return ((values)).length; }", "((values)).length", "((values))", SyntaxKind.PARENTHESIZED),
        new ArrayCase("int subject(String[][] values) { return values[0].length; }", "values[0].length", "values[0]", SyntaxKind.ARRAY_ACCESS),
        new ArrayCase("int subject(String[][] values) { return (values[0]).length; }", "(values[0]).length", "(values[0])", SyntaxKind.PARENTHESIZED),
        new ArrayCase("F subject() { return (String[] values) -> values.length; }", "values.length", "values", SyntaxKind.VARIABLE_ACCESS),
        new ArrayCase("F subject() { return values -> ((values)).length; }", "((values)).length", "((values))", SyntaxKind.PARENTHESIZED),
        new ArrayCase("G subject() { return (String[][] values) -> (values[0]).length; }", "(values[0]).length", "(values[0])", SyntaxKind.PARENTHESIZED))
        .map(variant -> DynamicTest.dynamicTest(variant.method(), () -> {
          // Arrange
          var source = "class Example { interface F { int apply(String[] values); } interface G { int apply(String[][] values); } "
              + variant.method() + " }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var length = only(ast, SyntaxKind.ARRAY_LENGTH);
            assertEquals("length", length.value(SyntaxValue.SourceSymbol.class).name());
            assertEquals("int", length.resolvedType().orElseThrow().erasedName());
            assertEquals(List.of(SyntaxRole.QUALIFIER), length.children().stream().map(child -> child.role()).toList());
            var qualifier = length.requiredChild(SyntaxRole.QUALIFIER);
            assertEquals(variant.qualifierKind(), qualifier.syntaxKind());
            assertEquals("java.lang.String[]", qualifier.resolvedType().orElseThrow().erasedName());
            var start = source.indexOf(variant.expression());
            assertSpan(source, length, variant.expression(), start);
            assertSpan(source, qualifier, variant.qualifier(), start);
            assertEquals(length.structuralPath().child(SyntaxRole.QUALIFIER, 0), qualifier.structuralPath());
            assertTrue(length.semanticReference().flatMap(reference -> reference.symbolKey()).isEmpty());
            assertFalse(ast.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.FIELD_ACCESS));
          }
        }));
  }

  @Test
  void givenConstantSwitchLabels_whenProducedAndDecoded_thenWrittenTreesAndNamedTargetsSurviveConstantFolding() {
    // Arrange
    var source = "class Example { int numeric(int value) { final int limit = 3; return switch (value) { "
        + "case 1 + 2 -> 10; case limit - 1 -> 11; case -1 -> 12; case (int) 4.0 -> 13; "
        + "case (true ? 5 : 6) -> 14; default -> 0; }; } "
        + "int text(String value) { return switch (value) { case \"a\" + \"b\" -> 1; default -> 0; }; } }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var selections = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION).toList();
      assertEquals(2, selections.size());
      var numeric = selections.stream().filter(node -> node.children(SyntaxRole.CASE).size() == 6).findFirst().orElseThrow();
      var labels = numeric.children(SyntaxRole.CASE).stream().flatMap(node -> node.children(SyntaxRole.LABEL).stream()).toList();
      assertEquals(List.of("1 + 2", "limit - 1", "-1", "(int) 4.0", "(true ? 5 : 6)"), labels.stream().map(node -> written(source, node)).toList());
      assertBinary(source, labels.get(0), SyntaxOperator.PLUS, "1", "2");
      assertEquals(List.of(SyntaxKind.LITERAL, SyntaxKind.LITERAL), labels.get(0).children().stream().map(child -> child.node().syntaxKind()).toList());
      assertBinary(source, labels.get(1), SyntaxOperator.MINUS, "limit", "1");
      var declaration = only(ast, SyntaxKind.LOCAL_DECLARATION);
      var key = declaration.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
      var named = labels.get(1).requiredChild(SyntaxRole.LEFT_OPERAND);
      assertEquals(SyntaxKind.VARIABLE_ACCESS, named.syntaxKind());
      assertEquals(key, named.requiredReference().symbolKey().orElseThrow());
      assertEquals(SyntaxKind.UNARY, labels.get(2).syntaxKind());
      assertEquals(SyntaxOperator.UNARY_MINUS, labels.get(2).value(SyntaxValue.Operator.class).operator());
      assertEquals(List.of(SyntaxRole.OPERAND), labels.get(2).children().stream().map(child -> child.role()).toList());
      assertEquals(SyntaxKind.LITERAL, labels.get(2).requiredChild(SyntaxRole.OPERAND).syntaxKind());
      assertSpan(source, labels.get(2).requiredChild(SyntaxRole.OPERAND), "1", source.indexOf("case -1") + 6);
      var cast = labels.get(3);
      assertEquals(SyntaxKind.TYPE_CAST, cast.syntaxKind());
      assertEquals(List.of(SyntaxRole.EXPRESSION, SyntaxRole.TYPE_OPERAND), cast.children().stream().map(child -> child.role()).toList());
      assertEquals(SyntaxKind.TYPE_USE, cast.requiredChild(SyntaxRole.TYPE_OPERAND).syntaxKind());
      assertEquals(SyntaxKind.LITERAL, cast.requiredChild(SyntaxRole.EXPRESSION).syntaxKind());
      assertSpan(source, cast.requiredChild(SyntaxRole.TYPE_OPERAND), "int", source.indexOf("(int) 4.0") + 1);
      assertSpan(source, cast.requiredChild(SyntaxRole.EXPRESSION), "4.0", source.indexOf("(int) 4.0") + 6);
      assertEquals("int", cast.resolvedType().orElseThrow().erasedName());
      var parenthesized = labels.get(4);
      assertEquals(SyntaxKind.PARENTHESIZED, parenthesized.syntaxKind());
      var conditional = parenthesized.requiredChild(SyntaxRole.EXPRESSION);
      assertEquals(SyntaxKind.CONDITIONAL_EXPRESSION, conditional.syntaxKind());
      assertEquals(List.of(SyntaxRole.CONDITION, SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION), conditional.children().stream().map(child -> child.role()).toList());
      assertEquals(List.of("true", "5", "6"), conditional.children().stream().map(child -> written(source, child.node())).toList());
      for (var child : conditional.children()) {
        assertEquals(SyntaxKind.LITERAL, child.node().syntaxKind());
        assertEquals(conditional.structuralPath().child(child.role(), 0), child.node().structuralPath());
      }
      var text = selections.stream().filter(node -> node.children(SyntaxRole.CASE).size() == 2).findFirst().orElseThrow();
      assertBinary(source, text.children(SyntaxRole.CASE).getFirst().children(SyntaxRole.LABEL).getFirst(), SyntaxOperator.PLUS, "\"a\"", "\"b\"");
      for (var selection : selections) {
        var groups = selection.children(SyntaxRole.CASE);
        for (var index = 0; index < groups.size(); index++) {
          var group = groups.get(index);
          assertEquals(selection.structuralPath().child(SyntaxRole.CASE, index), group.structuralPath());
          for (var label : group.children(SyntaxRole.LABEL)) {
            assertEquals(group.structuralPath().child(SyntaxRole.LABEL, 0), label.structuralPath());
            assertSpan(source, label, written(source, label), source.indexOf(written(source, label), source.indexOf("case ")));
          }
        }
      }
    }
  }

  private static SyntaxNode conditional(AstResult ast, String source, String condition) {
    var matches = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.IF
        && written(source, node.requiredChild(SyntaxRole.CONDITION)).equals(condition)).toList();
    assertEquals(1, matches.size());
    return matches.getFirst();
  }

  private static void assertBranch(String source, SyntaxNode owner, SyntaxRole role, String text) {
    var branch = owner.requiredChild(role);
    assertEquals(SyntaxKind.EXPRESSION_STATEMENT, branch.syntaxKind());
    assertEquals(owner.structuralPath().child(role, 0), branch.structuralPath());
    assertSpan(source, branch, text, source.indexOf(text));
  }

  private static void assertBinary(String source, SyntaxNode node, SyntaxOperator operator, String left, String right) {
    assertEquals(SyntaxKind.BINARY, node.syntaxKind());
    assertEquals(operator, node.value(SyntaxValue.Operator.class).operator());
    assertEquals(List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND), node.children().stream().map(child -> child.role()).toList());
    var start = Math.toIntExact(node.origin().orElseThrow().startOffset());
    assertSpan(source, node.requiredChild(SyntaxRole.LEFT_OPERAND), left, start);
    assertSpan(source, node.requiredChild(SyntaxRole.RIGHT_OPERAND), right, start + written(source, node).length() - right.length());
    for (var child : node.children()) assertEquals(node.structuralPath().child(child.role(), 0), child.node().structuralPath());
  }

  private static String written(String source, SyntaxNode node) {
    var span = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }

  private record BranchCase(String statement, SyntaxKind wrapper, boolean outerElse, boolean innerElse) {}
  private record ArrayCase(String method, String expression, String qualifier, SyntaxKind qualifierKind) {}

  private List<AstResult> produceAndDecode(String source) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
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

  private static SyntaxNode only(AstResult ast, SyntaxKind kind) {
    var nodes = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind).toList();
    assertEquals(1, nodes.size(), kind.toString());
    return nodes.getFirst();
  }

  private static void assertSpan(String source, SyntaxNode node, String text, int start) {
    assertTrue(start >= 0, text);
    assertEquals(new SourceSpan(node.sourceFileKey(), start, start + text.length()), node.origin().orElseThrow());
    assertEquals(text, source.substring(start, start + text.length()));
  }
}
