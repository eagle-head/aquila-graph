package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.IdentityHashMap;
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
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;

class IterationAstTest {
  @TempDir Path directory;

  @Test
  void givenExpandedIterationRequest_whenApplicationAnalyzesWithAndWithoutCfg_thenTheSameCompleteSourceSurvivesRefusal() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { void subject(int n) { while (n > 0) n--; } }")));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    // Act
    var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var selected = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
    assertTrue(astOnly.representation().cfg().isEmpty());
    assertEquals(AnalysisCompletion.PARTIAL, selected.completion());
    assertEquals(MethodCfgState.UNSUPPORTED, selected.representation().cfg().orElseThrow().methods().stream().filter(method -> selected.representation().ast().result().orElseThrow().method(method.methodKey()).orElseThrow().name().equals("subject")).findFirst().orElseThrow().state());
    var original = astOnly.representation().ast().result().orElseThrow();
    var refused = selected.representation().ast().result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, refused.completion());
    assertEquals(original.identity(), refused.identity());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(refused));
  }

  @Test
  void givenLoopFamilies_whenProducedAndDecoded_thenWrittenRolesAndLabelsSurviveClosure() throws IOException {
    // Arrange
    var source = """
        class Example {
          void subject(int n, int[] values) {
            outer: while (n > 0) {
              n--;
              if (n == 2) continue outer;
              if (n == 1) break outer;
              do { --n; if (n == 0) break; } while (n > 0);
            }
            for (int i = 0, j = 1; i < n; i++, j += 2) { if (i == j) continue; }
            for (;;) break;
            for (int value : values) ;
            exit: { if (n == 0) break exit; }
          }
        }
        """;
    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var result = AstCodec.decode(AstCodec.encode(original), original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.aggregate(), result.aggregate());
    assertEquals(original.coverage(), result.coverage());
    assertEquals(AstCodec.materialize(original), AstCodec.materialize(result));
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(result));
    var nodes = body(result);
    var loop = first(nodes, SyntaxKind.WHILE_LOOP);
    assertEquals(List.of(SyntaxRole.CONDITION, SyntaxRole.BODY), roles(loop));
    assertEquals(List.of(SyntaxRole.BODY, SyntaxRole.CONDITION), roles(first(nodes, SyntaxKind.DO_WHILE_LOOP)));
    var basic = first(nodes, SyntaxKind.FOR_LOOP);
    assertEquals(List.of(SyntaxRole.INITIALIZER, SyntaxRole.INITIALIZER, SyntaxRole.CONDITION, SyntaxRole.UPDATE, SyntaxRole.UPDATE, SyntaxRole.BODY), roles(basic));
    assertEquals(List.of(0, 1), basic.children().stream().filter(child -> child.role() == SyntaxRole.INITIALIZER).map(SyntaxChild::position).toList());
    assertEquals(List.of("i", "j"), basic.children(SyntaxRole.INITIALIZER).stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
    assertEquals(List.of(SyntaxKind.UPDATE, SyntaxKind.COMPOUND_ASSIGNMENT), basic.children(SyntaxRole.UPDATE).stream().map(node -> node.requiredChild(SyntaxRole.EXPRESSION).syntaxKind()).toList());
    var omitted = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.FOR_LOOP).skip(1).findFirst().orElseThrow();
    assertEquals(List.of(SyntaxRole.BODY), roles(omitted));
    var enhanced = first(nodes, SyntaxKind.ENHANCED_FOR_LOOP);
    assertEquals(List.of(SyntaxRole.PARAMETER, SyntaxRole.ITERABLE, SyntaxRole.BODY), roles(enhanced));
    assertEquals(SyntaxKind.EMPTY, enhanced.requiredChild(SyntaxRole.BODY).syntaxKind());
    assertEquals(List.of(Optional.of("outer"), Optional.of("exit")), nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.LABELED_STATEMENT).map(node -> node.value(SyntaxValue.StatementLabel.class).name()).toList());
    assertEquals(List.of(Optional.of("outer"), Optional.empty()), nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.CONTINUE).map(node -> node.value(SyntaxValue.StatementLabel.class).name()).toList());
    assertTrue(nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.BREAK || node.syntaxKind() == SyntaxKind.CONTINUE).allMatch(node -> node.children().isEmpty() && node.semanticReference().isEmpty() && node.origin().isPresent()));
    assertTrue(nodes.stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertEquals(AstCompletion.COMPLETE, result.completion());
  }

  @Test
  void givenEnhancedBindings_whenProduced_thenInferenceAnnotationsAndUnnamedValuesAreRetained() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target({ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Mark {}
        class Example {
          void subject(String[] array, Iterable<String> iterable) {
            for (@Mark final String item : array) { String copy = item; }
            for (var item : iterable) { String copy = item; }
            for (var _ : array) ;
            for (final String _ : iterable) ;
          }
        }
        """;
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var bindings = body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.ENHANCED_FOR_LOOP).map(node -> node.requiredChild(SyntaxRole.PARAMETER)).toList();
    assertEquals(List.of("item", "item", "_", "_"), bindings.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
    assertEquals(List.of(LocalBindingEvidence.TypeForm.DECLARED, LocalBindingEvidence.TypeForm.INFERRED, LocalBindingEvidence.TypeForm.INFERRED, LocalBindingEvidence.TypeForm.DECLARED), bindings.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm()).toList());
    assertTrue(bindings.stream().allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().filter(binding -> binding.context() == LocalBindingEvidence.Context.ENHANCED_FOR && !binding.initialized() && binding.declarationKey().isPresent()).isPresent()));
    assertEquals(List.of("final"), bindings.getFirst().value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
    assertEquals(1, bindings.getFirst().children(SyntaxRole.ANNOTATION).size());
    assertTrue(bindings.get(1).children(SyntaxRole.DECLARED_TYPE).isEmpty());
    assertTrue(bindings.get(2).semanticReference().isEmpty());
    assertTrue(bindings.get(3).semanticReference().isEmpty());
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidTransfers_whenProduced_thenCompilerErrorsPreventCompletion() {
    return Stream.of("label: { continue label; }", "break absent;", "continue absent;", "break;", "continue;")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var source = "class Example { void subject() { " + statement + " } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
        }));
  }

  @Test
  void givenLoops_whenBoundedProfileProduced_thenExistingProfileRemainsIncomplete() throws IOException {
    // Arrange
    var source = "class Example { void subject(int n) { while (n > 0) n--; } }";
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(SyntaxKind.WHILE_LOOP, first(body(result), SyntaxKind.WHILE_LOOP).syntaxKind());
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
  }

  @TestFactory
  Stream<DynamicTest> givenOptionalHeadersAndBodyVariants_whenProduced_thenEachWrittenClauseHasItsOwnRole() {
    return Stream.of("for (; n > 0;) n--;", "for (n = 2;;) break;", "for (;;n--) break;", "for (n = 2; n > 0;) n--;",
        "for (;n > 0;n--) ;", "for (n = 2;;n--) break;", "for (n = 1, m = 2; n < m; n++, m--) ;", "while (flag) ;", "do ; while (flag);")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var source = "class Example { void subject(int n, int m, Boolean flag) { " + statement + " } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          var loop = body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.FOR_LOOP || node.syntaxKind() == SyntaxKind.WHILE_LOOP || node.syntaxKind() == SyntaxKind.DO_WHILE_LOOP).findFirst().orElseThrow();
          if (statement.startsWith("for")) {
            var clauses = statement.substring(statement.indexOf('(') + 1, statement.indexOf(')')).split(";", -1);
            assertEquals(!clauses[1].isBlank(), loop.optionalChild(SyntaxRole.CONDITION).isPresent());
            assertEquals(clauses[0].isBlank(), loop.children(SyntaxRole.INITIALIZER).isEmpty());
            assertEquals(clauses[2].isBlank(), loop.children(SyntaxRole.UPDATE).isEmpty());
            assertTrue(loop.children(SyntaxRole.INITIALIZER).stream().allMatch(node -> node.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT));
          }
          assertTrue(loop.origin().isPresent());
          assertTrue(loop.requiredChild(SyntaxRole.BODY).origin().isPresent());
        }));
  }

  @Test
  void givenMalformedLoopShape_whenCopied_thenIntrinsicAdmissionRejectsMissingOrReorderedClauses() throws IOException {
    // Arrange
    var result = produce("class Example { void subject(int n) { while (n > 0) n--; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var loop = first(body(result), SyntaxKind.WHILE_LOOP);
    var missing = loop.children().stream().filter(child -> child.role() != SyntaxRole.CONDITION).toList();
    var reordered = loop.children().reversed();
    // Act
    var missingFailure = assertThrows(IllegalArgumentException.class, () -> copy(loop, loop.type(), missing));
    var orderFailure = assertThrows(IllegalArgumentException.class, () -> copy(loop, loop.type(), reordered));
    // Assert
    assertTrue(missingFailure.getMessage().contains("cardinality"));
    assertTrue(orderFailure.getMessage().contains("clause order"));
  }

  @Test
  void givenMissingRequiredIterableType_whenAdmitted_thenSourceIsIncompleteAndRetentionCannotAcquireStorage() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { void subject(int[] values) { for (var value : values) ; } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var captured = CopiedAstValues.capture(original.aggregate().orElseThrow());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        missingIterableType(source.root(), replacements), source.nodes().stream().map(node -> missingIterableType(node, replacements)).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), missingIterableType(body.root(), replacements),
        body.origin(), List.of(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes()));
    var input = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied), JavaCoverageTestSupport.diagnostics(original, "copied-iterable-type-qualification"));
    // Act
    var outcome = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var result = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var publication = new AstRetentionService(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(outcome);
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(SyntaxType.Unresolved.INSTANCE, first(body(decoded), SyntaxKind.ENHANCED_FOR_LOOP).requiredChild(SyntaxRole.ITERABLE).type());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
  }

  private static SyntaxNode missingIterableType(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements) {
    var previous = replacements.get(node);
    if (previous != null) {
      return previous;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), missingIterableType(child.node(), replacements))).toList();
    var type = node.structuralPath().steps().getLast().role() == SyntaxRole.ITERABLE ? SyntaxType.Unresolved.INSTANCE : node.type();
    var copied = node.origin().isPresent() ? copy(node, type, children)
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference());
    replacements.put(node, copied);
    return copied;
  }

  private static SyntaxNode copy(SyntaxNode node, SyntaxType type, List<SyntaxChild> children) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference(), node.origin().orElseThrow());
  }

  private static List<SyntaxNode> body(AstResult result) {
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    return result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
  }

  private static SyntaxNode first(List<SyntaxNode> nodes, SyntaxKind kind) {
    return nodes.stream().filter(node -> node.syntaxKind() == kind).findFirst().orElseThrow();
  }

  private static List<SyntaxRole> roles(SyntaxNode node) {
    return node.children().stream().map(SyntaxChild::role).toList();
  }
}
