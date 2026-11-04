package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SourceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;

class WrittenExpressionFidelityTest {
  @TempDir Path directory;

  @Test
  void givenNestedWrittenRegionsWithTheSameStart_whenTheSameRequestIsRepeated_thenCoverageEncodingAndIdentityAreDeterministic() {
    // Arrange
    var source = "class Example { String value() { return -1 * 2 + \"a\" + \"b\"; } int neighbor() { return -2; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_FIDELITY);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var outer = SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 0).child(SourceOwnerPath.Role.MEMBER, 0)
        .child(SourceOwnerPath.Role.BODY, 0).child(SourceOwnerPath.Role.STATEMENT, 0).child(SourceOwnerPath.Role.EXPRESSION, 0);
    var nested = outer.child(SourceOwnerPath.Role.LEFT_OPERAND, 0).child(SourceOwnerPath.Role.LEFT_OPERAND, 0);
    var neighbor = SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 0).child(SourceOwnerPath.Role.MEMBER, 1)
        .child(SourceOwnerPath.Role.BODY, 0).child(SourceOwnerPath.Role.STATEMENT, 0).child(SourceOwnerPath.Role.EXPRESSION, 0);
    var expectedOwners = List.of(nested, outer, neighbor);

    // Act
    var results = Stream.generate(() -> frontend.produce(request).result().orElseThrow()).limit(12).toList();

    // Assert
    var first = results.getFirst();
    var encoded = AstCodec.encode(first);
    for (var result : results) {
      assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
      assertTrue(result.diagnostics().isEmpty());
      var inventory = result.coverage().validationEvidence().writtenInventory();
      var regions = inventory.writtenExpressions().orElseThrow();
      assertEquals(expectedOwners, regions.stream().map(region -> region.compilerOwner()).toList());
      assertEquals(List.of("-1", "-1 * 2 + \"a\" + \"b\"", "-2"), regions.stream()
          .map(region -> source.substring((int) region.origin().startOffset(), (int) region.origin().endOffsetExclusive())).toList());
      assertEquals(List.of(0, 1, 2), inventory.contexts().stream()
          .filter(context -> context.address().partition() == SourceContext.Partition.WRITTEN_EXPRESSION)
          .map(context -> context.address().ordinal()).toList());
      assertEquals(first.coverage(), result.coverage());
      assertEquals(first.identity(), result.identity());
      assertArrayEquals(encoded, AstCodec.encode(result));
      var detached = AstCodec.decode(AstCodec.encode(result), result.identity());
      assertEquals(result.identity(), detached.identity());
      assertEquals(result.coverage(), detached.coverage());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenIllegalMinimumContexts_whenTheFrontendIsReused_thenCompilerErrorsDoNotContaminateLaterRequests() {
    // Arrange
    var expressions = List.of("2147483648", "-(2147483648)", "+2147483648", "9223372036854775808L", "2147483648 + \"a\" + \"b\"");

    // Act
    var observations = expressions.stream().map(expression -> DynamicTest.dynamicTest(
        "givenIllegalMinimum(" + expression + ")_whenFrontendIsReused_thenCompilerErrorsDoNotContaminateLaterRequests", () -> {
      // Arrange
      var frontend = new JavaFrontend(new JavaSourceLoader());
      var source = "class Invalid { long value() { return " + expression + "; } }";
      var invalid = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Invalid.java"), source),
          SourceFile.fromContent(Path.of("Neighbor.java"), "class Neighbor { String text() { return \"a\" + \"b\"; } }")));
      var valid = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Valid.java"),
          "class Valid { int number() { return -1; } String text() { return \"c\" + \"d\"; } }")));

      // Act
      var rejected = frontend.produce(new AstProductionRequest(new SourceInput.Snapshot(invalid),
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_FIDELITY));
      var subsequent = frontend.produce(new AstProductionRequest(new SourceInput.Snapshot(valid),
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_FIDELITY)).result().orElseThrow();

      // Assert
      assertFalse(rejected.completion() == AstCompletion.COMPLETE);
      assertTrue(rejected.diagnostics().stream().anyMatch(value -> value.code().startsWith("compiler.err.")), rejected.diagnostics().toString());
      assertEquals(AstCompletion.COMPLETE, subsequent.completion(), subsequent.diagnostics().toString());
      assertTrue(subsequent.diagnostics().isEmpty());
      assertEquals(1, subsequent.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.UNARY).count());
      assertEquals(1, subsequent.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY).count());
      assertTrue(subsequent.aggregate().nodes().stream().allMatch(node -> node.sourceFileKey().equals(valid.sourceFiles().getFirst().sourceFileKey())));
    }));

    // Assert
    return observations;
  }

  @Test
  void givenMixedNumericStringAndRefusedScopes_whenApplicationDerivesGraphs_thenWrittenSourceAndCompletionStayIndependent() {
    // Arrange
    var source = "class Example { String first() { return 1 + 2 + \"a\" + \"b\"; } "
        + "String second() { return \"a\" + \"b\" + 1 + 2; } int minimum() { return -2147483648; } "
        + "String refused(int input) { return switch(input) { default -> \"c\" + \"d\"; }; } }";
    var original = produce(source);
    var ast = AstOutcome.available(original);
    var selected = original.program().methods().stream().filter(method -> !method.name().equals("<init>"))
        .map(method -> method.methodKey()).toList();
    var runner = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());
    var request = new CfgRequest(original.identity(), selected, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
    var minimum = original.program().methods().stream().filter(method -> method.name().equals("minimum")).findFirst().orElseThrow();

    // Act
    var run = runner.deriveExisting(ast, request);
    var failed = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(minimum.methodKey(), new IllegalStateException("Declared graph construction failure")))
        .deriveExisting(ast, new CfgRequest(original.identity(), List.of(minimum.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.AVAILABLE, MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
        run.representation().cfg().orElseThrow().methods().stream().map(method -> method.state()).toList());
    assertEquals(MethodCfgState.FAILED, failed.representation().cfg().orElseThrow().methods().getFirst().state());
    assertEquals(AstCompletion.COMPLETE, run.representation().ast().completion());
    assertEquals(AstCompletion.COMPLETE, failed.representation().ast().completion());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(run.representation().ast().result().orElseThrow()));
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(failed.representation().ast().result().orElseThrow()));
    var first = occurrence(original, source, "1 + 2 + \"a\" + \"b\"");
    assertEquals("int", first.requiredChild(SyntaxRole.LEFT_OPERAND).requiredChild(SyntaxRole.LEFT_OPERAND).resolvedType().orElseThrow().displayName());
    var second = occurrence(original, source, "\"a\" + \"b\" + 1 + 2");
    assertEquals("java.lang.String", second.requiredChild(SyntaxRole.LEFT_OPERAND).requiredChild(SyntaxRole.LEFT_OPERAND).resolvedType().orElseThrow().displayName());
    assertEquals(Optional.of(new AnnotationScalar.StringValue("3ab")), original.aggregate().writtenSourceAssociation(first.nodeId())
        .orElseThrow().normalizedExpression().orElseThrow().derivedConstant());
    assertEquals(Optional.of(new AnnotationScalar.StringValue("ab12")), original.aggregate().writtenSourceAssociation(second.nodeId())
        .orElseThrow().normalizedExpression().orElseThrow().derivedConstant());
  }

  @Test
  void givenDirectDecimalNegative_whenProduced_thenWrittenUnaryAndOperandHaveDistinctOrigins() {
    // Arrange
    var source = "class Example { int value() { return -1; } }";

    // Act
    var result = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics() + " failed contexts: " + result.coverage().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED)
        .map(outcome -> outcome.address()).toList());
    var unary = occurrence(result, source, "-1");
    assertEquals(JavaSourceKind.UNARY_MINUS, unary.sourceKind());
    assertEquals(SyntaxKind.UNARY, unary.syntaxKind());
    var operand = unary.requiredChild(SyntaxRole.OPERAND);
    assertEquals(JavaSourceKind.INT_LITERAL, operand.sourceKind());
    assertEquals("1", text(operand, source));
    assertEquals(unary.structuralPath().child(SyntaxRole.OPERAND, 0), operand.structuralPath());
    assertEquals("int", unary.resolvedType().orElseThrow().displayName());
    assertEquals("int", operand.resolvedType().orElseThrow().displayName());
  }

  @Test
  void givenMixedAdditiveChain_whenProduced_thenWrittenLeftAssociationSurvivesCompilerFolding() {
    // Arrange
    var source = "class Example { String value(String x) { return x + \"a\" + \"b\"; } }";

    // Act
    var result = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics() + " failed contexts: " + result.coverage().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED)
        .map(outcome -> outcome.address()).toList());
    var addition = occurrence(result, source, "x + \"a\" + \"b\"");
    assertEquals(JavaSourceKind.PLUS, addition.sourceKind());
    var left = addition.requiredChild(SyntaxRole.LEFT_OPERAND);
    assertEquals(JavaSourceKind.PLUS, left.sourceKind());
    assertEquals("x + \"a\"", text(left, source));
    assertEquals("x", text(left.requiredChild(SyntaxRole.LEFT_OPERAND), source));
    assertEquals("\"a\"", text(left.requiredChild(SyntaxRole.RIGHT_OPERAND), source));
    assertEquals("\"b\"", text(addition.requiredChild(SyntaxRole.RIGHT_OPERAND), source));
  }

  @TestFactory
  Stream<DynamicTest> givenWrittenUnaryAndAdditiveVariants_whenProducedAndDecoded_thenGrammarAndClosedEvidenceSurvive() {
    // Arrange
    var cases = List.of(
        new ExpressionCase("ordinaryLong", "long", "-1L", "UNARY_MINUS(LONG_LITERAL{1L})"),
        new ExpressionCase("intMinimum", "int", "-2_147_483_648", "UNARY_MINUS(INT_LITERAL{2_147_483_648})"),
        new ExpressionCase("longMinimum", "long", "-9_223_372_036_854_775_808L", "UNARY_MINUS(LONG_LITERAL{9_223_372_036_854_775_808L})"),
        new ExpressionCase("floatControl", "float", "-1.5f", "UNARY_MINUS(FLOAT_LITERAL{1.5f})"),
        new ExpressionCase("exponentControl", "double", "-1e-3", "UNARY_MINUS(DOUBLE_LITERAL{1e-3})"),
        new ExpressionCase("hexControl", "int", "-0x1", "UNARY_MINUS(INT_LITERAL{0x1})"),
        new ExpressionCase("hexWidthControl", "int", "-0x80000000", "UNARY_MINUS(INT_LITERAL{0x80000000})"),
        new ExpressionCase("binaryControl", "int", "-0b1", "UNARY_MINUS(INT_LITERAL{0b1})"),
        new ExpressionCase("octalControl", "int", "-01", "UNARY_MINUS(INT_LITERAL{01})"),
        new ExpressionCase("groupedOperand", "int", "-(1)", "UNARY_MINUS(PARENTHESIZED{(1)})"),
        new ExpressionCase("literalPair", "String", "\"a\" + \"b\"", "PLUS(STRING_LITERAL{\"a\"},STRING_LITERAL{\"b\"})"),
        new ExpressionCase("literalChain", "String", "\"a\" + \"b\" + \"c\"", "PLUS(PLUS(STRING_LITERAL{\"a\"},STRING_LITERAL{\"b\"}),STRING_LITERAL{\"c\"})"),
        new ExpressionCase("dynamicPrefix", "String", "x + \"a\" + \"b\"", "PLUS(PLUS(IDENTIFIER{x},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("dynamicMiddle", "String", "\"a\" + x + \"b\" + \"c\"", "PLUS(PLUS(PLUS(STRING_LITERAL{\"a\"},IDENTIFIER{x}),STRING_LITERAL{\"b\"}),STRING_LITERAL{\"c\"})"),
        new ExpressionCase("numericPrefix", "String", "1 + 2 + \"a\" + \"b\"", "PLUS(PLUS(PLUS(INT_LITERAL{1},INT_LITERAL{2}),STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("floatingPrefix", "String", "1.5f + \"a\" + \"b\"", "PLUS(PLUS(FLOAT_LITERAL{1.5f},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("hexadecimalFloatingPrefix", "String", "0x1.fp2 + \"a\" + \"b\"", "PLUS(PLUS(DOUBLE_LITERAL{0x1.fp2},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("characterPrefix", "String", "'a' + \"b\" + \"c\"", "PLUS(PLUS(CHAR_LITERAL{'a'},STRING_LITERAL{\"b\"}),STRING_LITERAL{\"c\"})"),
        new ExpressionCase("booleanPrefix", "String", "true + \"a\" + \"b\"", "PLUS(PLUS(BOOLEAN_LITERAL{true},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("nullPrefix", "String", "null + \"a\" + \"b\"", "PLUS(PLUS(NULL_LITERAL{null},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("stringPrefix", "String", "\"a\" + \"b\" + 1 + 2", "PLUS(PLUS(PLUS(STRING_LITERAL{\"a\"},STRING_LITERAL{\"b\"}),INT_LITERAL{1}),INT_LITERAL{2})"),
        new ExpressionCase("multiplicationAnchor", "String", "1 * 2 + \"a\" + \"b\"", "PLUS(PLUS(MULTIPLY{1 * 2},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("invocationAnchor", "String", "\"a\" + \"b\" + call()", "PLUS(PLUS(STRING_LITERAL{\"a\"},STRING_LITERAL{\"b\"}),METHOD_INVOCATION{call()})"),
        new ExpressionCase("castAnchor", "String", "(int)-1 + \"a\" + \"b\"", "PLUS(PLUS(TYPE_CAST{(int)-1},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})"),
        new ExpressionCase("quotedOperators", "String", "\"+\" + /* - + */ \"-\"", "PLUS(STRING_LITERAL{\"+\"},STRING_LITERAL{\"-\"})"),
        new ExpressionCase("lineSeparator", "String", "\"a\" // + in a comment\r\n + \"b\"", "PLUS(STRING_LITERAL{\"a\"},STRING_LITERAL{\"b\"})"),
        new ExpressionCase("eligibleUnicodeMinus", "int", "\\u002d1", "UNARY_MINUS(INT_LITERAL{1})"),
        new ExpressionCase("eligibleUnicodeLiteral", "String", "\"\\u0061\" + \"b\"", "PLUS(STRING_LITERAL{\"\\u0061\"},STRING_LITERAL{\"b\"})"),
        new ExpressionCase("ineligibleUnicodeEscape", "String", "\"\\\\u0061\" + \"b\"", "PLUS(STRING_LITERAL{\"\\\\u0061\"},STRING_LITERAL{\"b\"})"),
        new ExpressionCase("textBlock", "String", "\"\"\"\n  first\n  second\n  \"\"\" + \"tail\"", "PLUS(STRING_LITERAL{\"\"\"\n  first\n  second\n  \"\"\"},STRING_LITERAL{\"tail\"})"));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.name() + "_whenProducedAndDecoded_thenWrittenGrammarAndEvidenceSurvive", () -> {
          // Arrange
          var source = "class Example { String call() { return \"dynamic\"; } " + value.returnType()
              + " value(String x) { return " + value.expression() + "; } }";

          // Act
          var result = produce(source);
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals("java-source/22", result.identity().sourceModelVersion());
          assertEquals("javac-public/14", result.identity().semanticProductionRevision());
          assertEquals(value.shape(), shape(occurrence(result, source, value.expression()), source));
          assertEquals(result.aggregate(), decoded.aggregate());
          assertEquals(result.coverage(), decoded.coverage());
          assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
          assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
        }));

    // Assert
    return observations;
  }

  @Test
  void givenMinimumDecimalOperands_whenGraphsAreDerived_thenOnlyTheCompleteNegativeRuntimeValuesAreLowered() {
    // Arrange
    var source = "class Example { int small() { return -2147483648; } long wide() { return -9223372036854775808L; } }";
    var result = produce(source);
    var methods = result.program().types().stream().flatMap(type -> type.methods().stream())
        .filter(method -> method.name().equals("small") || method.name().equals("wide")).toList();
    var request = new CfgRequest(result.identity(), methods.stream().map(method -> method.methodKey()).toList(), CfgProfile.JAVA_BOUNDED);

    // Act
    var graphs = CfgDerivers.standard().derive(result, request);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(2, graphs.methods().size());
    for (var method : graphs.methods()) {
      assertEquals(MethodCfgState.AVAILABLE, method.state(), method.toString());
      var graph = method.graph().orElseThrow();
      var values = graph.values().stream().flatMap(value -> value.literal().stream()).toList();
      assertTrue(values.contains(method.methodKey().equals(methods.stream().filter(value -> value.name().equals("small")).findFirst().orElseThrow().methodKey())
          ? "-2147483648" : "-9223372036854775808"), values.toString());
      assertFalse(values.contains("2147483648"));
      assertFalse(values.contains("9223372036854775808"));
    }
  }

  @Test
  void givenAnnotationAndDefaultRegions_whenProducedAndDecoded_thenWrittenSyntaxRemainsSeparateFromTypedValues() {
    // Arrange
    var source = "@interface Example { String text() default \"a\" + \"b\"; int number() default -2147483648; } "
        + "@Example(text=\"x\" + \"y\", number=-1) class Subject {}";

    // Act
    var result = produce(source);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals("PLUS(STRING_LITERAL{\"a\"},STRING_LITERAL{\"b\"})", shape(occurrence(result, source, "\"a\" + \"b\""), source));
    assertEquals("PLUS(STRING_LITERAL{\"x\"},STRING_LITERAL{\"y\"})", shape(occurrence(result, source, "\"x\" + \"y\""), source));
    assertEquals("UNARY_MINUS(INT_LITERAL{2147483648})", shape(occurrence(result, source, "-2147483648"), source));
    assertEquals("UNARY_MINUS(INT_LITERAL{1})", shape(occurrence(result, source, "-1"), source));
    assertEquals(result.aggregate(), decoded.aggregate());
  }

  @TestFactory
  Stream<DynamicTest> givenSupportedSourceProfiles_whenCorrectedExpressionsAreProduced_thenEveryLiveProfileUsesWrittenTopology() {
    // Arrange
    var profiles = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED, AstProfile.JAVA_SOURCE_INITIALIZATION,
        AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, AstProfile.JAVA_SOURCE_DECLARATIONS,
        AstProfile.JAVA_SOURCE_UNITS, AstProfile.JAVA_SOURCE_FIDELITY);
    var source = "class Example { int number() { return -1; } String text(String x) { return x + \"a\" + \"b\"; } }";

    // Act
    var observations = profiles.stream().map(profile -> DynamicTest.dynamicTest(
        "given" + profile.version() + "_whenProduced_thenCorrectedTopologyAndExactProfileSurvive", () -> {
          // Arrange: the enclosing factory supplies the immutable profile and source.

          // Act
          var result = produce(source, profile);
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(profile, result.profile());
          assertEquals("UNARY_MINUS(INT_LITERAL{1})", shape(occurrence(result, source, "-1"), source));
          assertEquals("PLUS(PLUS(IDENTIFIER{x},STRING_LITERAL{\"a\"}),STRING_LITERAL{\"b\"})",
              shape(occurrence(result, source, "x + \"a\" + \"b\""), source));
          assertEquals(result.identity(), decoded.identity());
          assertEquals(result.aggregate(), decoded.aggregate());
        }));

    // Assert
    return observations;
  }

  @Test
  void givenRecursiveDeclarationAndExecutableOwners_whenProducedAndDecoded_thenEveryRegionKeepsItsActualOwnerAndScope() {
    // Arrange
    var source = """
        enum Choice { A(-3, "enum" + "argument"); Choice(int number, String text) {} }
        class Example {
          int field = -4;
          String text = "field" + "value";
          static int staticField;
          static { staticField = -5; String value = "static" + "block"; }
          { field = -6; String value = "instance" + "block"; }
          Example() { field = -7; String value = "constructor" + "body"; }
          java.util.function.Supplier<String> function = () -> "field" + "lambda";
          String method(String input) {
            class Local { String value() { return "local" + "class"; } }
            Object instance = new Object() { String value() { return "anonymous" + "class"; } };
            java.util.function.Supplier<String> function = () -> "method" + "lambda";
            int local = -8;
            return switch (input) { case "switch" + "label" -> "branch" + "value"; default -> "fallback" + "value"; };
          }
        }
        """;

    // Act
    var result = produce(source);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    for (var expression : List.of("-3", "-4", "-5", "-6", "-7", "-8")) {
      var unary = occurrence(decoded, source, expression);
      assertEquals(JavaSourceKind.UNARY_MINUS, unary.sourceKind(), expression);
      assertEquals(expression.substring(1), text(unary.requiredChild(SyntaxRole.OPERAND), source));
    }
    for (var expression : List.of("\"enum\" + \"argument\"", "\"field\" + \"value\"", "\"static\" + \"block\"",
        "\"instance\" + \"block\"", "\"constructor\" + \"body\"", "\"field\" + \"lambda\"", "\"local\" + \"class\"",
        "\"anonymous\" + \"class\"", "\"method\" + \"lambda\"", "\"switch\" + \"label\"", "\"branch\" + \"value\"", "\"fallback\" + \"value\"")) {
      var binary = occurrence(decoded, source, expression);
      assertEquals(JavaSourceKind.PLUS, binary.sourceKind(), expression);
      assertEquals(2, binary.children().size());
      assertTrue(decoded.aggregate().writtenSourceAssociation(binary.nodeId()).orElseThrow().normalizedExpression().isPresent());
    }
    assertEquals(result.aggregate(), decoded.aggregate());
    assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
  }

  @Test
  void givenRepeatedNestedAndTypeUseAnnotationValues_whenProducedAndDecoded_thenPhysicalOrderAndTypedValuesRemainFaithful() {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Repeatable(Tags.class) @interface Tag { String value(); }
        @interface Tags { Tag[] value(); }
        @Target(ElementType.TYPE_USE) @interface Use { String text(); int number(); }
        @Tag("a" + "b") @Tag("c" + "d") class Example {
          @Use(text="e" + "f", number=-1) Example() {}
          @Use(text="g" + "h", number=-2) String @Use(text="i" + "j", number=-3) [] field;
        }
        @Tags({@Tag("k" + "l"), @Tag("m" + "n")}) class Neighbor {}
        """;

    // Act
    var result = produce(source);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var arguments = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT)
        .map(node -> node.value(SyntaxValue.AnnotationArgument.class).value()).toList();
    assertEquals(List.of("ab", "cd", "ef", "gh", "ij", "kl", "mn"), arguments.stream()
        .filter(value -> value instanceof AnnotationScalar.StringValue).map(value -> ((AnnotationScalar.StringValue) value).value()).toList());
    assertEquals(List.of(-1, -2, -3), arguments.stream().filter(value -> value instanceof AnnotationScalar.IntValue)
        .map(value -> ((AnnotationScalar.IntValue) value).value()).toList());
    assertEquals(7, decoded.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.PLUS
        && node.syntaxKind() == SyntaxKind.BINARY).count());
    assertEquals(result.aggregate(), decoded.aggregate());
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
  }

  private static String shape(SyntaxNode node, String source) {
    if (node.syntaxKind() == SyntaxKind.UNARY) {
      return node.sourceKind().name() + '(' + shape(node.requiredChild(SyntaxRole.OPERAND), source) + ')';
    }
    if (node.sourceKind() == JavaSourceKind.PLUS || node.sourceKind() == JavaSourceKind.MINUS) {
      return node.sourceKind().name() + '(' + shape(node.requiredChild(SyntaxRole.LEFT_OPERAND), source) + ','
          + shape(node.requiredChild(SyntaxRole.RIGHT_OPERAND), source) + ')';
    }
    return node.sourceKind().name() + '{' + text(node, source) + '}';
  }

  private record ExpressionCase(String name, String returnType, String expression, String shape) {
  }

  private AstResult produce(String source) {
    return produce(source, AstProfile.JAVA_SOURCE_FIDELITY);
  }

  private AstResult produce(String source, AstProfile profile) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)).result().orElseThrow();
  }

  private static SyntaxNode occurrence(AstResult result, String source, String text) {
    var nodes = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() != SyntaxKind.ANNOTATION_ARGUMENT
        && text.equals(text(node, source))).toList();
    assertEquals(1, nodes.size(), text);
    return nodes.getFirst();
  }

  private static String text(SyntaxNode node, String source) {
    return node.origin().map(span -> source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())))
        .orElse("");
  }
}
