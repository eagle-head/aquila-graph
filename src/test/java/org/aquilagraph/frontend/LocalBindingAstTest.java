package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class LocalBindingAstTest {
  @TempDir Path directory;

  @Test
  void givenAnnotatedDeclaredInferredAndUnnamedLocals_whenProducedAndDecoded_thenWrittenStructureAndBindingScopesSurviveClosure()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target({ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Dual { int value(); }
        class Example {
          int subject(int[] input) {
            @Dual(3) final int first = 1, second[] = input;
            int later;
            var inferred = first;
            int _ = 7;
            int _ = 9;
            { int shared = 3; later = shared; }
            { int shared = 5; later = shared; }
            return later + inferred;
          }
        }
        """;

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var locals = locals(decoded);
    var first = named(locals, "first");
    var second = named(locals, "second");
    var inferred = named(locals, "inferred");
    var unnamed = locals.stream().filter(node -> node.value(SyntaxValue.LocalDeclaration.class).name().equals("_")).toList();
    var shadowed = locals.stream().filter(node -> node.value(SyntaxValue.LocalDeclaration.class).name().equals("shared")).toList();
    var accesses = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(result.aggregate(), decoded.aggregate());
    assertEquals(result.coverage(), decoded.coverage());
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
    assertEquals(List.of("first", "second", "later", "inferred", "_", "_", "shared", "shared"),
        locals.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
    assertEquals(List.of("final"), first.value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
    assertEquals(List.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE, SyntaxRole.INITIALIZER),
        first.children().stream().map(child -> child.role()).toList());
    assertEquals(JavaTypeCategory.INT, ((SyntaxType.Resolved) first.type()).identity().category());
    assertEquals(JavaTypeCategory.ARRAY, ((SyntaxType.Resolved) second.type()).identity().category());
    assertEquals("1", first.requiredChild(SyntaxRole.INITIALIZER).value(SyntaxValue.Literal.class).text());
    assertEquals("input", second.requiredChild(SyntaxRole.INITIALIZER).value(SyntaxValue.SourceSymbol.class).name());
    assertFalse(named(locals, "later").optionalChild(SyntaxRole.INITIALIZER).isPresent());
    assertEquals(LocalBindingEvidence.TypeForm.INFERRED, inferred.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm());
    assertTrue(inferred.children(SyntaxRole.DECLARED_TYPE).isEmpty());
    assertTrue(inferred.value(SyntaxValue.LocalDeclaration.class).writtenType().isEmpty());
    assertEquals(JavaTypeCategory.INT, ((SyntaxType.Resolved) inferred.type()).identity().category());
    assertEquals(1, first.children(SyntaxRole.ANNOTATION).size());
    assertTrue(second.children(SyntaxRole.ANNOTATION).isEmpty());
    assertEquals(List.of(first.requiredChild(SyntaxRole.ANNOTATION).nodeId()),
        second.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().sharedAnnotationKeys());
    assertEquals(2, unnamed.size());
    assertNotEquals(unnamed.get(0).nodeId(), unnamed.get(1).nodeId());
    assertNotEquals(bindingKey(unnamed.get(0)), bindingKey(unnamed.get(1)));
    assertTrue(unnamed.stream().allMatch(node -> node.semanticReference().isEmpty()));
    assertNotEquals(bindingKey(shadowed.get(0)), bindingKey(shadowed.get(1)));
    for (var declaration : shadowed) {
      var key = bindingKey(declaration);
      assertEquals(1, accesses.stream().filter(node -> node.value(SyntaxValue.SourceSymbol.class).name().equals("shared"))
          .filter(node -> node.semanticReference().orElseThrow().symbolKey().orElseThrow().equals(key)).count());
    }
  }

  @Test
  void givenExpandedBindingForms_whenRequestedThroughBoundedProfile_thenBoundedQualificationRemainsIncomplete() throws IOException {
    // Arrange
    var source = "class Example { int subject() { final int named = 1; var inferred = named; int _ = 3; return inferred; } }";

    // Act
    var expanded = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);

    // Assert
    assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
    assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
    assertEquals(expanded.aggregate(), bounded.aggregate());
  }

  @Test
  void givenTypeUseDimensionsAndAnnotatedVar_whenClosed_thenEachDeclaratorKeepsItsOwnDimensionsAndPhysicalAnnotations() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use {}
        @Target(ElementType.LOCAL_VARIABLE) @interface Local {}
        class Example { void subject() {
          int @Use [] first = null, second @Use [] = null;
          @Local final var inferred = "text";
          int _ = 1, _ = 2;
        } }
        """;

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var declarations = locals(decoded);
    var first = named(declarations, "first");
    var second = named(declarations, "second");
    var inferred = named(declarations, "inferred");

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(result.aggregate(), decoded.aggregate());
    assertEquals(1, first.requiredChild(SyntaxRole.DECLARED_TYPE).descendantsAndSelf().stream()
        .filter(node -> node.sourceKind().name().equals("ARRAY_TYPE")).count());
    assertEquals(2, second.requiredChild(SyntaxRole.DECLARED_TYPE).descendantsAndSelf().stream()
        .filter(node -> node.sourceKind().name().equals("ARRAY_TYPE")).count());
    assertEquals(3, decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.semanticReference().orElseThrow().symbolKey().orElseThrow().equals("type:Use")
            || node.semanticReference().orElseThrow().symbolKey().orElseThrow().equals("type:Local")).count());
    assertEquals(List.of("final"), inferred.value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
    assertEquals(SyntaxValue.AnnotationLocation.DECLARATION, inferred.requiredChild(SyntaxRole.ANNOTATION)
        .value(SyntaxValue.Annotation.class).location());
    assertTrue(inferred.children(SyntaxRole.DECLARED_TYPE).isEmpty());
    assertTrue(declarations.stream().filter(node -> node.value(SyntaxValue.LocalDeclaration.class).name().equals("_"))
        .allMatch(node -> node.semanticReference().isEmpty()));
  }

  @Test
  void givenCopiedBindingIdentityAndOriginalReference_whenBindingIsChangedToAnotherOwner_thenIntrinsicAdmissionRejectsTheContradiction()
      throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { int value = 1; return value; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var node = locals(original).getFirst();
    var local = node.value(SyntaxValue.LocalDeclaration.class);
    var binding = local.bindingEvidence().orElseThrow();
    var foreign = new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(),
        Optional.of(new LocalBindingEvidence(Optional.of("type:Other#subject():local_variable:0:1"), binding.typeForm(), binding.context(),
            binding.initialized(), binding.annotations(), binding.sharedAnnotationKeys())));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(),
        node.sourceKind(), node.syntaxKind(), node.type(), foreign, node.children(), node.semanticReference(), node.origin().orElseThrow()));

    // Assert
    assertTrue(failure.getMessage().contains("exact binding identity"));
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @TestFactory
  Stream<DynamicTest> givenIllegalLocalDeclarations_whenCompiled_thenCompilerErrorsPreventCompletion() {
    return Stream.of("var value;", "var value = null;", "var first = 1, second = 2;", "int _;", "int _ = 1; int value = _;",
        "@Override int value = 1;", "public int value = 1;")
        .map(declaration -> DynamicTest.dynamicTest(declaration, () -> {
          // Arrange
          var source = "class Example { void subject() { " + declaration + " } }";

          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);

          // Assert
          assertNotEquals(AstCompletion.COMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.stage() == DiagnosticStage.FRONTEND
              && diagnostic.code().startsWith("compiler.err.")),
              result.diagnostics().toString());
        }));
  }

  @Test
  void givenCompleteUnnamedLocal_whenCfgIsRequested_thenTruthfulRefusalPreservesTheCompletedSource() throws IOException {
    // Arrange
    var result = produce("class Example { int subject() { int _ = 7; return 9; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var bytes = AstCodec.encode(result);

    // Act
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertArrayEquals(bytes, AstCodec.encode(result));
  }

  @Test
  void givenNamedFinalAndInferredLocals_whenCfgIsRequested_thenExistingOperationsStillProduceAnAvailableGraph() throws IOException {
    // Arrange
    var result = produce("class Example { int subject() { final int first = 3; var second = first + 2; return second; } }",
        AstProfile.JAVA_SOURCE_EXPANDED);
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();

    // Act
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(MethodCfgState.AVAILABLE, cfg.methods().getFirst().state());
    assertTrue(cfg.methods().getFirst().graph().isPresent());
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

  private static List<SyntaxNode> locals(AstResult result) {
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    return result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
  }

  private static SyntaxNode named(List<SyntaxNode> nodes, String name) {
    return nodes.stream().filter(node -> node.value(SyntaxValue.LocalDeclaration.class).name().equals(name)).findFirst().orElseThrow();
  }

  private static String bindingKey(SyntaxNode node) {
    return node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
  }
}
