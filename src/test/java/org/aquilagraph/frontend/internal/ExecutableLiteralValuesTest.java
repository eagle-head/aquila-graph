package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.DecimalMinimumOperand;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SourceInput;
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

class ExecutableLiteralValuesTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenRadixAndSeparatorVariants_whenProducedAndDecoded_thenExactTypedLiteralsAndRawRangesSurvive() {
    // Arrange
    var cases = List.of(
        literal("HexIntMaximum", "0x7fffffff", 2147483647),
        literal("HexIntHighBit", "0X80000000", -2147483648),
        literal("HexIntAllBits", "0xFFFFFFFF", -1),
        literal("HexLongMaximum", "0X7fffffffffffffffL", 9223372036854775807L),
        literal("HexLongHighBit", "0x8000000000000000l", -9223372036854775808L),
        literal("HexLongAllBits", "0XffffffffffffffffL", -1L),
        literal("OctalIntMaximum", "017777777777", 2147483647),
        literal("OctalIntHighBit", "020000000000", -2147483648),
        literal("OctalIntAllBits", "037777777777", -1),
        literal("OctalLongMaximum", "0777777777777777777777l", 9223372036854775807L),
        literal("OctalLongHighBit", "01000000000000000000000L", -9223372036854775808L),
        literal("OctalLongAllBits", "01777777777777777777777l", -1L),
        literal("BinaryIntMaximum", "0b1111111111111111111111111111111", 2147483647),
        literal("BinaryIntHighBit", "0B10000000000000000000000000000000", -2147483648),
        literal("BinaryIntAllBits", "0b11111111111111111111111111111111", -1),
        literal("BinaryLongMaximum", "0B111111111111111111111111111111111111111111111111111111111111111L", 9223372036854775807L),
        literal("BinaryLongHighBit", "0b1000000000000000000000000000000000000000000000000000000000000000l", -9223372036854775808L),
        literal("BinaryLongAllBits", "0B1111111111111111111111111111111111111111111111111111111111111111L", -1L),
        literal("DecimalIntMaximum", "2147483647", 2147483647),
        literal("DecimalLongMaximum", "9223372036854775807L", 9223372036854775807L),
        literal("DecimalRepeatedSeparators", "1__234_567", 1234567),
        literal("LongSuffixBoundary", "9_223_372_036_854_775_807l", 9223372036854775807L),
        literal("HexSeparators", "0xCA__FE_BA_BE", -889275714),
        literal("HexLongSeparators", "0X8000__0000_0000_0000L", -9223372036854775808L),
        literal("OctalPrefixSeparators", "0__7_7", 63),
        literal("OctalLongSeparators", "017_7777_7777_7777_7777_7777L", -1L),
        literal("BinarySeparators", "0b1010__0101", 165),
        literal("BinaryLongSeparators", "0B1__0000L", 16L),
        literal("Zero", "0", 0),
        literal("LongZero", "0l", 0L));

    // Act
    var tests = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.name() + "_whenProducedAndDecoded_thenTypedValueAndWrittenRangeRemainExact", () -> {
          // Arrange
          var source = methodSource(value);

          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertComplete(ast, source);
            var scope = new ExecutionScope.Method(typeKey() + "#value()");
            var root = ast.aggregate().executionSyntax(scope.key()).orElseThrow().root();
            var returned = root.children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION);
            assertLiteral(ast, returned, value, source.indexOf("return ") + 7);
            assertEquals(scope, ast.aggregate().executionSyntax(scope.key()).orElseThrow().scope());
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenSignedIntegerExpressions_whenProducedAndDecoded_thenLiteralAndUnaryValuesStayDistinct() {
    // Arrange
    var cases = List.of(
        new UnaryCase(literal("DecimalIntMinimum", "2147483648", Integer.MIN_VALUE), "-2147483648", true),
        new UnaryCase(literal("DecimalLongMinimum", "9223372036854775808L", Long.MIN_VALUE), "-9223372036854775808", true),
        new UnaryCase(literal("NegativeHexInt", "0x80000000", -2147483648), "-2147483648", false),
        new UnaryCase(literal("NegativeHexLong", "0x8000000000000000L", -9223372036854775808L), "-9223372036854775808", false),
        new UnaryCase(literal("NegativeOctalInt", "037777777777", -1), "1", false),
        new UnaryCase(literal("NegativeOctalLong", "01777777777777777777777l", -1L), "1", false),
        new UnaryCase(literal("NegativeBinaryInt", "0b11111111111111111111111111111111", -1), "1", false),
        new UnaryCase(literal("NegativeBinaryLong", "0B1111111111111111111111111111111111111111111111111111111111111111L", -1L), "1", false));

    // Act
    var tests = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.literal().name() + "_whenProducedAndDecoded_thenUnaryOwnsOneExactOperand", () -> {
          // Arrange
          var literal = value.literal();
          var source = "class Example { " + literal.type() + " value() { return -" + literal.raw() + "; } }";

          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertComplete(ast, source);
            var unary = ast.aggregate().executionSyntax(typeKey() + "#value()").orElseThrow().root()
                .children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION);
            assertEquals(SyntaxKind.UNARY, unary.syntaxKind());
            assertEquals(JavaSourceKind.UNARY_MINUS, unary.sourceKind());
            assertEquals(SyntaxOperator.UNARY_MINUS, unary.value(SyntaxValue.Operator.class).operator());
            assertEquals(List.of(SyntaxRole.OPERAND), unary.children().stream().map(child -> child.role()).toList());
            assertEquals(0, unary.children().getFirst().position());
            assertEquals(literal.type(), unary.resolvedType().orElseThrow().displayName());
            assertSpan(unary, source.indexOf("return ") + 7, literal.raw().length() + 1);
            var operand = unary.requiredChild(SyntaxRole.OPERAND);
            assertEquals(unary.structuralPath().child(SyntaxRole.OPERAND, 0), operand.structuralPath());
            if (value.restricted()) {
              assertEquals(SyntaxKind.LITERAL, operand.syntaxKind());
              assertEquals(literal.kind(), operand.sourceKind());
              assertEquals(literal.type(), operand.resolvedType().orElseThrow().displayName());
              assertSpan(operand, source.indexOf("return ") + 8, literal.raw().length());
              var category = literal.kind() == JavaSourceKind.INT_LITERAL ? JavaTypeCategory.INT : JavaTypeCategory.LONG;
              var magnitude = value.runtime().substring(1);
              assertEquals(new SyntaxValue.Literal(magnitude, Optional.of(new DecimalMinimumOperand(category, magnitude))), operand.value());
              assertEquals(Optional.empty(), ast.aggregate().executableLiteralText(operand.nodeId()));
              assertEquals(Optional.of(value.runtime()), ast.aggregate().writtenSourceAssociation(unary.nodeId()).orElseThrow()
                  .normalizedExpression().orElseThrow().derivedConstant().map(ExecutableLiteralValuesTest::scalarText));
            } else {
              assertLiteral(ast, operand, literal, source.indexOf("return ") + 8);
            }
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenFloatingPointSpellings_whenProducedAndDecoded_thenExactPrecisionAndRawRangesSurvive() {
    // Arrange
    var cases = List.of(
        literal("PositiveExponentFloat", "1e+2f", 100.0f),
        literal("NegativeExponentFloat", "125E-2F", 1.25f),
        literal("PositiveExponentDouble", "1E+2d", 100.0d),
        literal("NegativeExponentDouble", "125e-2D", 1.25d),
        literal("UnsuffixedExponent", "1e2", 100.0d),
        literal("LeadingPointFloat", ".5f", 0.5f),
        literal("LeadingPointDouble", ".5", 0.5d),
        literal("TrailingPointFloat", "2.F", 2.0f),
        literal("TrailingPointDouble", "2.", 2.0d),
        literal("IntegerFormFloat", "2f", 2.0f),
        literal("IntegerFormDouble", "2D", 2.0d),
        literal("FloatDecimalMaximum", "3.4028235e38f", Float.MAX_VALUE),
        literal("FloatDecimalMinimum", "1.4e-45F", Float.MIN_VALUE),
        literal("DoubleDecimalMaximum", "1.7976931348623157e308", Double.MAX_VALUE),
        literal("DoubleDecimalMinimum", "4.9e-324d", Double.MIN_VALUE),
        literal("FloatDecimalRoundingDown", "16777217f", 16777216.0f),
        literal("FloatDecimalNextValue", "16777218F", 16777218.0f),
        literal("DoubleDecimalRoundingDown", "9007199254740993d", 9007199254740992.0d),
        literal("DoubleDecimalNextValue", "9007199254740994D", 9007199254740994.0d),
        literal("DecimalFloatingSeparators", "1__2.5_0e+1_0f", 125000000000.0f),
        literal("HexFloatMaximum", "0x1.fffffeP+127f", Float.MAX_VALUE),
        literal("HexFloatMinimum", "0X0.000002p-126F", Float.MIN_VALUE),
        literal("HexDoubleMaximum", "0x1.f_ffff_ffff_ffffP+1023D", Double.MAX_VALUE),
        literal("HexDoubleMinimum", "0X1.0p-1074", Double.MIN_VALUE),
        literal("HexFloatRoundingDown", "0x1.000001p0f", 1.0f),
        literal("HexFloatNextValue", "0x1.000002P+0F", 1.00000011920928955078125f),
        literal("HexDoubleRoundingDown", "0x1.00000000000008p0", 1.0d),
        literal("HexDoubleNextValue", "0x1.0000000000001p0d", 1.0000000000000002220446049250313d),
        literal("HexLeadingPoint", "0x.8p1", 1.0d),
        literal("HexTrailingPoint", "0X1.P-1f", 0.5f),
        literal("HexIntegerSignificand", "0x1p+4D", 16.0d),
        literal("HexFloatingSeparators", "0x1__0.8_0p-0_1f", 8.25f),
        literal("FloatNormalBoundary", "0x1p-126f", Float.MIN_NORMAL),
        literal("DoubleNormalBoundary", "0x1p-1022", Double.MIN_NORMAL));

    // Act
    var tests = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.name() + "_whenProducedAndDecoded_thenExactFloatingPointValueSurvives", () -> {
          // Arrange
          var source = methodSource(value);

          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertComplete(ast, source);
            var node = ast.aggregate().executionSyntax(typeKey() + "#value()").orElseThrow().root()
                .children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION);
            assertLiteral(ast, node, value, source.indexOf("return ") + 7);
            var text = node.value(SyntaxValue.Literal.class).text();
            if (value.expected() instanceof Float expected) {
              assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(Float.parseFloat(text)));
            } else if (value.expected() instanceof Double expected) {
              assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(Double.parseDouble(text)));
            }
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenStringCharacterAndTextBlockSpellings_whenProducedAndDecoded_thenCompleteScalarsAndRawRangesSurvive() {
    // Arrange
    var cases = textCases();

    // Act
    var tests = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.name() + "_whenProducedAndDecoded_thenExactTextAndWrittenRangeSurvive", () -> {
          // Arrange
          var source = methodSource(value);

          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertComplete(ast, source);
            var node = ast.aggregate().executionSyntax(typeKey() + "#value()").orElseThrow().root()
                .children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION);
            assertLiteral(ast, node, value, source.indexOf("return ") + 7);
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidNumericBoundaries_whenProduced_thenTheIntendedLocatedCompilerErrorPreventsCompletion() {
    // Arrange
    var cases = List.of(
        new InvalidCase("PositiveIntMinimumMagnitude", "int", "2147483648", "compiler.err.int.number.too.large"),
        new InvalidCase("PositiveLongMinimumMagnitude", "long", "9223372036854775808L", "compiler.err.int.number.too.large"),
        new InvalidCase("LongBeyondMinimumMagnitude", "long", "-9223372036854775809L", "compiler.err.int.number.too.large"),
        new InvalidCase("IntBeyondMinimumMagnitude", "int", "-2147483649", "compiler.err.int.number.too.large"),
        new InvalidCase("HexIntTooWide", "int", "0x100000000", "compiler.err.int.number.too.large"),
        new InvalidCase("HexLongTooWide", "long", "0x10000000000000000L", "compiler.err.int.number.too.large"),
        new InvalidCase("OctalLongTooWide", "long", "02000000000000000000000L", "compiler.err.int.number.too.large"),
        new InvalidCase("BinaryIntTooWide", "int", "0b100000000000000000000000000000000", "compiler.err.int.number.too.large"),
        new InvalidCase("BinaryLongTooWide", "long", "0b10000000000000000000000000000000000000000000000000000000000000000L", "compiler.err.int.number.too.large"),
        new InvalidCase("TrailingDecimalSeparator", "int", "1_", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorBeforeLongSuffix", "long", "1_L", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorAfterHexPrefix", "int", "0x_1", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorAfterBinaryPrefix", "int", "0b_1", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorBeforePoint", "double", "1_.0", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorAfterPoint", "float", "1._0f", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorBeforeExponent", "double", "1_e2", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorAfterExponentSign", "double", "1e+_2", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorBeforeFloatSuffix", "float", "1.0_f", "compiler.err.illegal.underscore"),
        new InvalidCase("SeparatorBeforeBinaryExponent", "double", "0x1_p1", "compiler.err.illegal.underscore"),
        new InvalidCase("DecimalFloatOverflow", "float", "3.4028236e38f", "compiler.err.fp.number.too.large"),
        new InvalidCase("DecimalFloatUnderflow", "float", "1e-46f", "compiler.err.fp.number.too.small"),
        new InvalidCase("DecimalDoubleOverflow", "double", "1.7976931348623159e308", "compiler.err.fp.number.too.large"),
        new InvalidCase("DecimalDoubleUnderflow", "double", "1e-325", "compiler.err.fp.number.too.small"),
        new InvalidCase("HexFloatOverflow", "float", "0x1p128f", "compiler.err.fp.number.too.large"),
        new InvalidCase("HexFloatUnderflow", "float", "0x1p-150f", "compiler.err.fp.number.too.small"),
        new InvalidCase("HexDoubleOverflow", "double", "0x1p1024", "compiler.err.fp.number.too.large"),
        new InvalidCase("HexDoubleUnderflow", "double", "0x1p-1075", "compiler.err.fp.number.too.small"));

    // Act
    var tests = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.name() + "_whenProduced_thenTheSpecificCompilerErrorIsRetained", () -> {
          // Arrange
          var source = "class Example { " + value.type() + " value() { return " + value.raw()
              + "; } } class Neighbor { int preserved() { return 19; } }";

          // Act
          var outcome = produce(source);

          // Assert
          assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
          var errors = outcome.diagnostics().stream().filter(diagnostic -> diagnostic.isError() && diagnostic.code().equals(value.code()))
              .toList();
          assertFalse(errors.isEmpty(), outcome.diagnostics().toString());
          var start = source.indexOf("return ") + 7;
          assertTrue(errors.stream().flatMap(diagnostic -> diagnostic.origins().stream())
              .anyMatch(span -> span.startOffset() >= start && span.startOffset() < start + value.raw().length()), errors.toString());
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          for (var ast : List.of(original, decoded)) {
            assertNotEquals(AstCompletion.COMPLETE, ast.completion());
            assertTrue(ast.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().equals(value.code())));
            var owner = new JavaTypeDeclarationIdentity(Optional.empty(), "Neighbor").declarationKey();
            var preserved = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration method
                && method.declarationKey().equals(owner + "#preserved()")).toList();
            assertEquals(1, preserved.size());
            assertEquals(owner, preserved.getFirst().value(SyntaxValue.ExecutableDeclaration.class).ownerKey());
          }
        }));
    // Assert
    return tests;
  }

  private static List<LiteralCase> textCases() {
    return List.of(
        literal("NamedStringEscapes", "\"\\b\\t\\n\\f\\r\\s\"", "\b\t\n\f\r "),
        literal("NamedBackspace", "'\\b'", '\b'),
        literal("NamedTab", "'\\t'", '\t'),
        literal("NamedNewline", "'\\n'", '\n'),
        literal("NamedFormFeed", "'\\f'", '\f'),
        literal("NamedCarriageReturn", "'\\r'", '\r'),
        literal("NamedSpace", "'\\s'", ' '),
        literal("OctalCharacter", "'\\141'", 'a'),
        literal("OctalNull", "'\\0'", '\0'),
        literal("OctalMaximum", "'\\377'", (char) 255),
        literal("OctalDigitBoundary", "\"\\400\\377\\07\\7\"", " 0\u00ff\7\7"),
        literal("QuotesAndBackslashString", "\"\\\"\\'\\\\\"", "\"'\\"),
        literal("SingleQuoteCharacter", "'\\''", '\''),
        literal("DoubleQuoteCharacter", "'\\\"'", '"'),
        literal("BackslashCharacter", "'\\\\'", '\\'),
        literal("UnicodeCharacter", "'" + unicode("0061") + "'", 'a'),
        literal("UnicodeGeneratedEscape", "\"" + unicode("005c") + "n\"", "\n"),
        literal("EscapedUnicodeSpelling", "\"\\\\u0061\"", "\\u0061"),
        literal("UnicodeTranslatedString", "\"" + unicode("0061") + "\"", "a"),
        literal("UnicodeGeneratedOctal", "'" + unicode("005c") + "141'", 'a'),
        literal("TextBlockClosingIndent", "\"\"\"\n    alpha\n      beta\n  \"\"\"", "  alpha\n    beta\n"),
        literal("TextBlockSameLineClose", "\"\"\"\n    alpha\"\"\"", "alpha"),
        literal("TextBlockBlankTrailingLines", "\"\"\"\n    alpha\n    \n    \n    \"\"\"", "alpha\n\n\n"),
        literal("TextBlockCrLf", "\"\"\"\r\n    alpha\r\n      beta\r\n    \"\"\"", "alpha\n  beta\n"),
        literal("TextBlockSpaceEscape", "\"\"\"\n    alpha\\s\\s\n    \"\"\"", "alpha  \n"),
        literal("TextBlockContinuation", "\"\"\"\n    alpha\\\n    beta\n    \"\"\"", "alphabeta\n"),
        literal("TextBlockCrLfContinuation", "\"\"\"\r\n    alpha\\\r\n    beta\r\n    \"\"\"", "alphabeta\n"),
        literal("TextBlockEscapedCarriageReturn", "\"\"\"\n    alpha\\r\n    \"\"\"", "alpha\r\n"));
  }

  @TestFactory
  Stream<DynamicTest> givenLiteralFamiliesInDistinctExecutionOwners_whenProducedAndDecoded_thenEachOccurrenceHasItsExactCanonicalOwner() {
    // Arrange
    var cases = List.of(
        literal("Long", "47L", 47L),
        literal("Float", "1.25f", 1.25f),
        literal("Double", "2.5d", 2.5d),
        literal("True", "true", true),
        literal("False", "false", false),
        literal("Character", "'\\n'", '\n'),
        literal("EscapedString", "\"first\\tsecond\"", "first\tsecond"),
        literal("TextBlock", "\"\"\"\n  first\n  second\n  \"\"\"", "first\nsecond\n"));

    // Act
    var tests = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.name() + "InFourOwners_whenProducedAndDecoded_thenValuesAndOwnersRemainDistinct", () -> {
          // Arrange
          var method = value.type() + " value() { return " + value.raw() + "; }";
          var field = value.type() + " stored = " + value.raw() + ";";
          var staticBlock = "static { " + value.type() + " local = " + value.raw() + "; }";
          var instanceBlock = "{ " + value.type() + " local = " + value.raw() + "; }";
          var source = "class Example { " + method + " " + field + " " + staticBlock + " " + instanceBlock + " }";

          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertComplete(ast, source);
            var type = ast.aggregate().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).getFirst();
            var members = type.children(SyntaxRole.MEMBER);
            assertEquals(List.of(SyntaxKind.METHOD_DECLARATION, SyntaxKind.FIELD_DECLARATION,
                SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK),
                members.stream().map(SyntaxNode::syntaxKind).toList());
            var methodScope = new ExecutionScope.Method(typeKey() + "#value()");
            var fieldScope = new ExecutionScope.Field(typeKey() + "#field:stored", typeKey());
            var staticScope = initializer(ast, source.indexOf(staticBlock), staticBlock.length(), SyntaxKind.STATIC_INITIALIZER_BLOCK);
            var instanceScope = initializer(ast, source.lastIndexOf(instanceBlock), instanceBlock.length(), SyntaxKind.INSTANCE_INITIALIZER_BLOCK);
            var scopes = List.<ExecutionScope>of(methodScope, fieldScope, staticScope, instanceScope);
            var declarations = List.of(method, field, staticBlock, instanceBlock);
            for (var index = 0; index < scopes.size(); index++) {
              var expectedScope = scopes.get(index);
              var syntax = ast.aggregate().executionSyntax(expectedScope.key()).orElseThrow();
              assertEquals(expectedScope, syntax.scope());
              var literals = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).toList();
              assertEquals(1, literals.size());
              var node = literals.getFirst();
              var declarationStart = index == 3 ? source.lastIndexOf(instanceBlock) : source.indexOf(declarations.get(index));
              assertLiteral(ast, node, value, source.indexOf(value.raw(), declarationStart));
              var actualOwners = ast.aggregate().executionScopes().stream()
                  .filter(scope -> ast.aggregate().executionSyntax(scope.key()).orElseThrow().root().descendantsAndSelf().stream()
                      .anyMatch(occurrence -> occurrence.nodeId().equals(node.nodeId()))).toList();
              assertEquals(List.of(expectedScope), actualOwners);
              if (index > 0) {
                assertSame(members.get(index), syntax.root());
                assertTrue(ast.aggregate().methodSyntax(expectedScope.key()).isEmpty());
              }
            }
            assertEquals(4, ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).count());
          }
        }));
    // Assert
    return tests;
  }

  @Test
  void givenSignedFloatingZero_whenProducedAndDecoded_thenTheSignRemainsAUnaryOperationOverAPositiveLiteral() {
    // Arrange
    var source = "class Example { float value() { return -0.0F; } double other() { return +0.0d; } }";

    // Act
    var original = produce(source).result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    for (var ast : List.of(original, decoded)) {
      assertComplete(ast, source);
      var minus = ast.aggregate().executionSyntax(typeKey() + "#value()").orElseThrow().root()
          .children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION);
      var plus = ast.aggregate().executionSyntax(typeKey() + "#other()").orElseThrow().root()
          .children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION);
      for (var unary : List.of(minus, plus)) {
        assertEquals(SyntaxKind.UNARY, unary.syntaxKind());
        assertEquals(List.of(SyntaxRole.OPERAND), unary.children().stream().map(child -> child.role()).toList());
        assertEquals(0, unary.children().getFirst().position());
      }
      assertEquals(JavaSourceKind.UNARY_MINUS, minus.sourceKind());
      assertEquals(SyntaxOperator.UNARY_MINUS, minus.value(SyntaxValue.Operator.class).operator());
      assertEquals(JavaSourceKind.UNARY_PLUS, plus.sourceKind());
      assertEquals(SyntaxOperator.UNARY_PLUS, plus.value(SyntaxValue.Operator.class).operator());
      assertLiteral(ast, minus.requiredChild(SyntaxRole.OPERAND), literal("FloatZero", "0.0F", 0.0f), source.indexOf("0.0F"));
      assertLiteral(ast, plus.requiredChild(SyntaxRole.OPERAND), literal("DoubleZero", "0.0d", 0.0d), source.indexOf("0.0d"));
    }
  }

  private static ExecutionScope.Initializer initializer(AstResult ast, int start, int length, SyntaxKind kind) {
    var sourceKey = ast.snapshot().sourceFiles().getFirst().sourceFileKey();
    return new ExecutionScope.Initializer(typeKey() + ":initializer:" + start + ":" + (start + length), typeKey(),
        new SourceSpan(sourceKey, start, start + length), kind);
  }

  private static String unicode(String hexadecimal) {
    return "\\" + "u" + hexadecimal;
  }

  private record InvalidCase(String name, String type, String raw, String code) {
  }

  private record LiteralCase(String name, String type, JavaSourceKind kind, String raw, Object expected) {
  }

  private record UnaryCase(LiteralCase literal, String runtime, boolean restricted) {
  }

  private static LiteralCase literal(String name, String raw, Object expected) {
    return switch (expected) {
      case Integer _ -> new LiteralCase(name, "int", JavaSourceKind.INT_LITERAL, raw, expected);
      case Long _ -> new LiteralCase(name, "long", JavaSourceKind.LONG_LITERAL, raw, expected);
      case Float _ -> new LiteralCase(name, "float", JavaSourceKind.FLOAT_LITERAL, raw, expected);
      case Double _ -> new LiteralCase(name, "double", JavaSourceKind.DOUBLE_LITERAL, raw, expected);
      case Boolean _ -> new LiteralCase(name, "boolean", JavaSourceKind.BOOLEAN_LITERAL, raw, expected);
      case Character _ -> new LiteralCase(name, "char", JavaSourceKind.CHAR_LITERAL, raw, expected);
      case String _ -> new LiteralCase(name, "String", JavaSourceKind.STRING_LITERAL, raw, expected);
      default -> throw new IllegalArgumentException("Unsupported literal expectation");
    };
  }

  private static String methodSource(LiteralCase value) {
    return "class Example { " + value.type() + " value() { return " + value.raw() + "; } }";
  }

  private static void assertLiteral(AstResult ast, SyntaxNode node, LiteralCase value, int start) {
    assertEquals(SyntaxKind.LITERAL, node.syntaxKind());
    assertEquals(value.kind(), node.sourceKind());
    assertEquals(value.type().equals("String") ? "java.lang.String" : value.type(), node.resolvedType().orElseThrow().displayName());
    assertEquals(new SyntaxValue.Literal(value.expected().toString()), node.value());
    assertEquals(Optional.of(value.expected().toString()), ast.aggregate().executableLiteralText(node.nodeId()));
    assertSpan(node, start, value.raw().length());
    var source = ast.snapshot().sourceFiles().getFirst().content();
    var span = node.origin().orElseThrow();
    assertEquals(value.raw(), source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())));
    assertTrue(node.children().isEmpty());
    assertEquals(Optional.empty(), node.semanticReference());
  }

  private static String scalarText(AnnotationScalar scalar) {
    return switch (scalar) {
      case AnnotationScalar.IntValue value -> Integer.toString(value.value());
      case AnnotationScalar.LongValue value -> Long.toString(value.value());
      default -> throw new IllegalArgumentException("Expected an integral constant");
    };
  }

  private static void assertSpan(SyntaxNode node, int start, int length) {
    assertEquals(new SourceSpan(node.sourceFileKey(), start, start + length), node.origin().orElseThrow());
  }

  private static void assertComplete(AstResult ast, String source) {
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, ast.profile());
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertFalse(ast.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()), ast.diagnostics().toString());
    assertEquals(source, ast.snapshot().sourceFiles().getFirst().content());
  }

  private AstOutcome produce(String source) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return JavaFrontends.standard().produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
  }

  private static String typeKey() {
    return new JavaTypeDeclarationIdentity(Optional.empty(), "Example").declarationKey();
  }
}
