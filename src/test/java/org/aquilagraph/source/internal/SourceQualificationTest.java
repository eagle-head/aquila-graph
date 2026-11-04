package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.SourceQualificationReason.Category;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.aquilagraph.source.api.SourceQualificationReason.Node;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceQualificationTest {
  @TempDir Path directory;

  @Test
  void givenBoundedMultiCatchWithoutDiagnostics_whenAnalyzingAndReadingAfterCompilerClosure_thenTheAdmittedProfileCauseExplainsPartialSource()
      throws IOException {
    // Arrange
    var snapshot = snapshot("class Subject { void run() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }");
    var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), (ast, request) -> {
      throw new AssertionError("Source-only qualification must not acquire CFG");
    });
    var request = new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, configuration(), AstProfile.JAVA_SOURCE_BOUNDED), Optional.empty());

    // Act
    var run = runner.analyze(request);
    var outcome = run.representation().ast();
    var ast = outcome.result().orElseThrow();
    var qualification = ast.qualification();
    var reason = qualification.reasons().getFirst();
    var node = ast.aggregate().node(assertInstanceOf(Node.class, reason.subject()).nodeKey()).orElseThrow();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertSame(qualification, outcome.qualification());
    assertEquals(Optional.of(ast.identity().semanticProductionRevision()), qualification.semanticRevision());
    assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, qualification.profile());
    assertEquals(1, qualification.reasons().size());
    assertEquals(Category.PROFILE_RESTRICTION, reason.category());
    assertEquals(EvidenceRole.PROFILE_FORM, reason.role());
    assertEquals(JavaSourceKind.UNION_TYPE, node.sourceKind());
    assertEquals(node.origin().stream().toList(), reason.origins());
    assertFalse(reason.origins().isEmpty());
    assertTrue(reason.diagnostics().isEmpty());
    assertTrue(ast.diagnostics().isEmpty());
    assertTrue(run.diagnostics().values().isEmpty());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertSame(outcome, assertInstanceOf(RequestQualificationReason.Source.class, run.qualification().reasons().getFirst()).outcome());
    assertThrows(UnsupportedOperationException.class, () -> qualification.reasons().clear());
  }

  @Test
  void givenTheSameMultiCatchUnderTheExpandedProfile_whenProducing_thenOptionalBoundedRestrictionsDoNotBecomeRequired() throws IOException {
    // Arrange
    var snapshot = snapshot("class Subject { void run() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }");

    // Act
    var outcome = produce(snapshot, AstProfile.JAVA_SOURCE_EXPANDED);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.qualification().reasons().isEmpty());
    assertTrue(outcome.diagnostics().isEmpty());
  }

  @Test
  void givenEmptyCaptureAndAnEmptyWrittenFile_whenProducing_thenSemanticAbsenceAndHistoricalEnvelopeRemainDistinct() throws IOException {
    // Arrange
    var empty = InputSnapshot.fromSources(directory, List.of());
    var file = snapshot("");

    // Act
    var capture = produce(empty, AstProfile.JAVA_SOURCE_BOUNDED);
    var bounded = produce(file, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = produce(file, AstProfile.JAVA_SOURCE_UNITS);

    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, capture.completion());
    assertTrue(capture.capturedEnvironment().isEmpty());
    assertTrue(capture.qualification().semanticRevision().isEmpty());
    assertEquals(List.of(Category.EMPTY_SOURCE), capture.qualification().reasons().stream().map(SourceQualificationReason::category).toList());
    assertTrue(capture.qualification().reasons().getFirst().origins().isEmpty());
    assertEquals(AstCompletion.UNAVAILABLE, bounded.completion());
    assertTrue(bounded.qualification().semanticRevision().isPresent());
    assertTrue(bounded.qualification().reasons().stream().anyMatch(reason -> reason.role() == EvidenceRole.DECLARATION_ENVELOPE));
    assertEquals(AstCompletion.COMPLETE, expanded.completion());
    assertTrue(expanded.qualification().reasons().isEmpty());
  }

  @Test
  void givenNestedProfileRestrictionsAcrossCapturedFiles_whenProducing_thenAllDescendantCausesRetainCanonicalSourceOrder() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(
        SourceFile.fromContent(Path.of("First.java"), "class First { void run(Object lock, boolean flag) { synchronized (lock) { assert flag; } } }"),
        SourceFile.fromContent(Path.of("Second.java"), "class Second { void run(boolean flag) { assert flag; } }")));

    // Act
    var ast = produce(snapshot, AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var reasons = ast.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.PROFILE_FORM).toList();
    var nodes = ast.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.SYNCHRONIZED
        || node.sourceKind() == JavaSourceKind.ASSERT).toList();

    // Assert
    assertEquals(3, reasons.size());
    assertEquals(nodes.stream().map(node -> new Node(node.nodeId(), node.sourceFileKey())).toList(),
        reasons.stream().map(SourceQualificationReason::subject).toList());
    assertEquals(nodes.stream().map(node -> node.origin().stream().toList()).toList(), reasons.stream().map(SourceQualificationReason::origins).toList());
    assertTrue(reasons.stream().allMatch(reason -> reason.category() == Category.PROFILE_RESTRICTION));
  }

  @Test
  void givenIndependentRestrictionsAtOneLocalDeclaration_whenProducingBoundedSource_thenBothTypedCausesRemainAtTheSameSubject() throws IOException {
    // Arrange
    var snapshot = snapshot("class Subject { int run() { final var value = 1; return value; } }");

    // Act
    var ast = produce(snapshot, AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var reasons = ast.qualification().reasons().stream()
        .filter(reason -> reason.role() == EvidenceRole.LOCAL_MODIFIERS || reason.role() == EvidenceRole.LOCAL_TYPE_FORM).toList();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertEquals(List.of(EvidenceRole.LOCAL_MODIFIERS, EvidenceRole.LOCAL_TYPE_FORM), reasons.stream().map(SourceQualificationReason::role).toList());
    assertEquals(reasons.getFirst().subject(), reasons.getLast().subject());
    assertEquals(reasons.getFirst().origins(), reasons.getLast().origins());
    assertTrue(reasons.stream().allMatch(reason -> reason.category() == Category.PROFILE_RESTRICTION));
  }

  @Test
  void givenIncompleteCurrentAndHistoricalResults_whenDecodingAndQueryingWithoutCompilerOrCfg_thenReasonsBytesAndIdentityRemainEqual()
      throws IOException {
    // Arrange
    var ast = produce(snapshot("class Subject { int run() { return missing; } }"), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var results = retainedVariants(ast);

    for (var original : results) {
      var bytes = AstCodec.encode(original);
      // Act
      var decoded = AstCodec.decode(bytes, original.identity());
      var reasons = decoded.qualification().reasons();
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertFalse(reasons.isEmpty());
      assertTrue(reasons.stream().anyMatch(reason -> reason.category() == Category.COMPILER_ERROR));
      assertTrue(reasons.stream().anyMatch(reason -> reason.category() == Category.ERRONEOUS_EVIDENCE));
      assertEquals(original.qualification(), decoded.qualification());
      assertEquals(original.identity(), decoded.identity());
      assertEquals(original.diagnostics(), decoded.diagnostics());
      assertArrayEquals(bytes, AstCodec.encode(decoded));
      assertSame(decoded.qualification(), AstOutcome.available(decoded).qualification());
    }
  }

  @Test
  void givenCompleteHistoricalFormats_whenDecoded_thenOriginalProfilesAndSemanticRevisionsStillHaveNoBlockingReason() throws IOException {
    // Arrange
    var ast = produce(snapshot("class Subject { int run() { return 1; } }"), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var results = retainedVariants(ast);

    for (var original : results) {
      var bytes = AstCodec.encode(original);
      // Act
      var decoded = AstCodec.decode(bytes, original.identity());
      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertEquals(original.qualification(), decoded.qualification());
      assertEquals(original.identity(), decoded.identity());
      assertTrue(decoded.qualification().reasons().isEmpty());
      assertArrayEquals(bytes, AstCodec.encode(decoded));
    }
  }

  private static List<AstResult> retainedVariants(AstResult ast) {
    var variants = new ArrayList<>(List.of(ast, SourceAnalysisFixture.legacyResult(ast), SourceAnalysisFixture.previousResult(ast),
        SourceAnalysisFixture.updateResult(ast), SourceAnalysisFixture.arrayResult(ast), SourceAnalysisFixture.iterationResult(ast),
        SourceAnalysisFixture.assertionResult(ast), SourceAnalysisFixture.lambdaResult(ast), SourceAnalysisFixture.referenceResult(ast),
        SourceAnalysisFixture.patternResult(ast), SourceAnalysisFixture.switchResult(ast), SourceAnalysisFixture.exceptionResult(ast),
        SourceAnalysisFixture.classLiteralResult(ast)));
    var historical = SourceAnalysisFixture.historicalInput(SourceValueCapture.capture(ast));
    for (var version = 14; version <= 20; version++) {
      variants.add(AstResultAdmission.admit(historical, ast.profile(), ast.identity().semanticProductionRevision(), "java-source/" + version));
    }
    variants.add(AstResultAdmission.admit(SourceValueCapture.capture(ast), ast.profile(), ast.identity().semanticProductionRevision(),
        AstResult.ENVIRONMENT_IDENTITY_MODEL_VERSION));
    assertEquals(21, variants.stream().map(value -> value.identity().sourceModelVersion()).distinct().count());
    return List.copyOf(variants);
  }

  private InputSnapshot snapshot(String source) throws IOException {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Subject.java"), source)));
  }

  private static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }

  private static AstOutcome produce(InputSnapshot snapshot, AstProfile profile) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration(), profile);
  }
}
