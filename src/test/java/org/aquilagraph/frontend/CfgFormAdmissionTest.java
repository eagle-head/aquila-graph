package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedExecutableForms;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class CfgFormAdmissionTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenQualifiedCopiedExcludedForms_whenDerivingClosedValues_thenRefusedWithoutChangingSourceOrUnaffectedGraph() {
    var declarations = List.of(
        "void subject(AutoCloseable input) throws Exception { try (input) {} }",
        "void subject() { try {} finally { int retained = 7; } }",
        "int subject(int input) { return switch (input) { case 1: yield 2; default: yield 3; }; }");
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .flatMap(profile -> declarations.stream().map(declaration -> DynamicTest.dynamicTest(profile.version() + ": " + declaration,
            () -> assertExcludedForm(profile, declaration))));
  }

  private void assertExcludedForm(AstProfile sourceProfile, String declaration) throws IOException {
    // Arrange: copied typed variants retain the real compiler's independent expectations and diagnostics.
    var text = "class Example { " + declaration + " int unaffected() { return 7; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), text)));
    var original = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var originalResult = AstResultAdmission.admit(original, sourceProfile, "javac-public/1");
    var aggregate = AstValueAdmission.admit(CopiedExecutableForms.capture(original.aggregate().orElseThrow()));
    var supplied = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(aggregate),
        JavaCoverageTestSupport.diagnostics(original, "javac-public/1"));
    var admitted = AstResultAdmission.admit(supplied, sourceProfile, "javac-public/1");
    var ast = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());
    var subject = ast.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow().methodKey();
    var unaffected = ast.program().methods().stream().filter(method -> method.name().equals("unaffected")).findFirst().orElseThrow().methodKey();
    var before = AstCodec.encode(ast);
    var variants = ast.aggregate().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.TRY || node.syntaxKind() == SyntaxKind.CASE).toList();

    // Act and Assert
    assertEquals(AstCompletion.INCOMPLETE, originalResult.completion());
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertFalse(originalResult.diagnostics().isEmpty());
    assertEquals(admitted.identity(), ast.identity());
    assertEquals(admitted.aggregate(), ast.aggregate());
    assertEquals(admitted.diagnostics(), ast.diagnostics());
    assertTrue(ast.diagnostics().containsAll(originalResult.diagnostics()));
    assertEquals(original.languageFeatureCoverageResult().validationEvidence().writtenInventory(),
        supplied.languageFeatureCoverageResult().validationEvidence().writtenInventory());
    assertEquals(original.languageFeatureCoverageResult().validationEvidence().attributedExpectations(),
        supplied.languageFeatureCoverageResult().validationEvidence().attributedExpectations());
    assertFalse(variants.isEmpty());
    for (var graphProfile : List.of(CfgProfile.JAVA_BOUNDED, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS)) {
      var result = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), List.of(subject, unaffected), graphProfile));
      var refused = result.methods().getFirst();
      assertEquals(MethodCfgState.UNSUPPORTED, refused.state(), declaration);
      assertTrue(refused.graph().isEmpty());
      var admission = CfgAdmission.assess(ast, subject, graphProfile);
      assertEquals(MethodCfgState.UNSUPPORTED, admission.state());
      assertTrue(admission.plan().isEmpty());
      assertEquals(admission.blockers(), refused.blockers());
      for (var variant : variants) {
        var occurrence = new SourceOccurrence(ast.identity(), variant.nodeId());
        assertTrue(admission.blockers().stream().anyMatch(blocker -> blocker.kind() == MethodCfgState.UNSUPPORTED
            && blocker.slot().equals("construct") && blocker.occurrence().equals(Optional.of(occurrence))));
      }
      assertEquals(CfgReadiness.UNSUPPORTED, CfgAdmission.classify(ast.body(subject).orElseThrow()));
      var available = result.methods().getLast();
      assertEquals(MethodCfgState.AVAILABLE, available.state());
      assertFalse(available.graph().orElseThrow().operationNodes().isEmpty());
      assertTrue(available.graph().orElseThrow().values().stream().anyMatch(value -> value.literal().equals(Optional.of("7"))));
      assertEquals(ast.bodyRef(unaffected), available.body());
      assertEquals(ast.identity(), result.request().astIdentity());
      assertEquals(AstCompletion.INCOMPLETE, ast.completion());
      assertEquals(admitted.diagnostics(), ast.diagnostics());
      assertArrayEquals(before, AstCodec.encode(ast));
    }
  }
}
