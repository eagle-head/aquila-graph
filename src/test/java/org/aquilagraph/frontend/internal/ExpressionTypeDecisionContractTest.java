package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.aquilagraph.source.api.ExecutionSyntax;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ExpressionTypeDecisionContractTest {
  private static final String PARAMETERS = "int i, long l, byte b, short s, char c, float f, double d, Integer boxed, Long wide, Byte small, "
      + "Double decimal, Float fraction, boolean p, Boolean flag, Boolean otherFlag, Integer otherBoxed, Object reference, String text";

  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenSimpleAssignments_whenProducedAndDecoded_thenLocationsValuesAndSelectedTargetsRemainExact() {
    return Stream.of(
        new AssignmentCase("int local = 0; local = small;", "local = small", "local", "small", "int",
            SyntaxKind.VARIABLE_ACCESS, unbox("java.lang.Byte", "byte", "int")),
        new AssignmentCase("this.field = boxed;", "this.field = boxed", "this.field", "boxed", "long",
            SyntaxKind.FIELD_ACCESS, unbox("java.lang.Integer", "int", "long")),
        new AssignmentCase("array[0] = small;", "array[0] = small", "array[0]", "small", "int",
            SyntaxKind.ARRAY_ACCESS, unbox("java.lang.Byte", "byte", "int")),
        new AssignmentCase("int first = 0; int second = 0; first = second = b;", "first = second = b", "first", "second = b", "int",
            SyntaxKind.VARIABLE_ACCESS, plain("int", "int")),
        new AssignmentCase("int first = 0; int second = 0; first = second = b;", "second = b", "second", "b", "int",
            SyntaxKind.VARIABLE_ACCESS, plain("byte", "int")),
        new AssignmentCase("Object value = null; value = i;", "value = i", "value", "i", "java.lang.Object",
            SyntaxKind.VARIABLE_ACCESS, box("int", "java.lang.Object", "int", "java.lang.Integer")),
        new AssignmentCase("(enclosing = other).new Inner();", "enclosing = other", "enclosing", "other", "Example",
            SyntaxKind.VARIABLE_ACCESS, plain("Example", "Example")))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = "class Example { long field; class Inner {} void subject(int[] array, byte b, int i, Byte small, Integer boxed, "
              + "Example enclosing, Example other) { " + variant.statements() + " } }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var assignment = expression(ast, source, variant.expression(), SyntaxKind.ASSIGNMENT);
            assertType(variant.type(), assignment);
            assertChildren(ast, source, assignment, List.of(SyntaxRole.LOCATION, SyntaxRole.VALUE),
                List.of(variant.location(), variant.value()));
            assertEquals(variant.locationKind(), assignment.requiredChild(SyntaxRole.LOCATION).syntaxKind());
            assertType(variant.type(), assignment.requiredChild(SyntaxRole.LOCATION));
            assertEquals(variant.type(), variant.use().target());
            assertUse(subject(ast), assignment, SyntaxRole.VALUE, variant.use());
            assertTrue(subject(ast).useDecision(assignment.nodeId(), SyntaxRole.LOCATION, 0).isEmpty());
            if (variant.type().equals("Example")) {
              var creation = expression(ast, source, "(enclosing = other).new Inner()", SyntaxKind.OBJECT_CREATION);
              var enclosing = creation.requiredChild(SyntaxRole.ENCLOSING_INSTANCE);
              assertEquals(SyntaxKind.PARENTHESIZED, enclosing.syntaxKind());
              assertEquals(assignment.nodeId(), enclosing.requiredChild(SyntaxRole.EXPRESSION).nodeId());
            }
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenPrimitiveBoxedAndNestedUnaryOperands_whenProducedAndDecoded_thenOperatorAndUseTypesRemainDistinct() {
    return Stream.of(
        new UnaryCase("+b", SyntaxOperator.UNARY_PLUS, "b", "int", plain("byte", "int")),
        new UnaryCase("+small", SyntaxOperator.UNARY_PLUS, "small", "int", unbox("java.lang.Byte", "byte", "int")),
        new UnaryCase("-l", SyntaxOperator.UNARY_MINUS, "l", "long", plain("long", "long")),
        new UnaryCase("-decimal", SyntaxOperator.UNARY_MINUS, "decimal", "double", unbox("java.lang.Double", "double", "double")),
        new UnaryCase("~c", SyntaxOperator.BITWISE_COMPLEMENT, "c", "int", plain("char", "int")),
        new UnaryCase("~wide", SyntaxOperator.BITWISE_COMPLEMENT, "wide", "long", unbox("java.lang.Long", "long", "long")),
        new UnaryCase("!p", SyntaxOperator.LOGICAL_COMPLEMENT, "p", "boolean", plain("boolean", "boolean")),
        new UnaryCase("!flag", SyntaxOperator.LOGICAL_COMPLEMENT, "flag", "boolean", unbox("java.lang.Boolean", "boolean", "boolean")),
        new UnaryCase("-((+small))", SyntaxOperator.UNARY_MINUS, "((+small))", "int", plain("int", "int")),
        new UnaryCase("!((!flag))", SyntaxOperator.LOGICAL_COMPLEMENT, "((!flag))", "boolean", plain("boolean", "boolean")))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = expressionSource(variant.result(), variant.expression());

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var unary = expression(ast, source, variant.expression(), SyntaxKind.UNARY);
            assertEquals(variant.operator(), unary.value(SyntaxValue.Operator.class).operator());
            assertType(variant.result(), unary);
            assertChildren(ast, source, unary, List.of(SyntaxRole.OPERAND), List.of(variant.operand()));
            assertUse(subject(ast), unary, SyntaxRole.OPERAND, variant.use());
            if (variant.expression().equals("-((+small))")) {
              var parentheses = unary.requiredChild(SyntaxRole.OPERAND);
              assertEquals(SyntaxKind.PARENTHESIZED, parentheses.syntaxKind());
              var inner = parentheses.requiredChild(SyntaxRole.EXPRESSION).requiredChild(SyntaxRole.EXPRESSION);
              assertEquals(SyntaxOperator.UNARY_PLUS, inner.value(SyntaxValue.Operator.class).operator());
              assertUse(subject(ast), inner, SyntaxRole.OPERAND, unbox("java.lang.Byte", "byte", "int"));
            } else if (variant.expression().equals("!((!flag))")) {
              var inner = unary.requiredChild(SyntaxRole.OPERAND).requiredChild(SyntaxRole.EXPRESSION).requiredChild(SyntaxRole.EXPRESSION);
              assertEquals(SyntaxOperator.LOGICAL_COMPLEMENT, inner.value(SyntaxValue.Operator.class).operator());
              assertUse(subject(ast), inner, SyntaxRole.OPERAND, unbox("java.lang.Boolean", "boolean", "boolean"));
            }
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenIntegralFloatingAndBoxedArithmetic_whenProducedAndDecoded_thenPromotionAndWrittenGroupingRemainExact() {
    var multiplicative = Stream.of(new OperatorCase("*", SyntaxOperator.MULTIPLY), new OperatorCase("/", SyntaxOperator.DIVIDE),
        new OperatorCase("%", SyntaxOperator.REMAINDER)).flatMap(operator -> Stream.of(
            binary("i", operator, "l", "long", plain("int", "long"), plain("long", "long")),
            binary("small", operator, "fraction", "float", unbox("java.lang.Byte", "byte", "float"), unbox("java.lang.Float", "float", "float")),
            binary("decimal", operator, "s", "double", unbox("java.lang.Double", "double", "double"), plain("short", "double")),
            binary("f", operator, "d", "double", plain("float", "double"), plain("double", "double"))));
    var additive = Stream.of(new OperatorCase("+", SyntaxOperator.PLUS), new OperatorCase("-", SyntaxOperator.MINUS))
        .flatMap(operator -> Stream.of(
            binary("l", operator, "boxed", "long", plain("long", "long"), unbox("java.lang.Integer", "int", "long")),
            binary("fraction", operator, "i", "float", unbox("java.lang.Float", "float", "float"), plain("int", "float")),
            binary("i", operator, "decimal", "double", plain("int", "double"), unbox("java.lang.Double", "double", "double")),
            binary("f", operator, "d", "double", plain("float", "double"), plain("double", "double"))));
    return Stream.concat(multiplicative, additive).map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
      // Arrange
      var source = expressionSource(variant.result(), variant.expression());

      // Act
      var results = produceAndDecode(source);

      // Assert
      for (var ast : results) {
        assertBinary(ast, source, variant);
      }
    }));
  }

  @Test
  void givenLongConditionalAdditionAndNestedSubtraction_whenProducedAndDecoded_thenGroupingAndNumericDecisionsSurvive() {
    // Arrange
    var text = "p ? l + boxed : (i - small) + l - b";
    var source = expressionSource("long", text);

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var conditional = expression(ast, source, text, SyntaxKind.CONDITIONAL_EXPRESSION);
      assertChildren(ast, source, conditional, List.of(SyntaxRole.CONDITION, SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION),
          List.of("p", "l + boxed", "(i - small) + l - b"));
      assertType("long", conditional);
      assertUse(subject(ast), conditional, SyntaxRole.TRUE_EXPRESSION, plain("long", "long"));
      assertUse(subject(ast), conditional, SyntaxRole.FALSE_EXPRESSION, plain("long", "long"));
      assertBinary(ast, source, new BinaryCase("l + boxed", SyntaxOperator.PLUS, "l", "boxed", "long",
          plain("long", "long"), unbox("java.lang.Integer", "int", "long")));
      assertBinary(ast, source, new BinaryCase("i - small", SyntaxOperator.MINUS, "i", "small", "int",
          plain("int", "int"), unbox("java.lang.Byte", "byte", "int")));
      assertBinary(ast, source, new BinaryCase("(i - small) + l", SyntaxOperator.PLUS, "(i - small)", "l", "long",
          plain("int", "long"), plain("long", "long")));
      assertBinary(ast, source, new BinaryCase("(i - small) + l - b", SyntaxOperator.MINUS, "(i - small) + l", "b", "long",
          plain("long", "long"), plain("byte", "long")));
      var parentheses = expression(ast, source, "(i - small)", SyntaxKind.PARENTHESIZED);
      assertEquals(expression(ast, source, "i - small", SyntaxKind.BINARY).nodeId(),
          parentheses.requiredChild(SyntaxRole.EXPRESSION).nodeId());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenNumericComparisonsInBranchesAndLoops_whenProducedAndDecoded_thenBooleanResultsKeepPromotedOperandFacts() {
    return Stream.of(new OperatorCase("<", SyntaxOperator.LESS_THAN), new OperatorCase(">", SyntaxOperator.GREATER_THAN),
        new OperatorCase("<=", SyntaxOperator.LESS_THAN_EQUAL), new OperatorCase(">=", SyntaxOperator.GREATER_THAN_EQUAL))
        .flatMap(operator -> Stream.of(
            binary("i", operator, "l", "boolean", plain("int", "long"), plain("long", "long")),
            binary("boxed", operator, "decimal", "boolean", unbox("java.lang.Integer", "int", "double"),
                unbox("java.lang.Double", "double", "double"))))
        .flatMap(variant -> Stream.of("if", "while").map(context -> DynamicTest.dynamicTest(context + " " + variant.expression(), () -> {
          // Arrange
          var statement = context.equals("if") ? "if (" + variant.expression() + ") return true; return false;"
              : "while (" + variant.expression() + ") { return true; } return false;";
          var source = "class Example { boolean subject(" + PARAMETERS + ") { " + statement + " } }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            assertBinary(ast, source, variant);
            var control = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind()
                == (context.equals("if") ? SyntaxKind.IF : SyntaxKind.WHILE_LOOP)).findFirst().orElseThrow();
            var condition = control.requiredChild(SyntaxRole.CONDITION);
            assertEquals(SyntaxKind.PARENTHESIZED, condition.syntaxKind());
            assertEquals(expression(ast, source, variant.expression(), SyntaxKind.BINARY).nodeId(),
                condition.requiredChild(SyntaxRole.EXPRESSION).nodeId());
            assertUse(subject(ast), control, SyntaxRole.CONDITION, plain("boolean", "boolean"));
          }
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenNumericBooleanAndReferenceEquality_whenProducedAndDecoded_thenOnlyApplicableOperandsAreUnboxed() {
    return Stream.of(new OperatorCase("==", SyntaxOperator.EQUAL_TO), new OperatorCase("!=", SyntaxOperator.NOT_EQUAL_TO))
        .flatMap(operator -> Stream.of(
            binary("i", operator, "l", "boolean", plain("int", "long"), plain("long", "long")),
            binary("boxed", operator, "i", "boolean", unbox("java.lang.Integer", "int", "int"), plain("int", "int")),
            binary("p", operator, "flag", "boolean", plain("boolean", "boolean"), unbox("java.lang.Boolean", "boolean", "boolean")),
            binary("flag", operator, "otherFlag", "boolean", plain("java.lang.Boolean", "java.lang.Boolean"), plain("java.lang.Boolean", "java.lang.Boolean")),
            binary("boxed", operator, "otherBoxed", "boolean", plain("java.lang.Integer", "java.lang.Integer"), plain("java.lang.Integer", "java.lang.Integer")),
            binary("reference", operator, "text", "boolean", plain("java.lang.Object", "java.lang.Object"), plain("java.lang.String", "java.lang.String")),
            binary("text", operator, "null", "boolean", plain("java.lang.String", "java.lang.String"), plain("<nulltype>", "<nulltype>")),
            binary("null", operator, "reference", "boolean", plain("<nulltype>", "<nulltype>"), plain("java.lang.Object", "java.lang.Object"))))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = expressionSource("boolean", variant.expression());

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            assertBinary(ast, source, variant);
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenIntegralAndEagerBooleanOperators_whenProducedAndDecoded_thenBitwiseDecisionsRemainDistinct() {
    return Stream.of(new OperatorCase("&", SyntaxOperator.AND), new OperatorCase("^", SyntaxOperator.XOR), new OperatorCase("|", SyntaxOperator.OR))
        .flatMap(operator -> Stream.of(
            binary("b", operator, "s", "int", plain("byte", "int"), plain("short", "int")),
            binary("boxed", operator, "wide", "long", unbox("java.lang.Integer", "int", "long"), unbox("java.lang.Long", "long", "long")),
            binary("p", operator, "flag", "boolean", plain("boolean", "boolean"), unbox("java.lang.Boolean", "boolean", "boolean")),
            binary("flag", operator, "otherFlag", "boolean", unbox("java.lang.Boolean", "boolean", "boolean"), unbox("java.lang.Boolean", "boolean", "boolean"))))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = expressionSource(variant.result(), variant.expression());

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            assertBinary(ast, source, variant);
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenPrimitiveAndBoxedShortCircuitOperands_whenProducedAndDecoded_thenOrderedBooleanUsesRemainExact() {
    return Stream.of(new OperatorCase("&&", SyntaxOperator.CONDITIONAL_AND), new OperatorCase("||", SyntaxOperator.CONDITIONAL_OR))
        .flatMap(operator -> Stream.of(
            binary("p", operator, "flag", "boolean", plain("boolean", "boolean"), unbox("java.lang.Boolean", "boolean", "boolean")),
            binary("flag", operator, "p", "boolean", unbox("java.lang.Boolean", "boolean", "boolean"), plain("boolean", "boolean")),
            binary("flag", operator, "otherFlag", "boolean", unbox("java.lang.Boolean", "boolean", "boolean"), unbox("java.lang.Boolean", "boolean", "boolean")),
            binary("p", operator, "true", "boolean", plain("boolean", "boolean"), plain("boolean", "boolean"))))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = expressionSource("boolean", variant.expression());

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var kind = variant.operator() == SyntaxOperator.CONDITIONAL_AND ? SyntaxKind.CONDITIONAL_AND : SyntaxKind.CONDITIONAL_OR;
            assertBinary(ast, source, variant, kind);
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenPatternTestsInValidShortCircuitScopes_whenProducedAndDecoded_thenReadsSelectTheExactPatternDeclaration() {
    return Stream.of("reference instanceof String match && match.isEmpty()", "!(reference instanceof String match) || match.isEmpty()")
        .map(text -> DynamicTest.dynamicTest(text, () -> {
          // Arrange
          var source = expressionSource("boolean", text);
          var conjunction = text.contains("&&");

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var kind = conjunction ? SyntaxKind.CONDITIONAL_AND : SyntaxKind.CONDITIONAL_OR;
            var operator = conjunction ? SyntaxOperator.CONDITIONAL_AND : SyntaxOperator.CONDITIONAL_OR;
            var left = conjunction ? "reference instanceof String match" : "!(reference instanceof String match)";
            assertBinary(ast, source, new BinaryCase(text, operator, left, "match.isEmpty()", "boolean",
                plain("boolean", "boolean"), plain("boolean", "boolean")), kind);
            var pattern = expression(ast, source, "String match", SyntaxKind.BINDING_PATTERN);
            var binding = pattern.requiredChild(SyntaxRole.PARAMETER);
            var key = binding.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
            var read = expression(ast, source, "match.isEmpty()", SyntaxKind.INVOCATION).requiredChild(SyntaxRole.RECEIVER);
            assertEquals(SyntaxKind.VARIABLE_ACCESS, read.syntaxKind());
            assertEquals(key, read.requiredReference().symbolKey().orElseThrow());
            assertEquals(List.of(read.origin().orElseThrow()), pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow());
            assertEquals("java.lang.String", read.resolvedType().orElseThrow().erasedName());
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenConditionalTypingRegimes_whenProducedAndDecoded_thenConditionAndBranchTargetsMatchEachRegime() {
    return Stream.of(
        new ConditionalCase("flag", "p", "true", "boolean", plain("boolean", "boolean"), plain("boolean", "boolean")),
        new ConditionalCase("p", "flag", "otherFlag", "java.lang.Boolean", plain("java.lang.Boolean", "java.lang.Boolean"), plain("java.lang.Boolean", "java.lang.Boolean")),
        new ConditionalCase("p", "flag", "false", "boolean", unbox("java.lang.Boolean", "boolean", "boolean"), plain("boolean", "boolean")),
        new ConditionalCase("p", "b", "1", "byte", plain("byte", "byte"), plain("int", "byte")),
        new ConditionalCase("p", "127", "small", "byte", plain("int", "byte"), unbox("java.lang.Byte", "byte", "byte")),
        new ConditionalCase("p", "b", "128", "int", plain("byte", "int"), plain("int", "int")),
        new ConditionalCase("p", "s", "32767", "short", plain("short", "short"), plain("int", "short")),
        new ConditionalCase("p", "c", "65535", "char", plain("char", "char"), plain("int", "char")),
        new ConditionalCase("p", "small", "s", "short", unbox("java.lang.Byte", "byte", "short"), plain("short", "short")),
        new ConditionalCase("p", "wide", "i", "long", unbox("java.lang.Long", "long", "long"), plain("int", "long")),
        new ConditionalCase("p", "null", "text", "java.lang.String", plain("<nulltype>", "java.lang.String"), plain("java.lang.String", "java.lang.String")),
        new ConditionalCase("p", "text", "null", "java.lang.Object", plain("java.lang.String", "java.lang.Object"), plain("<nulltype>", "java.lang.Object")),
        new ConditionalCase("p", "text", "i", "java.lang.Object", plain("java.lang.String", "java.lang.Object"), box("int", "java.lang.Object", "int", "java.lang.Integer")),
        new ConditionalCase("p", "(flag ? b : 1)", "s", "short", plain("byte", "short"), plain("short", "short")))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = expressionSource(variant.result(), variant.expression());

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            assertConditional(ast, source, variant);
            if (variant.whenTrue().equals("(flag ? b : 1)")) {
              assertConditional(ast, source, new ConditionalCase("flag", "b", "1", "byte", plain("byte", "byte"), plain("int", "byte")));
              var parentheses = expression(ast, source, variant.whenTrue(), SyntaxKind.PARENTHESIZED);
              assertEquals(expression(ast, source, "flag ? b : 1", SyntaxKind.CONDITIONAL_EXPRESSION).nodeId(),
                  parentheses.requiredChild(SyntaxRole.EXPRESSION).nodeId());
            }
          }
        }));
  }

  @Test
  void givenGenericPolyConditionalInInvocationContext_whenProducedAndDecoded_thenBranchResultsAndFormalTargetRetainTypeArguments() {
    // Arrange
    var source = "import java.util.List; class Example { static <T> List<T> empty() { return null; } "
        + "static void consume(List<String> values) {} void subject(boolean p, List<String> values) { consume(p ? empty() : values); } }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var node = expression(ast, source, "p ? empty() : values", SyntaxKind.CONDITIONAL_EXPRESSION);
      assertChildren(ast, source, node, List.of(SyntaxRole.CONDITION, SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION),
          List.of("p", "empty()", "values"));
      assertEquals("java.util.List<java.lang.String>", node.resolvedType().orElseThrow().displayName());
      for (var role : List.of(SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION)) {
        var use = subject(ast).useDecision(node.nodeId(), role, 0).orElseThrow();
        assertEquals("java.util.List<java.lang.String>", use.sourceType().displayName());
        assertEquals("java.util.List<java.lang.String>", use.selectedTarget().displayName());
        assertUse(subject(ast), node, role, plain("java.util.List", "java.util.List"));
      }
      assertUse(subject(ast), node, SyntaxRole.CONDITION, plain("boolean", "boolean"));
      var call = expression(ast, source, "consume(p ? empty() : values)", SyntaxKind.INVOCATION);
      assertEquals("java.util.List<java.lang.String>", subject(ast).useDecision(call.nodeId(), SyntaxRole.ARGUMENT, 0)
          .orElseThrow().selectedTarget().displayName());
      assertEquals(new JavaTypeDeclarationIdentity(Optional.empty(), "Example").declarationKey() + "#empty()",
          node.requiredChild(SyntaxRole.TRUE_EXPRESSION).requiredReference().symbolKey().orElseThrow());
    }
  }

  @Test
  void givenGenericPolyConditionalInAssignmentContext_whenProducedAndDecoded_thenBothBranchesKeepTheExpectedParameterization() {
    // Arrange
    var source = "import java.util.List; class Example { static <T> List<T> empty() { return null; } "
        + "void subject(boolean p, List<String> values) { List<String> selected = p ? values : empty(); } }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var node = expression(ast, source, "p ? values : empty()", SyntaxKind.CONDITIONAL_EXPRESSION);
      assertChildren(ast, source, node, List.of(SyntaxRole.CONDITION, SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION),
          List.of("p", "values", "empty()"));
      assertEquals("java.util.List<java.lang.String>", node.resolvedType().orElseThrow().displayName());
      for (var role : List.of(SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION)) {
        assertUse(subject(ast), node, role, plain("java.util.List", "java.util.List"));
        var use = subject(ast).useDecision(node.nodeId(), role, 0).orElseThrow();
        assertEquals("java.util.List<java.lang.String>", use.sourceType().displayName());
        assertEquals("java.util.List<java.lang.String>", use.selectedTarget().displayName());
      }
      assertUse(subject(ast), node, SyntaxRole.CONDITION, plain("boolean", "boolean"));
      var declaration = ast.aggregate().nodes().stream().filter(candidate -> candidate.syntaxKind() == SyntaxKind.LOCAL_DECLARATION
          && candidate.value(SyntaxValue.LocalDeclaration.class).name().equals("selected")).findFirst().orElseThrow();
      assertEquals(node.nodeId(), declaration.requiredChild(SyntaxRole.INITIALIZER).nodeId());
      assertEquals("java.util.List<java.lang.String>", subject(ast).useDecision(declaration.nodeId(), SyntaxRole.INITIALIZER, 0)
          .orElseThrow().selectedTarget().displayName());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenMixedWidthShiftOperands_whenProducedAndDecoded_thenEachOperandHasItsOwnUnaryPromotion() {
    return Stream.of(new OperatorCase("<<", SyntaxOperator.LEFT_SHIFT), new OperatorCase(">>", SyntaxOperator.RIGHT_SHIFT),
        new OperatorCase(">>>", SyntaxOperator.UNSIGNED_RIGHT_SHIFT)).flatMap(operator -> Stream.of(
            binary("i", operator, "l", "int", plain("int", "int"), plain("long", "long")),
            binary("l", operator, "i", "long", plain("long", "long"), plain("int", "int")),
            binary("b", operator, "c", "int", plain("byte", "int"), plain("char", "int")),
            binary("small", operator, "wide", "int", unbox("java.lang.Byte", "byte", "int"), unbox("java.lang.Long", "long", "long")),
            binary("wide", operator, "small", "long", unbox("java.lang.Long", "long", "long"), unbox("java.lang.Byte", "byte", "int")),
            binary("boxed", operator, "s", "int", unbox("java.lang.Integer", "int", "int"), plain("short", "int"))))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = expressionSource(variant.result(), variant.expression());

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            assertBinary(ast, source, variant);
          }
        }));
  }

  private void assertBinary(AstResult ast, String source, BinaryCase variant) {
    assertBinary(ast, source, variant, SyntaxKind.BINARY);
  }

  private void assertBinary(AstResult ast, String source, BinaryCase variant, SyntaxKind kind) {
    var node = expression(ast, source, variant.expression(), kind);
    assertEquals(variant.operator(), node.value(SyntaxValue.Operator.class).operator());
    assertType(variant.result(), node);
    assertChildren(ast, source, node, List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND),
        List.of(variant.left(), variant.right()));
    var body = subject(ast);
    assertUse(body, node, SyntaxRole.LEFT_OPERAND, variant.leftUse());
    assertUse(body, node, SyntaxRole.RIGHT_OPERAND, variant.rightUse());
    assertTrue(body.stringConcatenation(node.nodeId()).isEmpty());
  }

  private static void assertConditional(AstResult ast, String source, ConditionalCase variant) {
    var node = expression(ast, source, variant.expression(), SyntaxKind.CONDITIONAL_EXPRESSION);
    assertType(variant.result(), node);
    assertChildren(ast, source, node, List.of(SyntaxRole.CONDITION, SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION),
        List.of(variant.condition(), variant.whenTrue(), variant.whenFalse()));
    assertUse(subject(ast), node, SyntaxRole.CONDITION, variant.condition().equals("flag")
        ? unbox("java.lang.Boolean", "boolean", "boolean") : plain("boolean", "boolean"));
    assertUse(subject(ast), node, SyntaxRole.TRUE_EXPRESSION, variant.trueUse());
    assertUse(subject(ast), node, SyntaxRole.FALSE_EXPRESSION, variant.falseUse());
  }

  private static String expressionSource(String result, String expression) {
    return "class Example { " + result + " subject(" + PARAMETERS + ") { return " + expression + "; } }";
  }

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

  private static ExecutionSyntax subject(AstResult ast) {
    var keys = ast.aggregate().methodBodyKeys().stream().filter(key -> key.contains("#subject(")).toList();
    assertEquals(1, keys.size());
    return ast.aggregate().methodSyntax(keys.getFirst()).orElseThrow();
  }

  private static SyntaxNode expression(AstResult ast, String source, String text, SyntaxKind kind) {
    var start = source.indexOf(text);
    assertTrue(start >= 0, text);
    var matches = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind
        && node.origin().equals(Optional.of(new SourceSpan(node.sourceFileKey(), start, start + text.length())))).toList();
    assertEquals(1, matches.size(), kind + " " + text);
    return matches.getFirst();
  }

  private static void assertChildren(AstResult ast, String source, SyntaxNode parent, List<SyntaxRole> roles, List<String> texts) {
    assertEquals(roles, parent.children().stream().map(child -> child.role()).toList());
    var cursor = Math.toIntExact(parent.origin().orElseThrow().startOffset());
    for (var index = 0; index < roles.size(); index++) {
      var child = parent.children().get(index);
      var text = texts.get(index);
      var start = source.indexOf(text, cursor);
      assertEquals(0, child.position());
      assertEquals(new SourceSpan(child.node().sourceFileKey(), start, start + text.length()), child.node().origin().orElseThrow());
      assertEquals(parent.structuralPath().child(roles.get(index), 0), child.node().structuralPath());
      assertEquals(1, ast.aggregate().nodes().stream().filter(node -> node.nodeId().equals(child.node().nodeId())).count());
      cursor = start + text.length();
    }
  }

  private static void assertType(String expected, SyntaxNode node) {
    assertEquals(expected, node.resolvedType().orElseThrow().erasedName());
  }

  private static void assertUse(ExecutionSyntax body, SyntaxNode consumer, SyntaxRole role, Use expected) {
    var use = body.useDecision(consumer.nodeId(), role, 0).orElseThrow();
    assertEquals(body.scopeKey(), use.scopeKey());
    assertEquals(consumer.nodeId(), use.consumerNodeId());
    assertEquals(role, use.role());
    assertEquals(0, use.position());
    assertType(expected.source(), consumer.requiredChild(role));
    assertEquals(expected.source(), use.sourceType().erasedName());
    assertEquals(expected.target(), use.selectedTarget().erasedName());
    assertEquals(Optional.ofNullable(expected.unboxed()), use.unboxedType().map(type -> type.erasedName()));
    assertEquals(Optional.ofNullable(expected.unboxed() == null ? null : expected.target()),
        use.effectivePrimitiveType().map(type -> type.erasedName()));
    assertEquals(Optional.ofNullable(expected.preBox()), use.preBoxPrimitiveType().map(type -> type.erasedName()));
    assertEquals(Optional.ofNullable(expected.boxed()), use.boxedType().map(type -> type.erasedName()));
    assertTrue(use.checkedReferenceTarget().isEmpty());
  }

  private static Use plain(String source, String target) {
    return new Use(source, target, null, null, null);
  }

  private static Use unbox(String source, String unboxed, String target) {
    return new Use(source, target, unboxed, null, null);
  }

  private static Use box(String source, String target, String preBox, String boxed) {
    return new Use(source, target, null, preBox, boxed);
  }

  private static BinaryCase binary(String left, OperatorCase operator, String right, String result, Use leftUse, Use rightUse) {
    return new BinaryCase(left + " " + operator.text() + " " + right, operator.operator(), left, right, result, leftUse, rightUse);
  }

  private record OperatorCase(String text, SyntaxOperator operator) {}
  private record Use(String source, String target, String unboxed, String preBox, String boxed) {}
  private record BinaryCase(String expression, SyntaxOperator operator, String left, String right, String result, Use leftUse, Use rightUse) {}
  private record AssignmentCase(String statements, String expression, String location, String value, String type, SyntaxKind locationKind, Use use) {}
  private record UnaryCase(String expression, SyntaxOperator operator, String operand, String result, Use use) {}
  private record ConditionalCase(String condition, String whenTrue, String whenFalse, String result, Use trueUse, Use falseUse) {
    String expression() { return condition + " ? " + whenTrue + " : " + whenFalse; }
  }
}
