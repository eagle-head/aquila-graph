package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ExceptionAstTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenLegalExceptionForms_whenProducedAndDecoded_thenEveryWrittenClauseRemainsQueryable() {
    return Stream.of(
        new Form("try {} catch (RuntimeException failure) {}", List.of(SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE)),
        new Form("try {} finally {}", List.of(SyntaxRole.TRY_BLOCK, SyntaxRole.FINALLY_BLOCK)),
        new Form("try {} catch (RuntimeException failure) {} finally {}", List.of(SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK)),
        new Form("try (var first = open()) {}", List.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK)),
        new Form("try (AutoCloseable first = open(); var second = first;) {} catch (Exception failure) {} finally {}", List.of(SyntaxRole.RESOURCE, SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK)),
        new Form("try (existing; this.field; System.in) {}", List.of(SyntaxRole.RESOURCE, SyntaxRole.RESOURCE, SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK)),
        new Form("try (final AutoCloseable _ = open()) {}", List.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK)),
        new Form("try {} catch (IllegalArgumentException | IllegalStateException _) {}", List.of(SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE)))
        .map(form -> DynamicTest.dynamicTest(form.body(), () -> {
          // Arrange
          var source = source(form.body());
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          var statement = subjectTry(result);
          assertEquals(form.roles(), statement.children().stream().map(child -> child.role()).toList());
          assertEquals(form.body(), written(source, statement));
          assertEquals(original.aggregate(), result.aggregate());
          assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
          assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
          assertTrue(statement.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
              .allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isPresent()));
        }));
  }

  @Test
  void givenOrderedResourcesAndMultiCatch_whenProduced_thenBindingAccessesAndEveryUnionAlternativeArePreserved() throws IOException {
    // Arrange
    var source = source("try (final AutoCloseable first = open(); var second = first) { first.toString(); } catch (IllegalArgumentException | IllegalStateException failure) { failure.toString(); } finally { existing.toString(); }");
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var statement = subjectTry(result);
    var resources = statement.children(SyntaxRole.RESOURCE);
    assertEquals(List.of("first", "second"), resources.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
    assertEquals(List.of(LocalBindingEvidence.TypeForm.DECLARED, LocalBindingEvidence.TypeForm.INFERRED), resources.stream()
        .map(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm()).toList());
    assertEquals(List.of("final"), resources.getFirst().value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
    var firstKey = resources.getFirst().semanticReference().orElseThrow().symbolKey().orElseThrow();
    assertEquals(firstKey, resources.getLast().requiredChild(SyntaxRole.INITIALIZER).semanticReference().orElseThrow().symbolKey().orElseThrow());
    var parameter = statement.children(SyntaxRole.CATCH_CLAUSE).getFirst().requiredChild(SyntaxRole.PARAMETER);
    var union = parameter.requiredChild(SyntaxRole.DECLARED_TYPE);
    assertEquals(List.of("IllegalArgumentException", "IllegalStateException"), union.children(SyntaxRole.UNION_ALTERNATIVE).stream().map(node -> written(source, node)).toList());
    assertEquals(LocalBindingEvidence.Context.CATCH, parameter.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().context());
    assertTrue(statement.children(SyntaxRole.RESOURCE).stream().allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class)
        .bindingEvidence().orElseThrow().context() == LocalBindingEvidence.Context.RESOURCE));
    assertEquals(2, statement.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS
        && node.semanticReference().flatMap(reference -> reference.symbolKey()).filter(firstKey::equals).isPresent()).count());
    assertFalse(statement.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .anyMatch(node -> node.value(SyntaxValue.Invocation.class).name().equals("close") || node.value(SyntaxValue.Invocation.class).name().equals("addSuppressed")));
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidResourcesOrCatchAlternatives_whenProduced_thenCompilerErrorsPreventCompletion() {
    return Stream.of("try (int number = 1) {}", "try (existing) { existing = open(); }",
        "try {} catch (RuntimeException | IllegalArgumentException failure) {}",
        "try {} catch (String failure) {}")
        .map(body -> DynamicTest.dynamicTest(body, () -> {
          // Arrange
          var source = source(body);
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()
              && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
        }));
  }

  private static String source(String body) {
    return "class Example { final AutoCloseable field; Example() { field = null; } static AutoCloseable open() { return null; } void subject() throws Exception { AutoCloseable existing = open(); " + body + " } }";
  }

  @Test
  void givenAnnotatedResourcesAndCatchAlternatives_whenProducedAndDecoded_thenAnnotationsAndNestedBodiesKeepCanonicalOwners() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.LOCAL_VARIABLE) @interface ResourceMark {}
        @Target(ElementType.PARAMETER) @interface CatchMark {}
        @Target(ElementType.TYPE_USE) @interface TypeMark {}
        class Example {
          void subject() throws Exception {
            try (@ResourceMark final @TypeMark AutoCloseable first = null; @ResourceMark var _ = first) {
              try {} finally { int[] numbers = new int[] {1, 2}; Runnable action = () -> { assert numbers.length > 0; }; action.run(); }
            } catch (@CatchMark final @TypeMark IllegalArgumentException | @TypeMark IllegalStateException _) {}
            finally {}
          }
        }
        """;
    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var result = AstCodec.decode(AstCodec.encode(original), original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var statement = subjectTry(result);
    assertEquals(2, statement.children(SyntaxRole.RESOURCE).size());
    var annotations = statement.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).toList();
    assertEquals(2, annotations.stream().filter(node -> node.semanticReference().flatMap(reference -> reference.symbolKey()).filter("type:ResourceMark"::equals).isPresent()).count());
    assertEquals(1, annotations.stream().filter(node -> node.semanticReference().flatMap(reference -> reference.symbolKey()).filter("type:CatchMark"::equals).isPresent()).count());
    assertEquals(3, annotations.stream().filter(node -> node.semanticReference().flatMap(reference -> reference.symbolKey()).filter("type:TypeMark"::equals).isPresent()).count());
    var catchParameter = statement.children(SyntaxRole.CATCH_CLAUSE).getFirst().requiredChild(SyntaxRole.PARAMETER);
    assertEquals("_", catchParameter.value(SyntaxValue.LocalDeclaration.class).name());
    assertTrue(catchParameter.semanticReference().isEmpty());
    assertEquals(2, catchParameter.requiredChild(SyntaxRole.DECLARED_TYPE).children(SyntaxRole.UNION_ALTERNATIVE).size());
    assertEquals(2, statement.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.TRY).count());
    assertEquals(1, statement.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).count());
    assertEquals(original.aggregate(), result.aggregate());
    assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
  }

  @TestFactory
  Stream<DynamicTest> givenExceptionProfiles_whenApplicationRequestsCfg_thenSourceQualificationAndGraphPolicyRemainIndependent() {
    return Stream.of(new ProfileCase("try {} catch (RuntimeException failure) {}", true),
        new ProfileCase("try {} finally {}", false), new ProfileCase("try (var resource = open()) {}", false),
        new ProfileCase("try {} catch (IllegalArgumentException | IllegalStateException failure) {}", false))
        .map(variant -> DynamicTest.dynamicTest(variant.body(), () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source(variant.body()))));
          var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
          var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
          // Act
          var astOnly = runner.analyze(new AnalysisComputationRequest(
              new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_EXPANDED), Optional.empty()));
          var withCfg = runner.analyze(new AnalysisComputationRequest(
              new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_EXPANDED), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
          var bounded = produce(source(variant.body()), AstProfile.JAVA_SOURCE_BOUNDED);
          // Assert
          var original = astOnly.representation().ast().result().orElseThrow();
          var selected = withCfg.representation().ast().result().orElseThrow();
          assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
          assertEquals(AstCompletion.COMPLETE, selected.completion());
          assertEquals(original.identity(), selected.identity());
          assertEquals(original.aggregate(), selected.aggregate());
          assertEquals(variant.bounded() ? AstCompletion.COMPLETE : AstCompletion.INCOMPLETE, bounded.completion());
          assertEquals(variant.body().contains("finally") || variant.body().contains("try (") ? AnalysisCompletion.PARTIAL : AnalysisCompletion.COMPLETE,
              withCfg.completion());
        }));
  }

  private record ProfileCase(String body, boolean bounded) {}

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
  }

  private static SyntaxNode subjectTry(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.sourceKind().name().equals("TRY")).findFirst().orElseThrow();
  }

  private static String written(String source, SyntaxNode node) {
    var origin = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
  }

  private record Form(String body, List<SyntaxRole> roles) {}
}
