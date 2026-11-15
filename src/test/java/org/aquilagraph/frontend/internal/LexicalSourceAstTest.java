package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
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
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
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

class LexicalSourceAstTest {
  @TempDir Path directory;

  @Test
  void givenEscapedOwnerDelimiter_whenProducedAndDecoded_thenGenericConstructorRetainsItsWrittenArguments() throws IOException {
    // Arrange
    var delimiter = unicode("007d");
    var source = "class Box { <T> Box(T x) {} " + delimiter + " class Probe { Object make() { return new Box(1); } }";
    assertEquals(6, delimiter.length());
    assertCompilerAccepts(source);

    // Act
    var outcome = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
      assertCompleteCapture(ast, source);
      var creation = occurrence(ast, SyntaxKind.OBJECT_CREATION, "new Box(1)");
      assertEquals(List.of(occurrence(ast, SyntaxKind.LITERAL, "1")), creation.children(SyntaxRole.ARGUMENT));
      assertConstructorEvidence(ast, creation);
    }
  }

  @TestFactory
  Stream<DynamicTest> givenTranslatedOwnerBoundaries_whenProducedAndDecoded_thenConstructorMeaningComposesAcrossContexts() {
    // Arrange
    var cases = List.of(
        "class Box { <T> Box(T x) {} } class Probe { Object make() { return new Box(1); } }",
        "class Box { <T> Box(T x) {} " + "\\" + "uuuu007d class Probe { Object make() { return new Box(1); } }",
        "class Box { String braces = \"}\"; /* } */ <T> Box(T x) {} " + unicode("007d")
            + " class Probe { Object make() { return new Box(1); } }",
        "class Probe { Object scope() { class Box { <T> Box(T x) {} " + unicode("007d")
            + " class Local { Object make() { return new Box(1); } } return new Local().make(); } }",
        "class Probe { Object scope() { class Box<A> { <T> Box(T x) {} " + unicode("007d")
            + " class Local { Object make() { return new Box<>(1); } } return new Local().make(); } }");

    // Act
    var tests = cases.stream().map(source -> DynamicTest.dynamicTest(
        "givenOwnerContext" + cases.indexOf(source) + "_whenProducedAndDecoded_thenSelectedConstructorAndOffsetsRemainExact", () -> {
          // Arrange
          assertCompilerAccepts(source);
          // Act
          var outcome = produce(source);
          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          var original = outcome.result().orElseThrow();
          for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
            var creation = occurrence(ast, SyntaxKind.OBJECT_CREATION, source.contains("new Box<>") ? "new Box<>(1)" : "new Box(1)");
            assertConstructorEvidence(ast, creation);
            assertEquals(List.of(occurrence(ast, SyntaxKind.LITERAL, "1")), creation.children(SyntaxRole.ARGUMENT));
          }
        }));
    // Assert
    return tests;
  }

  @Test
  void givenEscapedConstructorOwnerInAnotherUnit_whenProducedAndDecoded_thenDeclarationUnitSuppliesTheBoundary() throws IOException {
    // Arrange
    var owner = "class Box { <T> Box(T x) {} " + unicode("007d");
    var use = "class Probe { Object make() { return new Box(1); } }";
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Box.java")), owner),
        SourceFile.fromContent(capturedPath(Path.of("Probe.java")), use)));
    assertCompilerAccepts(snapshot.sourceFiles());

    // Act
    var outcome = JavaFrontends.standard().produce(request(new SourceInput.Snapshot(snapshot)));

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
      var creation = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst().orElseThrow();
      assertConstructorEvidence(ast, creation);
      var file = ast.snapshot().sourceFiles().stream().filter(value -> value.sourceFileKey().equals(creation.sourceFileKey())).findFirst().orElseThrow();
      assertEquals(capturedPath(Path.of("Probe.java")), file.relativePath());
      assertEquals("new Box(1)", slice(creation, file.content()));
      assertEquals(original.aggregate(), ast.aggregate());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenCompilerLiteralSpellingsAndFoldedChildren_whenProducedAndDecoded_thenMeaningsAndWrittenOperandsRemainDistinct() {
    // Arrange
    var cases = List.of(new LiteralCase("true control", "true", "true"),
        new LiteralCase("ignorable true", "tr" + unicode("0000") + "ue", "true"),
        new LiteralCase("ignorable false", "fa" + unicode("0000") + "lse", "false"),
        new LiteralCase("ignorable null", "nu" + unicode("0000") + "ll", "null"));

    // Act
    var tests = cases.stream().map(example -> DynamicTest.dynamicTest(
        "given" + example.name() + "_whenFoldedAndDecoded_thenIndividualLiteralAndRangeRemainExact", () -> {
          // Arrange
          var expression = "\"prefix\" + " + example.raw() + " + \"suffix\"";
          var source = "class Example { String value() { return " + expression + "; } }";
          assertCompilerAccepts(source);
          // Act
          var outcome = produce(source);
          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          var original = outcome.result().orElseThrow();
          for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
            var literal = occurrence(ast, SyntaxKind.LITERAL, example.raw());
            assertEquals(example.expected(), literal.value(SyntaxValue.Literal.class).text());
            var outer = occurrence(ast, SyntaxKind.BINARY, expression);
            assertEquals(literal, outer.requiredChild(SyntaxRole.LEFT_OPERAND).requiredChild(SyntaxRole.RIGHT_OPERAND));
            assertEquals("suffix", outer.requiredChild(SyntaxRole.RIGHT_OPERAND).value(SyntaxValue.Literal.class).text());
          }
        }));
    // Assert
    return tests;
  }

  @Test
  void givenIgnorableLiteralInAnnotationDefault_whenProducedAndDecoded_thenAnnotationAndStringContentsKeepTheirOwnMeaning() throws IOException {
    // Arrange
    var spelling = "tr" + unicode("0000") + "ue";
    var content = "tr" + unicode("0000") + "ue";
    var source = "@interface Example { String value() default \"prefix\" + " + spelling
        + "; } class Probe { String text() { return \"" + content + "\"; } }";
    assertCompilerAccepts(source);

    // Act
    var outcome = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
      var value = ast.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.BOOLEAN_LITERAL).findFirst().orElseThrow();
      assertEquals(spelling, value.value(SyntaxValue.Literal.class).text());
      assertEquals(spelling, slice(value, source));
      var declaration = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration method
          && method.defaultValue().isPresent()).findFirst().orElseThrow();
      assertEquals(Optional.of(new AnnotationScalar.StringValue("prefixtrue")),
          declaration.value(SyntaxValue.ExecutableDeclaration.class).defaultValue());
      var text = occurrence(ast, SyntaxKind.LITERAL, "\"" + content + "\"");
      assertEquals("tr" + Character.toString(0) + "ue", text.value(SyntaxValue.Literal.class).text());
      assertEquals(original.coverage(), ast.coverage());
    }
  }

  private static void assertConstructorEvidence(AstResult ast, SyntaxNode creation) {
    var owner = ast.program().types().stream().filter(value -> value.qualifiedName().endsWith("Box")).findFirst().orElseThrow();
    var constructor = owner.methods().stream().filter(value -> value.constructor()).findFirst().orElseThrow();
    assertEquals(constructor.methodKey(), creation.semanticReference().orElseThrow().symbolKey().orElseThrow());
    var method = ast.program().methods().stream().filter(value -> value.name().equals("make")).findFirst().orElseThrow();
    var body = ast.body(method.methodKey()).orElseThrow();
    var conversion = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
    assertEquals("int", conversion.sourceType().displayName());
    assertEquals("java.lang.Integer", conversion.selectedTarget().displayName());
    assertEquals("int", conversion.preBoxPrimitiveType().orElseThrow().displayName());
    assertEquals("java.lang.Integer", conversion.boxedType().orElseThrow().displayName());
    var applicability = body.invocationArity(creation.nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.FIXED, applicability.mode());
    assertEquals(1, applicability.formalCount());
    assertEquals(method.methodKey(), conversion.scopeKey());
    assertFalse(ast.program().methods().stream().anyMatch(value -> value.name().startsWith("constructorSelection")));
  }

  @TestFactory
  Stream<DynamicTest> givenAnnotatedLocalConstructorReceivers_whenProducedAndDecoded_thenAnnotationsAndSelectedFactsCompose() {
    // Arrange
    var cases = List.of(new AnnotatedConstruction(true, false, "}"), new AnnotatedConstruction(true, false, unicode("007d")),
        new AnnotatedConstruction(false, false, unicode("007d")), new AnnotatedConstruction(true, true, unicode("007d")),
        new AnnotatedConstruction(false, true, "}"));

    // Act
    var tests = cases.stream().map(example -> DynamicTest.dynamicTest(
        "givenAnnotatedLocalContext" + cases.indexOf(example) + "_whenProducedAndDecoded_thenSelectedFactsAndRangesSurvive", () -> {
          // Arrange
          var creationText = example.diamond() ? "new @Mark Box<>(1)" : "new @Mark Box(1)";
          var source = "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} class Probe { "
              + (example.staticMethod() ? "static " : "") + "Object make() { class Box" + (example.diamond() ? "<A>" : "")
              + " { <T> Box(T x) {} " + example.delimiter() + " return " + creationText + "; } }";
          assertCompilerAccepts(source);
          // Act
          var outcome = produce(source);
          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          var original = outcome.result().orElseThrow();
          for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
            var creation = occurrence(ast, SyntaxKind.OBJECT_CREATION, creationText);
            assertConstructorEvidence(ast, creation);
            assertEquals(original.aggregate(), ast.aggregate());
            assertEquals(original.coverage(), ast.coverage());
            assertTrue(creation.descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.ANNOTATION));
          }
        }));
    // Assert
    return tests;
  }

  private record AnnotatedConstruction(boolean staticMethod, boolean diamond, String delimiter) {
  }

  @Test
  void givenCompilerRecognizedIgnorableBoolean_whenProducedAndDecoded_thenValueAndOriginalRangeRemainTrue() throws IOException {
    // Arrange
    var spelling = "tr" + unicode("0000") + "ue";
    var source = "class Probe { boolean m() { return " + spelling + "; } }";
    assertCompilerAccepts(source);

    // Act
    var outcome = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
      assertCompleteCapture(ast, source);
      var literal = occurrence(ast, SyntaxKind.LITERAL, spelling);
      assertEquals(JavaSourceKind.BOOLEAN_LITERAL, literal.sourceKind());
      assertEquals("true", literal.value(SyntaxValue.Literal.class).text());
    }
  }

  private void assertCompilerAccepts(String source) throws IOException {
    assertCompilerAccepts(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
  }

  private void assertCompilerAccepts(List<SourceFile> sources) throws IOException {
    var paths = new ArrayList<Path>();
    for (var source : sources) {
      var file = directory.resolve(source.canonicalRelativePath());
      Files.writeString(file, source.content());
      paths.add(file);
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      assertTrue(compiler.getTask(null, manager, diagnostics,
          List.of("--release", "25", "-proc:none", "-d", directory.toString()), null,
          manager.getJavaFileObjectsFromPaths(paths)).call(), diagnostics.getDiagnostics().toString());
    }
  }

  @Test
  void givenCrLfAndEscapedKeywordAfterSupplementaryComment_whenCapturedAndDecoded_thenRawDeclarationAndChildOffsetsRemainExact()
      throws IOException {
    // Arrange
    var source = "// " + Character.toString(0x1F985) + "\r\ncl" + unicode("0061")
        + "ss Example {\r\n  int value() {\r\n    return 7;\r\n  }\r\n}\r\n";
    Files.writeString(directory.resolve("Example.java"), source);
    var request = request(new SourceInput.Root(directory));

    // Act
    var original = JavaFrontends.standard().produce(request).result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    for (var ast : List.of(original, decoded)) {
      assertCompleteCapture(ast, source);
      var type = occurrence(ast, SyntaxKind.TYPE_DECLARATION, source.substring(source.indexOf("cl"), source.lastIndexOf('}') + 1));
      assertEquals(JavaSourceKind.CLASS, type.sourceKind());
      assertEquals("Example", type.value(SyntaxValue.TypeDeclaration.class).name());
      var method = occurrence(ast, SyntaxKind.METHOD_DECLARATION, "int value() {\r\n    return 7;\r\n  }");
      assertEquals("value", method.value(SyntaxValue.ExecutableDeclaration.class).name());
      var statement = occurrence(ast, SyntaxKind.RETURN, "return 7;");
      assertEquals(occurrence(ast, SyntaxKind.LITERAL, "7"), statement.requiredChild(SyntaxRole.EXPRESSION));
      assertEquals(List.of(method), type.children(SyntaxRole.MEMBER));
    }
  }

  @TestFactory
  Stream<DynamicTest> givenUnicodeKeywordsAndCommentBoundaries_whenProducedAndDecoded_thenOnlyWrittenDeclarationsHaveRawOrigins() {
    // Arrange
    var escapedClass = "cl" + "\\" + "uuuu0061" + "ss";
    var prefixes = List.of("", unicode("002f") + unicode("002f") + " class Ghost {}" + unicode("000a"),
        "// class Ghost {}" + unicode("000d") + "\n", "// " + unicode("005c") + "u000a class Ghost {}\n",
        unicode("002f") + unicode("002a") + "class Ghost {}" + unicode("002a") + unicode("002f"));

    // Act
    var tests = prefixes.stream().map(prefix -> DynamicTest.dynamicTest(
        "givenTranslatedCommentBoundary_whenProducedAndDecoded_thenOnlyExampleIsDeclared: " + prefixes.indexOf(prefix), () -> {
          // Arrange
          var source = prefix + escapedClass + " Example { int value() { return 31; } }";
          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertCompleteCapture(ast, source);
            var rootTypes = ast.aggregate().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
            assertEquals(List.of("Example"), rootTypes.stream().map(node -> node.value(SyntaxValue.TypeDeclaration.class).name()).toList());
            assertEquals(occurrence(ast, SyntaxKind.TYPE_DECLARATION, source.substring(prefix.length())), rootTypes.getFirst());
            var returned = occurrence(ast, SyntaxKind.RETURN, "return 31;");
            assertEquals(occurrence(ast, SyntaxKind.LITERAL, "31"), returned.requiredChild(SyntaxRole.EXPRESSION));
            assertEquals("31", returned.requiredChild(SyntaxRole.EXPRESSION).value(SyntaxValue.Literal.class).text());
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenEligibleAndIneligibleLiteralEscapes_whenProducedAndDecoded_thenValuesAndRawSpellingsRemainDistinct() {
    // Arrange
    var cases = List.of(new LiteralCase("repeated marker", "\"" + "\\" + "uuu0041\"", "A"),
        new LiteralCase("even raw backslashes", "\"" + "\\".repeat(2) + "u0041\"", "\\u0041"),
        new LiteralCase("odd raw backslashes", "\"" + "\\".repeat(3) + "u0041\"", "\\A"),
        new LiteralCase("four raw backslashes", "\"" + "\\".repeat(4) + "u0041\"", "\\\\u0041"),
        new LiteralCase("two produced backslashes", "\"" + unicode("005c") + unicode("005c") + "\"", "\\"),
        new LiteralCase("produced escape introducer", "\"" + unicode("005c") + "n\"", "\n"),
        new LiteralCase("eligibility after produced backslash", "\"" + unicode("005c") + unicode("006e") + "\"", "\n"));

    // Act
    var tests = cases.stream().map(example -> DynamicTest.dynamicTest(
        "given" + example.name() + "_whenProducedAndDecoded_thenLiteralInterpretationAndRawSpanAgree", () -> {
          // Arrange
          var source = "class Example { String value() { return " + example.raw() + "; } }";
          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertCompleteCapture(ast, source);
            var literal = occurrence(ast, SyntaxKind.LITERAL, example.raw());
            assertEquals(JavaSourceKind.STRING_LITERAL, literal.sourceKind());
            assertEquals(example.expected(), literal.value(SyntaxValue.Literal.class).text());
            assertEquals(literal, occurrence(ast, SyntaxKind.RETURN, "return " + example.raw() + ";")
                .requiredChild(SyntaxRole.EXPRESSION));
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenEveryLineTerminatorAcrossDeclarationsCommentsAndTextBlocks_whenProducedAndDecoded_thenRawSlicesRemainExact() {
    // Arrange
    var terminators = List.of("\n", "\r", "\r\n");

    // Act
    var tests = Stream.of(0, 1, 2, 3).map(variant -> DynamicTest.dynamicTest(
        "givenLineTerminatorVariant" + variant + "_whenProducedAndDecoded_thenRawSlicesAndTextBlockValueRemainExact", () -> {
          // Arrange
          var first = terminators.get(variant % 3);
          var second = terminators.get(variant == 3 ? 1 : variant);
          var third = terminators.get(variant == 3 ? 2 : variant);
          var literal = "\"\"\"" + first + "alpha" + second + "beta" + third + "\"\"\"";
          var method = "String value() { // ignored" + first + "return " + literal + ";" + second + "}";
          var source = "// heading" + first + "class Example {" + second + method + third + "}";
          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertCompleteCapture(ast, source);
            var declaration = occurrence(ast, SyntaxKind.METHOD_DECLARATION, method);
            assertEquals("value", declaration.value(SyntaxValue.ExecutableDeclaration.class).name());
            var returned = occurrence(ast, SyntaxKind.RETURN, "return " + literal + ";");
            var text = occurrence(ast, SyntaxKind.LITERAL, literal);
            assertEquals(JavaSourceKind.STRING_LITERAL, text.sourceKind());
            assertEquals("alpha\nbeta\n", text.value(SyntaxValue.Literal.class).text());
            assertEquals(text, returned.requiredChild(SyntaxRole.EXPRESSION));
            assertEquals(List.of(returned), declaration.requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT));
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenIgnorableIdentifierCharacters_whenProducedAndDecoded_thenRawAlternativesResolveToTheSameExpectedField() {
    // Arrange
    var ignorables = List.of(Character.toString(0), Character.toString(8), Character.toString(0x200C), Character.toString(0x200D));

    // Act
    var tests = ignorables.stream().map(ignorable -> DynamicTest.dynamicTest(
        "givenIgnorableCodePoint" + (int) ignorable.charAt(0) + "_whenProducedAndDecoded_thenFieldBindingIgnoresOnlyThatCharacter", () -> {
          // Arrange
          var declared = "co" + ignorable + "unt";
          var reference = "c" + ignorable + "ount";
          var fieldText = "int " + declared + " = 5;";
          var expression = "count + " + reference;
          var source = "class Example { " + fieldText + " int value() { return " + expression + "; } }";
          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertCompleteCapture(ast, source);
            var field = occurrence(ast, SyntaxKind.FIELD_DECLARATION, fieldText).value(SyntaxValue.VariableDeclaration.class);
            assertEquals("count", field.name());
            assertEquals(typeKey() + "#field:count", field.declarationKey());
            var sum = occurrence(ast, SyntaxKind.BINARY, expression);
            assertReference(sum.requiredChild(SyntaxRole.LEFT_OPERAND), ReferenceKind.FIELD, typeKey() + "#field:count", "count", source);
            assertReference(sum.requiredChild(SyntaxRole.RIGHT_OPERAND), ReferenceKind.FIELD, typeKey() + "#field:count", reference, source);
            assertSpan(sum.requiredChild(SyntaxRole.LEFT_OPERAND), source.indexOf(expression), "count".length());
            assertSpan(sum.requiredChild(SyntaxRole.RIGHT_OPERAND), source.indexOf(expression) + "count + ".length(), reference.length());
          }
        }));
    // Assert
    return tests;
  }

  @Test
  void givenCanonicallyEquivalentDistinctIdentifiers_whenProducedAndDecoded_thenEachRawSpellingKeepsItsOwnDeclarationTarget() {
    // Arrange
    var composed = Character.toString(0x00E9);
    var decomposed = "e" + Character.toString(0x0301);
    var source = "class Example { int " + composed + " = 2; int " + decomposed + " = 3; int value() { return "
        + composed + " + " + decomposed + "; } }";

    // Act
    var original = produce(source).result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    for (var ast : List.of(original, decoded)) {
      assertCompleteCapture(ast, source);
      for (var name : List.of(composed, decomposed)) {
        var field = occurrence(ast, SyntaxKind.FIELD_DECLARATION, "int " + name + " = " + (name.equals(composed) ? 2 : 3) + ";");
        assertEquals(name, field.value(SyntaxValue.VariableDeclaration.class).name());
        assertEquals(typeKey() + "#field:" + name, field.value(SyntaxValue.VariableDeclaration.class).declarationKey());
      }
      var sum = occurrence(ast, SyntaxKind.BINARY, composed + " + " + decomposed);
      assertReference(sum.requiredChild(SyntaxRole.LEFT_OPERAND), ReferenceKind.FIELD, typeKey() + "#field:" + composed, composed, source);
      assertReference(sum.requiredChild(SyntaxRole.RIGHT_OPERAND), ReferenceKind.FIELD, typeKey() + "#field:" + decomposed, decomposed, source);
      var expressionStart = source.indexOf("return ") + "return ".length();
      assertSpan(sum.requiredChild(SyntaxRole.LEFT_OPERAND), expressionStart, composed.length());
      assertSpan(sum.requiredChild(SyntaxRole.RIGHT_OPERAND), expressionStart + composed.length() + " + ".length(), decomposed.length());
      assertNotEquals(sum.requiredChild(SyntaxRole.LEFT_OPERAND).semanticReference(), sum.requiredChild(SyntaxRole.RIGHT_OPERAND).semanticReference());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenContextualWordsAsFieldAndMethodNames_whenProducedAndDecoded_thenNamesKindsAndTargetsRemainIdentifiers() {
    // Arrange
    var names = List.of("record", "var", "yield", "sealed", "permits", "module", "open", "exports", "opens", "requires",
        "transitive", "to", "uses", "provides", "with", "when");

    // Act
    var tests = names.stream().map(name -> DynamicTest.dynamicTest(
        "givenContextualName" + name + "_whenProducedAndDecoded_thenFieldAndQualifiedMethodTargetsRemainExact", () -> {
          // Arrange
          var fieldText = "int " + name + " = 4;";
          var methodText = "int " + name + "() { return this." + name + "; }";
          var source = "class Example { " + fieldText + " " + methodText + " int value() { return this." + name + "(); } }";
          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertCompleteCapture(ast, source);
            var field = occurrence(ast, SyntaxKind.FIELD_DECLARATION, fieldText);
            assertEquals(JavaSourceKind.VARIABLE, field.sourceKind());
            assertEquals(name, field.value(SyntaxValue.VariableDeclaration.class).name());
            assertEquals(typeKey() + "#field:" + name, field.value(SyntaxValue.VariableDeclaration.class).declarationKey());
            var method = occurrence(ast, SyntaxKind.METHOD_DECLARATION, methodText);
            assertEquals(JavaSourceKind.METHOD, method.sourceKind());
            assertEquals(name, method.value(SyntaxValue.ExecutableDeclaration.class).name());
            assertEquals(typeKey() + "#" + name + "()", method.value(SyntaxValue.ExecutableDeclaration.class).declarationKey());
            var access = occurrence(ast, SyntaxKind.FIELD_ACCESS, "this." + name);
            assertReference(access, ReferenceKind.FIELD, typeKey() + "#field:" + name, "this." + name, source);
            var invocation = occurrence(ast, SyntaxKind.INVOCATION, "this." + name + "()");
            assertReference(invocation, ReferenceKind.METHOD, typeKey() + "#" + name + "()", "this." + name + "()", source);
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenRestrictedTypeNames_whenProduced_thenTheSpecificCompilerRestrictionPreventsCompleteness() {
    // Arrange
    var names = List.of("permits", "record", "sealed", "var", "yield");

    // Act
    var tests = names.stream().map(name -> DynamicTest.dynamicTest(
        "givenRestrictedTypeName" + name + "_whenProduced_thenRestrictedTypeDiagnosticIsRetained", () -> {
          // Arrange
          var source = "class " + name + " {} class Neighbor { int preserved() { return 19; } }";
          // Act
          var outcome = produce(source);

          // Assert
          assertCompilerRejection(outcome, "compiler.err.restricted.type.not.allowed");
          assertNeighbor(outcome);
        }));
    // Assert
    return tests;
  }

  @Test
  void givenUnqualifiedYieldInvocation_whenProduced_thenTheSpecificMethodRestrictionPreventsCompleteness() {
    // Arrange
    var source = "class Example { int yield() { return 1; } int value() { return yield(); } } "
        + "class Neighbor { int preserved() { return 19; } }";

    // Act
    var outcome = produce(source);

    // Assert
    assertCompilerRejection(outcome, "compiler.err.invalid.yield");
    assertNeighbor(outcome);
  }

  @Test
  void givenAdjacentOperatorsAndCommentSeparators_whenProducedAndDecoded_thenOperatorKindsChildOrderAndRawRangesRemainDistinct() {
    // Arrange
    var source = "class Example { int value(int a, int b) { int compact = a+++b; int separated = a+/* gap */++b; "
        + "int minus = a-/* gap */-b; int shift = a>>>b; return compact + separated + minus + shift; } }";

    // Act
    var original = produce(source).result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    for (var ast : List.of(original, decoded)) {
      assertCompleteCapture(ast, source);
      assertBinary(ast, "a+++b", SyntaxOperator.PLUS, "a++", "b");
      assertBinary(ast, "a+/* gap */++b", SyntaxOperator.PLUS, "a", "++b");
      assertBinary(ast, "a-/* gap */-b", SyntaxOperator.MINUS, "a", "-b");
      assertBinary(ast, "a>>>b", SyntaxOperator.UNSIGNED_RIGHT_SHIFT, "a", "b");
      for (var expected : List.of(new OperatorCase("a++", JavaSourceKind.POSTFIX_INCREMENT, SyntaxOperator.POSTFIX_INCREMENT),
          new OperatorCase("++b", JavaSourceKind.PREFIX_INCREMENT, SyntaxOperator.PREFIX_INCREMENT),
          new OperatorCase("-b", JavaSourceKind.UNARY_MINUS, SyntaxOperator.UNARY_MINUS))) {
        var node = occurrence(ast, expected.raw().equals("-b") ? SyntaxKind.UNARY : SyntaxKind.UPDATE, expected.raw(),
            source.lastIndexOf(expected.raw()));
        assertEquals(expected.kind(), node.sourceKind());
        assertEquals(expected.operator(), node.value(SyntaxValue.Operator.class).operator());
        assertEquals(expected.raw().contains("a") ? "a" : "b", slice(node.requiredChild(SyntaxRole.OPERAND), source));
        var operandStart = source.lastIndexOf(expected.raw()) + (expected.raw().startsWith("a") ? 0 : expected.raw().length() - 1);
        assertSpan(node.requiredChild(SyntaxRole.OPERAND), operandStart, 1);
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> givenNestedGenericClosersAndCommentSeparatedKeywords_whenProducedAndDecoded_thenTypeArgumentTopologyRemainsWritten() {
    // Arrange
    var separators = List.of("", " ", "/* close */");

    // Act
    var tests = separators.stream().map(separator -> DynamicTest.dynamicTest(
        "givenGenericCloserSeparator" + separators.indexOf(separator) + "_whenProducedAndDecoded_thenTypeArgumentsKeepRawRanges", () -> {
          // Arrange
          var outer = "java.util.List<java.util.List<Integer>" + separator + ">";
          var type = "java.util.Map<String, " + outer + separator + ">";
          var source = "class/* boundary */Example { " + type + " values; int/* boundary */value() { return 9; } }";

          // Act
          var original = produce(source).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var ast : List.of(original, decoded)) {
            assertCompleteCapture(ast, source);
            var declaration = occurrence(ast, SyntaxKind.TYPE_DECLARATION, source);
            assertEquals("Example", declaration.value(SyntaxValue.TypeDeclaration.class).name());
            assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION),
                declaration.children(SyntaxRole.MEMBER).stream().map(SyntaxNode::syntaxKind).toList());
            var field = occurrence(ast, SyntaxKind.FIELD_DECLARATION, type + " values;");
            var declared = field.requiredChild(SyntaxRole.DECLARED_TYPE);
            assertEquals(occurrence(ast, SyntaxKind.TYPE_USE, type), declared);
            assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, declared.sourceKind());
            assertEquals(List.of("String", outer),
                declared.children(SyntaxRole.TYPE_ARGUMENT).stream().map(node -> slice(node, source)).toList());
            assertEquals(List.of(0, 1), declared.children().stream().filter(child -> child.role() == SyntaxRole.TYPE_ARGUMENT)
                .map(child -> child.position()).toList());
            var outerList = declared.children(SyntaxRole.TYPE_ARGUMENT).get(1);
            assertEquals(occurrence(ast, SyntaxKind.TYPE_USE, outer), outerList);
            assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, outerList.sourceKind());
            var innerList = outerList.requiredChild(SyntaxRole.TYPE_ARGUMENT);
            assertEquals(occurrence(ast, SyntaxKind.TYPE_USE, "java.util.List<Integer>"), innerList);
            assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, innerList.sourceKind());
            assertEquals("Integer", slice(innerList.requiredChild(SyntaxRole.TYPE_ARGUMENT), source));
            occurrence(ast, SyntaxKind.METHOD_DECLARATION, "int/* boundary */value() { return 9; }");
          }
        }));
    // Assert
    return tests;
  }

  @Test
  void givenContextualTypeAndUnqualifiedMethodNames_whenProducedAndDecoded_thenLegalNamesHaveExactTargets() {
    // Arrange
    var source = "class module { int record() { return 1; } int var() { return record(); } "
        + "int sealed() { return var(); } int value() { return sealed(); } }";
    var ownerKey = new JavaTypeDeclarationIdentity(Optional.empty(), "module").declarationKey();

    // Act
    var original = produce(source).result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    for (var ast : List.of(original, decoded)) {
      assertCompleteCapture(ast, source);
      var type = occurrence(ast, SyntaxKind.TYPE_DECLARATION, source);
      assertEquals("module", type.value(SyntaxValue.TypeDeclaration.class).name());
      assertEquals(ownerKey, type.value(SyntaxValue.TypeDeclaration.class).declarationKey());
      for (var name : List.of("record", "var", "sealed")) {
        var invocation = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION
            && node.value(SyntaxValue.Invocation.class).name().equals(name)).findFirst().orElseThrow();
        assertReference(invocation, ReferenceKind.METHOD, ownerKey + "#" + name + "()", name + "()", source);
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> givenUnicodeProducedLineBreakInsideLiteral_whenProduced_thenTheActualUnclosedLiteralDiagnosticIsRetained() {
    // Arrange
    var breaks = List.of("000a", "000d");

    // Act
    var tests = breaks.stream().map(hexadecimal -> DynamicTest.dynamicTest(
        "givenLiteralLineBreak" + hexadecimal + "_whenProduced_thenLiteralCannotBeComplete", () -> {
          // Arrange
          var source = "class Example { String value() { return \"before" + unicode(hexadecimal) + "after\"; } }";
          // Act
          var outcome = produce(source);

          // Assert
          assertCompilerRejection(outcome, "compiler.err.unclosed.str.lit");
        }));
    // Assert
    return tests;
  }

  @Test
  void givenTerminalSubAfterACompleteDeclaration_whenCapturedAndDecoded_thenOnlyTheEnvelopeIncludesTheTerminator() throws IOException {
    // Arrange
    var declaration = "class Example { int value() { return 6; } }";
    var source = declaration + Character.toString(0x1A);
    Files.writeString(directory.resolve("Example.java"), source);

    // Act
    var original = JavaFrontends.standard().produce(request(new SourceInput.Root(directory))).result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    for (var ast : List.of(original, decoded)) {
      assertCompleteCapture(ast, source);
      var root = ast.aggregate().roots().getFirst();
      var type = occurrence(ast, SyntaxKind.TYPE_DECLARATION, declaration);
      assertEquals(List.of(type), root.children(SyntaxRole.TYPE_DECLARATION));
      occurrence(ast, SyntaxKind.METHOD_DECLARATION, "int value() { return 6; }");
      occurrence(ast, SyntaxKind.RETURN, "return 6;");
      assertTrue(ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() != SyntaxKind.COMPILATION_UNIT)
          .allMatch(node -> node.origin().orElseThrow().endOffsetExclusive() <= declaration.length()));
      assertEquals(List.of(SyntaxRole.TYPE_DECLARATION), root.children().stream().map(child -> child.role()).toList());
    }
  }

  private record LiteralCase(String name, String raw, String expected) {
  }

  private record OperatorCase(String raw, JavaSourceKind kind, SyntaxOperator operator) {
  }

  private static void assertBinary(AstResult ast, String raw, SyntaxOperator operator, String left, String right) {
    var node = occurrence(ast, SyntaxKind.BINARY, raw);
    assertEquals(JavaSourceKind.valueOf(operator.name()), node.sourceKind());
    assertEquals(operator, node.value(SyntaxValue.Operator.class).operator());
    assertEquals(List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND), node.children().stream().map(child -> child.role()).toList());
    assertEquals(List.of(0, 0), node.children().stream().map(child -> child.position()).toList());
    var source = ast.snapshot().sourceFiles().getFirst().content();
    assertEquals(left, slice(node.requiredChild(SyntaxRole.LEFT_OPERAND), source));
    assertEquals(right, slice(node.requiredChild(SyntaxRole.RIGHT_OPERAND), source));
    assertSpan(node.requiredChild(SyntaxRole.LEFT_OPERAND), source.indexOf(raw), left.length());
    assertSpan(node.requiredChild(SyntaxRole.RIGHT_OPERAND), source.indexOf(raw) + raw.lastIndexOf(right), right.length());
  }

  private static String typeKey() {
    return new JavaTypeDeclarationIdentity(Optional.empty(), "Example").declarationKey();
  }

  private static void assertReference(SyntaxNode node, ReferenceKind kind, String key, String raw, String source) {
    assertEquals(raw, slice(node, source));
    var reference = node.semanticReference().orElseThrow();
    assertEquals(kind, reference.referenceKind());
    assertEquals(ResolutionStatus.RESOLVED, reference.status());
    assertEquals(Optional.of(key), reference.symbolKey());
  }

  private static void assertCompilerRejection(AstOutcome outcome, String code) {
    assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().equals(code)),
        outcome.diagnostics().toString());
  }

  private static void assertNeighbor(AstOutcome outcome) {
    var ast = outcome.result().orElseThrow();
    var method = occurrence(ast, SyntaxKind.METHOD_DECLARATION, "int preserved() { return 19; }");
    var owner = new JavaTypeDeclarationIdentity(Optional.empty(), "Neighbor").declarationKey();
    assertEquals(owner + "#preserved()", method.value(SyntaxValue.ExecutableDeclaration.class).declarationKey());
    assertEquals(owner, method.value(SyntaxValue.ExecutableDeclaration.class).ownerKey());
  }

  private static void assertSpan(SyntaxNode node, int start, int length) {
    assertEquals(new SourceSpan(node.sourceFileKey(), start, start + length), node.origin().orElseThrow());
  }

  private AstOutcome produce(String source) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return JavaFrontends.standard().produce(request(new SourceInput.Snapshot(snapshot)));
  }

  private static AstProductionRequest request(SourceInput input) {
    return new AstProductionRequest(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static String unicode(String hexadecimal) {
    return "\\" + "u" + hexadecimal;
  }

  private static void assertCompleteCapture(AstResult ast, String source) {
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, ast.profile());
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertFalse(ast.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()), ast.diagnostics().toString());
    var file = ast.snapshot().sourceFiles().getFirst();
    assertEquals(source, file.content());
    var root = ast.aggregate().roots().getFirst();
    assertEquals(new SourceSpan(file.sourceFileKey(), 0, source.length()), root.origin().orElseThrow());
  }

  private static SyntaxNode occurrence(AstResult ast, SyntaxKind kind, String raw) {
    return occurrence(ast, kind, raw, ast.snapshot().sourceFiles().getFirst().content().indexOf(raw));
  }

  private static SyntaxNode occurrence(AstResult ast, SyntaxKind kind, String raw, int start) {
    var source = ast.snapshot().sourceFiles().getFirst().content();
    var matches = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind && node.origin().isPresent())
        .filter(node -> raw.equals(slice(node, source))).toList();
    assertEquals(1, matches.size(), kind + ": " + raw);
    var node = matches.getFirst();
    assertEquals(new SourceSpan(node.sourceFileKey(), start, start + raw.length()), node.origin().orElseThrow());
    return node;
  }

  private static String slice(SyntaxNode node, String source) {
    var span = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }
}
