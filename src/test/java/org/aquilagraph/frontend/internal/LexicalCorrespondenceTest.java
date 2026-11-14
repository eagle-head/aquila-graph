package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
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
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class LexicalCorrespondenceTest {
  @TempDir
  Path directory;

  @Test
  void givenTranslatedEnumAndConstructorSource_whenProducedRetainedAndDerived_thenDetachedFactsKeepExactRawCorrespondence() {
    // Arrange
    var constants = constantCases();
    var source = combinedSource(constants);

    // Act
    var outcome = produce(source);
    var original = outcome.result().orElseThrow();
    var encoded = AstCodec.encode(original);
    var retained = AstCodec.decode(encoded, original.identity());
    var graphs = CfgDerivers.standard().derive(retained,
        new CfgRequest(retained.identity(), List.of(methodKey("Example", "make()")), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.isError()), outcome.diagnostics().toString());
    assertEquals(original.identity(), retained.identity());
    assertArrayEquals(encoded, AstCodec.encode(retained));
    for (var ast : List.of(original, retained)) {
      assertEquals(source, ast.snapshot().sourceFiles().getFirst().content());
      assertEquals(new SourceSpan(ast.snapshot().sourceFiles().getFirst().sourceFileKey(), 0, source.length()),
          ast.aggregate().roots().getFirst().origin().orElseThrow());
      var declarations = ast.aggregate().nodes().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();
      assertEquals(constants.size(), declarations.size());
      for (var expected : constants) {
        var node = occurrence(ast, SyntaxKind.ENUM_CONSTANT_DECLARATION, expected.raw());
        var value = node.value(SyntaxValue.VariableDeclaration.class);
        assertEquals(expected.name(), value.name());
        assertEquals(expected.argumentList(), value.argumentList());
        assertEquals(expected.body(), value.constantBody());
        assertEquals(0, value.arguments());
        assertTrue(node.children(SyntaxRole.ARGUMENT).isEmpty());
        var execution = ast.aggregate().executionSyntax(value.declarationKey()).orElseThrow();
        assertSame(node, execution.root());
        assertEquals(Optional.of(methodKey("Example", "<init>()")),
            ((ExecutionScope.EnumConstant) execution.scope()).selectedConstructor().orElseThrow().symbolKey());
      }
      var text = occurrence(ast, SyntaxKind.BINARY, "\"A\" + " + unicode("0022") + "B" + unicode("0022"));
      assertEquals("A", text.requiredChild(SyntaxRole.LEFT_OPERAND).value(SyntaxValue.Literal.class).text());
      assertEquals("B", text.requiredChild(SyntaxRole.RIGHT_OPERAND).value(SyntaxValue.Literal.class).text());
      var creation = occurrence(ast, SyntaxKind.OBJECT_CREATION, "new Box(7)");
      assertEquals(Optional.of(methodKey("Box", "<init>(java.lang.Object)")), creation.requiredReference().symbolKey());
      var body = ast.body(methodKey("Example", "make()")).orElseThrow();
      var conversion = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
      assertEquals("int", conversion.sourceType().displayName());
      assertEquals("java.lang.Integer", conversion.selectedTarget().displayName());
      assertEquals("java.lang.Integer", conversion.boxedType().orElseThrow().displayName());
      assertEquals(InvocationApplicabilityMode.FIXED, body.invocationArity(creation.nodeId()).orElseThrow().mode());
      assertFalse(ast.program().methods().stream().anyMatch(method -> method.name().startsWith("constructorSelection")));
    }
    assertEquals(1, graphs.methods().size());
    var method = graphs.methods().getFirst();
    assertEquals(MethodCfgState.AVAILABLE, method.state(), graphs.diagnostics().toString());
    var constructed = method.graph().orElseThrow().operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.NEW_OBJECT).toList();
    assertEquals(1, constructed.size());
    var creation = occurrence(retained, SyntaxKind.OBJECT_CREATION, "new Box(7)");
    assertEquals(retained.bodyRef(methodKey("Example", "make()")).orElseThrow(), constructed.getFirst().sourceRef().body());
    assertEquals(retained.occurrence(creation).orElseThrow(), constructed.getFirst().sourceRef().occurrence().orElseThrow());
    assertTrue(constructed.getFirst().origins().contains(creation.origin().orElseThrow()));
  }

  @Test
  void givenTranslatedEnumNamesAndTrivia_whenParsedInventoryIsCaptured_thenTokenSpellingMeaningAndRawBoundsRemainDistinct() throws IOException {
    // Arrange
    var constants = constantCases();
    var source = combinedSource(constants);

    // Act
    var tokens = capturedTokens(source);

    // Assert
    for (var expected : constants) {
      var start = source.indexOf(expected.raw());
      var nameEnd = start + expected.rawName().length();
      var names = tokens.within(start, nameEnd);
      assertEquals(List.of(new JavaSourceTokens.Token(JavaSourceTokens.Kind.IDENTIFIER,
          expected.spelling(), expected.name(), start, nameEnd)), names);
      assertTrue(tokens.within(start + 1L, nameEnd).isEmpty());
      assertTrue(tokens.within(start, nameEnd - 1L).isEmpty());
      var contents = tokens.within(start, start + expected.raw().length());
      var expectedValues = expected.argumentList()
          ? List.of(expected.spelling(), "(", ")")
          : expected.body() ? List.of(expected.spelling(), "{", "}") : List.of(expected.spelling());
      assertEquals(expectedValues, contents.stream().map(JavaSourceTokens.Token::value).toList());
    }
    var ownerEnd = source.length();
    var ownerClose = tokens.closingBrace(source.indexOf("class Box"), ownerEnd).orElseThrow();
    assertEquals("}", ownerClose.value());
    assertEquals(ownerEnd - repeatedUnicode("007d").length(), ownerClose.start());
    assertEquals(ownerEnd, ownerClose.end());
    assertEquals(repeatedUnicode("007d"), source.substring(ownerClose.start(), ownerClose.end()));
  }

  @TestFactory
  Stream<DynamicTest> givenMalformedLexicalInput_whenProductionIsFollowedByValidInput_thenCompilerErrorsAndHealthyRecoveryRemainVisible() {
    // Arrange
    var cases = List.of(
        new MalformedCase("nonhexadecimal escape", "enum Example { A " + unicode("00G0") + "(); }", "compiler.err.illegal.unicode.esc"),
        new MalformedCase("truncated escape", "enum Example { A " + "\\" + "uu00", "compiler.err.illegal.unicode.esc"),
        new MalformedCase("nonjava whitespace", "enum Example { A" + Character.toString(0x00a0) + "(); }", "compiler.err.illegal.char"),
        new MalformedCase("unfinished comment", "enum Example { A /* ()", "compiler.err.unclosed.comment"));

    // Act
    var tests = cases.stream().map(example -> DynamicTest.dynamicTest(
        "given" + example.name() + "_whenProducedThenRetried_thenOriginalCompilerErrorAndHealthySourceRemain", () -> {
          // Arrange
          var frontend = JavaFrontends.standard();
          var malformed = request(example.source());
          var healthy = request("enum Example { PLAIN, EMPTY(); Example() {} int value() { return 23; } }");

          // Act
          var failed = frontend.produce(malformed);
          var following = frontend.produce(healthy);

          // Assert
          assertNotEquals(AstCompletion.COMPLETE, failed.completion());
          assertTrue(failed.diagnostics().stream().anyMatch(value -> value.isError() && value.code().equals(example.code())),
              failed.diagnostics().toString());
          assertEquals(AstCompletion.COMPLETE, following.completion(), following.diagnostics().toString());
          assertFalse(following.diagnostics().stream().anyMatch(value -> value.isError()), following.diagnostics().toString());
          var ast = following.result().orElseThrow();
          assertFalse(occurrence(ast, SyntaxKind.ENUM_CONSTANT_DECLARATION, "PLAIN")
              .value(SyntaxValue.VariableDeclaration.class).argumentList());
          assertTrue(occurrence(ast, SyntaxKind.ENUM_CONSTANT_DECLARATION, "EMPTY()")
              .value(SyntaxValue.VariableDeclaration.class).argumentList());
          assertEquals("23", occurrence(ast, SyntaxKind.LITERAL, "23").value(SyntaxValue.Literal.class).text());
        }));

    // Assert
    return tests;
  }

  @Test
  void givenNonAsciiEscapeDigitsAcceptedByCompiler_whenSourceIsProduced_thenUnprovenEnumSpellingIsRefused() throws IOException {
    // Arrange
    var spelling = "\\" + "u" + Character.toString(0xff10).repeat(2) + "41";
    var source = "enum Example { " + spelling + "(); }";
    var frontend = JavaFrontends.standard();

    // Act
    var tokens = capturedTokens(source);
    var outcome = frontend.produce(request(source));
    var following = frontend.produce(request("enum Example { A(); }"));

    // Assert
    assertFalse(tokens.all().stream().anyMatch(token -> token.meaning().equals("A")));
    assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.isError()
        && value.code().equals("frontend.unavailable-enum-argument-list")), outcome.diagnostics().toString());
    assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.code().startsWith("compiler.")), outcome.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, following.completion(), following.diagnostics().toString());
    assertTrue(occurrence(following.result().orElseThrow(), SyntaxKind.ENUM_CONSTANT_DECLARATION, "A()")
        .value(SyntaxValue.VariableDeclaration.class).argumentList());
  }

  private JavaSourceTokens capturedTokens(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var sourceFile = SourceFile.fromContent(Path.of("Example.java"), source);
    var input = SimpleJavaFileObject.forSource(directory.resolve("Example.java").toUri(), source);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
          List.of("--release", "25", "-proc:none"), null, List.of(input));
      var unit = task.parse().iterator().next();
      var inventory = ParsedTreeInventory.capture(List.of(unit), ignored -> Optional.of(sourceFile),
          new JavaSourceEvidence(Trees.instance(task).getSourcePositions()));
      task.analyze();
      assertFalse(diagnostics.getDiagnostics().stream().anyMatch(value -> value.getKind() == Diagnostic.Kind.ERROR),
          diagnostics.getDiagnostics().toString());
      return inventory.tokens(unit);
    }
  }

  private AstOutcome produce(String source) {
    return JavaFrontends.standard().produce(request(source));
  }

  private AstProductionRequest request(String source) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static String combinedSource(List<ConstantCase> constants) {
    return "enum Example { " + String.join(", ", constants.stream().map(ConstantCase::raw).toList())
        + "; Example() {} String text() { return \"A\" + " + unicode("0022") + "B" + unicode("0022")
        + "; } Object make() { return new Box(7); } } class Box { <T> Box(T value) {} " + repeatedUnicode("007d");
  }

  private static List<ConstantCase> constantCases() {
    var empty = "E" + repeatedUnicode("004d") + "PTY";
    var supplementary = unicode("d801") + unicode("dc00");
    var supplementaryName = Character.toString(0x10400);
    var ignorable = "I" + unicode("0000") + "GNORABLE";
    var block = " /* " + "\\".repeat(2) + "u002a/ () */ ";
    var line = " // " + "\\".repeat(2) + "u000a ()\r\n";
    var producedBackslash = " // " + unicode("005c") + "u000a ()\n";
    return List.of(
        new ConstantCase("PLAIN", "PLAIN", "PLAIN", "PLAIN", false, false),
        new ConstantCase(empty, "EMPTY", "EMPTY", empty + block + unicode("0028") + unicode("0029"), true, false),
        new ConstantCase(supplementary, supplementaryName, supplementaryName, supplementary + "()", true, false),
        new ConstantCase(ignorable, "I" + Character.toString(0) + "GNORABLE", "IGNORABLE", ignorable + "()", true, false),
        new ConstantCase("LINE", "LINE", "LINE", "LINE" + line + "()", true, false),
        new ConstantCase("ESCAPED_LINE", "ESCAPED_LINE", "ESCAPED_LINE", "ESCAPED_LINE" + producedBackslash + "()", true, false),
        new ConstantCase("BODY", "BODY", "BODY", "BODY {}", false, true));
  }

  private static SyntaxNode occurrence(AstResult ast, SyntaxKind kind, String raw) {
    var file = ast.snapshot().sourceFiles().getFirst();
    var start = file.content().indexOf(raw);
    assertTrue(start >= 0, raw);
    var origin = new SourceSpan(file.sourceFileKey(), start, start + raw.length());
    var nodes = ast.aggregate().nodes().stream()
        .filter(node -> node.syntaxKind() == kind && node.origin().equals(Optional.of(origin))).toList();
    assertEquals(1, nodes.size(), kind + ": " + raw);
    var node = nodes.getFirst();
    assertSame(node, ast.aggregate().node(node.nodeId()).orElseThrow());
    return node;
  }

  private static String methodKey(String owner, String signature) {
    return new JavaTypeDeclarationIdentity(Optional.empty(), owner).declarationKey() + "#" + signature;
  }

  private static String unicode(String hexadecimal) {
    return "\\" + "u" + hexadecimal;
  }

  private static String repeatedUnicode(String hexadecimal) {
    return "\\" + "uuu" + hexadecimal;
  }

  private record ConstantCase(String rawName, String spelling, String name, String raw, boolean argumentList, boolean body) {}

  private record MalformedCase(String name, String source, String code) {}
}
