package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class QualifiedReceiverProjectionTest {
  @TempDir Path directory;

  @Test
  void givenGenericMemberOfInlineOwner_whenItsConstructorTargetIsRestored_thenActualArgumentsSurvive() {
    // Arrange
    var source = "class Probe { Object make() { return new Object() { class Box<B> { <T> Box(Box<String> other, T value) {} } }.new Box<Integer>(null, 1); } }";

    // Act
    var original = produce(source, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertTarget(original, "<anonymous java.lang.Object>.Box<java.lang.String>");
    assertTarget(decoded, "<anonymous java.lang.Object>.Box<java.lang.String>");
    assertEquals(original.aggregate(), decoded.aggregate());
  }

  @Test
  void givenDiamondMemberInsideAnonymousOwner_whenCorrelated_thenItsLexicalNameRemainsDenotable() {
    // Arrange
    var source = "class Probe { Object make() { return new Object() { class Box<B> { <T> Box(T value) {} } Object read() { return new Box<>(1); } }; } }";

    // Act
    var original = produce(source, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertTarget(original, "java.lang.Integer");
    assertTarget(decoded, "java.lang.Integer");
    assertEquals(original.aggregate(), decoded.aggregate());
  }

  @Test
  void givenNestedCaptureInBinaryMember_whenProducedAndDecoded_thenSelectionSurvivesWithoutChangingEitherDependency() throws IOException {
    // Arrange
    var classes = Files.createDirectories(directory.resolve("classes"));
    var source = "package support; public class Outer<A> { public class Box { public <T> Box(T value) {} } }";
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      assertTrue(compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none", "-d", classes.toString()), null,
          List.of(SimpleJavaFileObject.forSource(URI.create("memory:///support/Outer.java"), source))).call());
      assertTrue(diagnostics.getDiagnostics().isEmpty(), diagnostics.getDiagnostics().toString());
    }
    var outer = classes.resolve("support/Outer.class");
    var member = classes.resolve("support/Outer$Box.class");
    var outerBytes = Files.readAllBytes(outer);
    var memberBytes = Files.readAllBytes(member);

    // Act
    var original = produce("class Holder<A> { support.Outer<java.util.List<A>> outer; } "
        + "class Probe { Object make(Holder<?> holder) { return holder.outer.new Box(1); } }",
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(capturedAbsolute(classes)), List.of(), List.of()));
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertTarget(original, "java.lang.Integer");
    assertTarget(decoded, "java.lang.Integer");
    assertEquals(original.aggregate(), decoded.aggregate());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    assertArrayEquals(outerBytes, Files.readAllBytes(outer));
    assertArrayEquals(memberBytes, Files.readAllBytes(member));
  }

  @TestFactory
  Stream<DynamicTest> givenNestedArgumentAnnotations_whenAnExplicitConstructorTargetIsSelected_thenRecursiveMetadataRemainsIndependent() {
    // Arrange
    var types = List.of(new Target("List<@Mark String>", "List<String>", "java.util.List<java.lang.String>"),
        new Target("@Mark String[]", "String[]", "java.lang.String[]"));

    // Act
    var cases = types.stream().map(type -> DynamicTest.dynamicTest("given" + type.parameter() + "_whenExplicitTargetIsSelected_thenNestedMetadataRemainsIndependent", () -> {
      // Arrange
      var source = "import java.util.List; import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} "
          + "class Outer<A> { class Box { <T> Box(T value) {} } } class Probe { Object make(Outer<Integer> outer, "
          + type.parameter() + " values) { return outer.new <" + type.argument() + "> Box(values); } }";

      // Act
      var original = produce(source, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
      var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

      // Assert
      assertTarget(original, type.selected());
      assertTarget(decoded, type.selected());
      assertEquals(original.aggregate(), decoded.aggregate());
    }));

    // Assert
    return cases;
  }

  @TestFactory
  Stream<DynamicTest> givenAnnotatedMemberArgument_whenConstructorTypeIsSelected_thenClassMetadataCannotReplaceTheConstructorTarget() {
    // Arrange
    var forms = List.of("", "<String> ");

    // Act
    var cases = forms.stream().map(form -> DynamicTest.dynamicTest("given" + (form.isEmpty() ? "Inferred" : "Explicit")
        + "ConstructorArgument_whenSelected_thenClassMetadataRemainsIndependent", () -> assertIndependentTarget(form)));

    // Assert
    return cases;
  }

  private void assertIndependentTarget(String constructorArgument) {
    // Arrange
    var source = "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} "
        + "class Outer<A> { class Box<B> { <T> Box(T value) {} } } "
        + "class Probe { Object make(Outer<Integer> outer) { return outer.new " + constructorArgument + "Box<@Mark String>(\"value\"); } }";

    // Act
    var original = produce(source, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertTarget(original, "java.lang.String");
    assertTarget(decoded, "java.lang.String");
    assertEquals(1, original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .map(node -> node.origin().orElseThrow())
        .filter(range -> source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())).equals("@Mark")).count());
    assertEquals(original.aggregate(), decoded.aggregate());
  }

  private AstResult produce(String source, CompilerConfiguration configuration) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory.resolve("input")), List.of(SourceFile.fromContent(capturedPath(Path.of("Probe.java")), source)));
    var outcome = JavaFrontends.standard().produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot), configuration,
        AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }

  private static void assertTarget(AstResult result, String target) {
    var creations = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .filter(node -> !node.children(SyntaxRole.ARGUMENT).isEmpty()).toList();
    assertEquals(1, creations.size());
    var creation = creations.getFirst();
    var scopes = result.aggregate().executionScopes().stream().map(scope -> result.aggregate().executionSyntax(scope.key()).orElseThrow())
        .filter(scope -> scope.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).isPresent()).toList();
    assertEquals(1, scopes.size());
    var scope = scopes.getFirst();
    assertEquals(target, scope.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
    assertEquals(InvocationApplicabilityMode.FIXED, scope.invocationArity(creation.nodeId()).orElseThrow().mode());
  }

  private record Target(String parameter, String argument, String selected) {
  }
}
