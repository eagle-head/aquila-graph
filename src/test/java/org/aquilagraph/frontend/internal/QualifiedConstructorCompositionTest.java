package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class QualifiedConstructorCompositionTest {
  private static final String DECLARATIONS = """
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Target;
      @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
      class Outer { class Box { <T> Box(T value) {} } }
      """;

  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenAnnotatedEnclosingExpressions_whenProducedAndDecoded_thenConstructorFactsAndWrittenAnnotationsSurvive() {
    // Arrange
    var cases = List.of(
        new Receiver("Variable", "", "@Mark(1) Outer outer", "outer"),
        new Receiver("Parenthesized", "", "@Mark(1) Outer outer", "(outer)"),
        new Receiver("Assignment", "", "@Mark(1) Outer outer, @Mark(2) Outer replacement", "(outer = replacement)"),
        new Receiver("Cast", "", "Outer outer", "((@Mark(1) Outer) outer)"),
        new Receiver("MethodResult", "@Mark(1) Outer owner() { return null; }", "", "owner()"),
        new Receiver("Field", "@Mark(1) Outer outer;", "", "this.outer"),
        new Receiver("ArrayElement", "", "@Mark(1) Outer[] owners", "owners[0]"),
        new Receiver("Conditional", "", "boolean flag, @Mark(1) Outer outer, @Mark(2) Outer replacement", "(flag ? outer : replacement)"),
        new Receiver("NewEnclosingInstance", "", "", "new @Mark(1) Outer()"));

    // Act
    var observations = cases.stream().map(example -> DynamicTest.dynamicTest("givenAnnotated" + example.name()
        + "_whenQualifiedConstructorIsProducedAndDecoded_thenSelectedFactsAndSourceSurvive", () -> {
          // Arrange
          var creation = example.expression() + ".new Box(1)";
          var source = DECLARATIONS + "class Probe { " + example.members() + " Object make(" + example.parameters()
              + ") { return " + creation + "; } }";
          assertCompilerAccepts(source);

          // Act
          var outcome = produce(source);

          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
          var original = outcome.result().orElseThrow();
          assertEquals("javac-public/14", original.identity().semanticProductionRevision());
          var bytes = AstCodec.encode(original);
          var decoded = AstCodec.decode(bytes, original.identity());
          for (var ast : List.of(original, decoded)) {
            assertBoxSelection(ast, source, creation);
            assertWrittenAnnotations(ast, source);
          }
          assertEquals(original.identity(), decoded.identity());
          assertEquals(original.aggregate(), decoded.aggregate());
          assertEquals(original.program(), decoded.program());
          assertEquals(original.coverage(), decoded.coverage());
          assertArrayEquals(bytes, AstCodec.encode(decoded));
        }));

    // Assert
    return observations;
  }

  private static void assertBoxSelection(AstResult ast, String source, String writtenCreation) {
    assertEquals(source, ast.snapshot().sourceFiles().getFirst().content());
    var start = source.indexOf(writtenCreation);
    var sourceKey = ast.snapshot().sourceFiles().getFirst().sourceFileKey();
    var creation = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .filter(node -> node.origin().equals(Optional.of(new SourceSpan(sourceKey, start, start + writtenCreation.length()))))
        .findFirst().orElseThrow();
    var owner = ast.program().types().stream().filter(type -> type.qualifiedName().equals("Outer.Box")).findFirst().orElseThrow();
    var constructor = owner.methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow();
    assertEquals(constructor.methodKey(), creation.semanticReference().orElseThrow().symbolKey().orElseThrow());
    var method = ast.program().methods().stream().filter(value -> value.name().equals("make")).findFirst().orElseThrow();
    var body = ast.body(method.methodKey()).orElseThrow();
    var argument = creation.requiredChild(SyntaxRole.ARGUMENT);
    assertEquals("1", slice(source, argument));
    assertEquals(creation.structuralPath().child(SyntaxRole.ARGUMENT, 0), argument.structuralPath());
    var conversion = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
    assertEquals(method.methodKey(), conversion.scopeKey());
    assertEquals("int", conversion.sourceType().displayName());
    assertEquals("java.lang.Integer", conversion.selectedTarget().displayName());
    assertEquals("int", conversion.preBoxPrimitiveType().orElseThrow().displayName());
    assertEquals("java.lang.Integer", conversion.boxedType().orElseThrow().displayName());
    var arity = body.invocationArity(creation.nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.FIXED, arity.mode());
    assertEquals(1, arity.formalCount());
    assertFalse(ast.program().methods().stream().anyMatch(value -> value.name().startsWith("constructorSelection")));
  }

  @Test
  void givenAnnotatedGenericOwner_whenConstructorsCompose_thenClassArgumentsInferenceAndCapturesRemainDistinct() throws IOException {
    // Arrange
    var source = DECLARATIONS.replace("class Outer { class Box { <T> Box(T value) {} } }", """
        class Outer<A extends Number> {
          class Box<B> { <T extends B> Box(T value, A enclosing) {} }
          class Captured { <T extends A> Captured(T value) {} }
        }
        """) + """
        class Probe {
          Object fixed(@Mark(1) Outer<Integer> outer) { return outer.new Box<Number>(1, 2); }
          Outer<Integer>.Box<Number> diamond(@Mark(2) Outer<Integer> outer) { return outer.new Box<>(1, 2); }
          Object explicit(@Mark(3) Outer<Integer> outer) { return outer.new <Integer> Box<Number>(1, 2); }
          Object captured(@Mark(5) Outer<? extends Number> outer) { return outer.new Captured(null); }
        }
        """;
    assertCompilerAccepts(source);

    // Act
    var outcome = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    for (var ast : List.of(original, decoded)) {
      for (var name : List.of("fixed", "diamond", "explicit", "captured")) {
        var method = ast.program().methods().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
        var body = ast.body(method.methodKey()).orElseThrow();
        var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
            .findFirst().orElseThrow();
        var arity = body.invocationArity(creation.nodeId()).orElseThrow();
        assertEquals(InvocationApplicabilityMode.FIXED, arity.mode());
        if (name.equals("captured")) {
          assertEquals(1, arity.formalCount());
          var target = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget();
          assertEquals(JavaTypeCategory.TYPE_VARIABLE, target.category());
          assertEquals("java.lang.Number", target.erasedName());
        } else {
          assertEquals(2, arity.formalCount());
          for (var position : List.of(0, 1)) {
            var argument = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, position).orElseThrow();
            assertEquals("java.lang.Integer", argument.selectedTarget().displayName());
            assertEquals("int", argument.preBoxPrimitiveType().orElseThrow().displayName());
            assertEquals("java.lang.Integer", argument.boxedType().orElseThrow().displayName());
          }
        }
      }
      assertWrittenAnnotations(ast, source);
    }
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.identity(), decoded.identity());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
  }

  @TestFactory
  Stream<DynamicTest> givenWildcardEnclosingExpressions_whenProducedAndDecoded_thenOriginalCaptureAndAnnotationsSurvive() {
    // Arrange
    var cases = List.of(
        new Receiver("AnnotatedCapture", "", "@Mark(1) Outer<@Mark(2) ? extends @Mark(3) Number> outer", "outer"),
        new Receiver("ComputedCapture", "@Mark(1) Outer<? extends @Mark(2) Number> owner() { return null; }", "", "owner()"),
        new Receiver("CastCapture", "", "Outer<?> outer", "((@Mark(1) Outer<? extends @Mark(2) Number>) outer)"));

    // Act
    var observations = cases.stream().map(example -> DynamicTest.dynamicTest("given" + example.name()
        + "_whenQualifiedConstructorIsProducedAndDecoded_thenCapturedTargetAndSourceSurvive", () -> {
          // Arrange
          var source = DECLARATIONS.replace("class Outer { class Box { <T> Box(T value) {} } }",
              "class Outer<A extends Number> { class Box { <T extends A> Box(T value) {} } }")
              + "class Probe { " + example.members() + " Object make(" + example.parameters() + ") { return "
              + example.expression() + ".new Box(null); } }";
          assertCompilerAccepts(source);

          // Act
          var outcome = produce(source);

          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          for (var ast : List.of(original, decoded)) {
            var method = ast.program().methods().stream().filter(value -> value.name().equals("make")).findFirst().orElseThrow();
            var body = ast.body(method.methodKey()).orElseThrow();
            var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
                .findFirst().orElseThrow();
            assertEquals(example.expression() + ".new Box(null)", slice(source, creation));
            var target = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget();
            assertEquals(JavaTypeCategory.TYPE_VARIABLE, target.category());
            assertEquals("java.lang.Number", target.erasedName());
            assertEquals(InvocationApplicabilityMode.FIXED, body.invocationArity(creation.nodeId()).orElseThrow().mode());
            assertWrittenAnnotations(ast, source);
          }
          assertEquals(original.aggregate(), decoded.aggregate());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
        }));

    // Assert
    return observations;
  }

  private static void assertWrittenAnnotations(AstResult ast, String source) {
    for (var start = source.indexOf("@Mark("); start >= 0; start = source.indexOf("@Mark(", start + 1)) {
      var annotation = source.substring(start, source.indexOf(')', start) + 1);
      var offset = start;
      var matches = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
          .filter(node -> node.origin().orElseThrow().startOffset() == offset).toList();
      assertEquals(1, matches.size(), annotation);
      assertEquals(annotation, slice(source, matches.getFirst()));
      assertEquals(SyntaxValue.AnnotationLocation.TYPE_USE, matches.getFirst().value(SyntaxValue.Annotation.class).location());
    }
  }

  @Test
  void givenQualifiedCreationsAcrossRecursiveScopes_whenProducedAndDecoded_thenEveryArgumentKeepsItsCanonicalExecutionOwner()
      throws IOException {
    // Arrange
    var source = "import java.util.function.Supplier;\n" + DECLARATIONS + """
        class Probe {
          @Mark(1) Outer outer;
          Object field = outer.new Box(1);
          { Object local = outer.new Box(2); }
          Object make() {
            class Local { Object value() { return outer.new Box(3); } }
            return new Local().value();
          }
          Supplier<Object> action = () -> outer.new Box(4);
          Object anonymous() { return outer.new Box(5) {}; }
          static @Mark(2) Outer owner() { return null; }
        }
        enum Token { ONE(Probe.owner().new Box(6)); Token(Object value) {} }
        """;
    assertCompilerAccepts(source);

    // Act
    var outcome = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    for (var ast : List.of(original, decoded)) {
      var creations = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
          .filter(node -> slice(source, node).contains(".new Box(")).toList();
      assertEquals(6, creations.size());
      for (var creation : creations) {
        var argument = creation.requiredChild(SyntaxRole.ARGUMENT);
        var literal = argument.value(SyntaxValue.Literal.class).text();
        var owners = ast.aggregate().executionScopes().stream()
            .map(scope -> ast.aggregate().executionSyntax(scope.key()).orElseThrow())
            .filter(scope -> scope.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).isPresent()).toList();
        assertEquals(1, owners.size(), literal);
        var owner = owners.getFirst();
        var expectedScope = switch (literal) {
          case "1", "4" -> ExecutionScope.Field.class;
          case "2" -> ExecutionScope.Initializer.class;
          case "3", "5" -> ExecutionScope.Method.class;
          case "6" -> ExecutionScope.EnumConstant.class;
          default -> throw new AssertionError("Unexpected argument: " + literal);
        };
        assertEquals(expectedScope, owner.scope().getClass());
        var conversion = owner.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
        assertEquals(owner.scopeKey(), conversion.scopeKey());
        assertEquals("java.lang.Integer", conversion.selectedTarget().displayName());
        assertEquals("int", conversion.preBoxPrimitiveType().orElseThrow().displayName());
        assertEquals("java.lang.Integer", conversion.boxedType().orElseThrow().displayName());
        assertEquals(InvocationApplicabilityMode.FIXED, owner.invocationArity(creation.nodeId()).orElseThrow().mode());
      }
      assertWrittenAnnotations(ast, source);
    }
    assertEquals(original.aggregate(), decoded.aggregate());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
  }

  @Test
  void givenRecursiveAnnotatedCapture_whenProducedAndDecoded_thenSelectedTargetKeepsItsRecursiveBound() throws IOException {
    // Arrange
    var source = DECLARATIONS.replace("class Outer { class Box { <T> Box(T value) {} } }",
        "class Outer<A extends Comparable<A>> { class Box { <T extends A> Box(T value) {} } }")
        + "class Probe { Object make(@Mark(1) Outer<?> outer) { return outer.new Box(null); } }";
    assertCompilerAccepts(source);

    // Act
    var outcome = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    for (var ast : List.of(original, decoded)) {
      var method = ast.program().methods().stream().filter(value -> value.name().equals("make")).findFirst().orElseThrow();
      var body = ast.body(method.methodKey()).orElseThrow();
      var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
          .findFirst().orElseThrow();
      var conversion = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
      assertEquals(JavaTypeCategory.NULL, conversion.sourceType().category());
      assertEquals(JavaTypeCategory.TYPE_VARIABLE, conversion.selectedTarget().category());
      assertEquals("java.lang.Comparable", conversion.selectedTarget().erasedName());
      assertEquals(InvocationApplicabilityMode.FIXED, body.invocationArity(creation.nodeId()).orElseThrow().mode());
      assertWrittenAnnotations(ast, source);
    }
    assertEquals(original.aggregate(), decoded.aggregate());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
  }

  @Test
  void givenSuppliedPreviousRevision_whenDecoded_thenRecordedIdentityAndCompletionRemainUnchanged() {
    // Arrange
    var source = DECLARATIONS + "class Probe { Object make(Outer outer) { return outer.new Box(1); } }";
    var current = produce(source).result().orElseThrow();
    var copied = new SyntaxAnalysisInput(current.snapshot(), current.environment(), current.program(), Optional.of(current.aggregate()),
        current.diagnostics(), current.coverage());
    var previous = AstResultAdmission.admit(copied, current.profile(), "javac-public/12");
    var bytes = AstCodec.encode(previous);

    // Act
    var decoded = AstCodec.decode(bytes, previous.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, current.completion());
    assertEquals("javac-public/14", current.identity().semanticProductionRevision());
    assertEquals("javac-public/12", decoded.identity().semanticProductionRevision());
    assertEquals(previous.identity(), decoded.identity());
    assertEquals(previous.completion(), decoded.completion());
    assertEquals(previous.aggregate(), decoded.aggregate());
    assertFalse(current.identity().equals(decoded.identity()));
    assertArrayEquals(bytes, AstCodec.encode(decoded));
  }

  private static String slice(String source, SyntaxNode node) {
    var range = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
  }

  private AstOutcome produce(String source) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Probe.java")), source)));
    return JavaFrontends.standard().produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
  }

  private static void assertCompilerAccepts(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null,
          List.of(SimpleJavaFileObject.forSource(URI.create("memory:///Probe.java"), source)));
      task.parse();
      task.analyze();
      assertTrue(diagnostics.getDiagnostics().isEmpty(), diagnostics.getDiagnostics().toString());
    }
  }

  private record Receiver(String name, String members, String parameters, String expression) {
  }
}
