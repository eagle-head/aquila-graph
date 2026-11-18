package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SourceQualificationReason.Category;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;

class CanonicalAstEmissionTest {
  private static final CapturedLocation ROOT = CapturedLocation.parse(Syntax.POSIX, "/captured");
  private static final AstProfile PROFILE = AstProfile.JAVA_SOURCE_FIDELITY;
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String SOURCE = "final class Sample { public static int value(int x) { return x + 1 + 1; } }";

  @Test
  void givenCopiedCompilerEvidenceWithRepeatedDiagnostics_whenAdmittedAndRetained_thenFullContentAndOccurrencesSurvive()
      throws IOException, NoSuchAlgorithmException {
    // Arrange
    var source = SourceFile.fromContent(CapturedLocation.parse(Syntax.POSIX, "Sample.java"), SOURCE);
    var snapshot = InputSnapshot.fromSources(ROOT, List.of(source));
    var produced = JavaFrontends.standard().produce(new AstProductionRequest(snapshot, CONFIGURATION, PROFILE)).result().orElseThrow();
    var environment = new AnalysisEnvironmentSnapshot(CONFIGURATION, "javac", "Captured Vendor", "25",
        List.of("--release", "25", "-encoding", "UTF-8", "-proc:none"), List.of(), List.of());
    var error = diagnostic("error", DiagnosticSeverity.ERROR);
    var warning = diagnostic("warning", DiagnosticSeverity.WARNING);
    var diagnostics = List.of(error, warning, error);
    var input = copiedInput(produced, environment, diagnostics);
    var reordered = copiedInput(produced, environment, List.of(warning, error, error));
    var revision = produced.identity().semanticProductionRevision();

    // Act
    var outcome = AstProductionAdmission.outcome(input, PROFILE, revision);
    var reorderedOutcome = AstProductionAdmission.outcome(reordered, PROFILE, revision);
    var result = outcome.result().orElseThrow();
    var retained = AstCodec.encode(result);
    var decoded = AstCodec.decode(retained, result.identity());
    var literals = decoded.aggregate().roots().getFirst().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, produced.completion());
    assertTrue(produced.diagnostics().isEmpty());
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, reorderedOutcome.completion());
    assertEquals(List.of(Category.SOURCE_ERROR, Category.SOURCE_ERROR),
        outcome.qualification().reasons().stream().map(reason -> reason.category()).toList());
    assertEquals(List.of(0, 2),
        outcome.qualification().reasons().stream().map(reason -> reason.diagnostics().getFirst().position()).toList());
    assertEquals(diagnostics, outcome.diagnostics());
    assertEquals(diagnostics, decoded.diagnostics());
    assertEquals(outcome.qualification(), decoded.qualification());
    assertEquals(input.languageFeatureCoverageResult(), decoded.coverage());
    assertEquals(input.languageFeatureCoverageResult().observations(), decoded.coverage().observations());
    assertEquals(List.of("1", "1"), literals.stream().map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
    assertEquals(List.of(65L, 69L), literals.stream().map(node -> node.origin().orElseThrow().startOffset()).toList());
    assertNotEquals(literals.getFirst().nodeId(), literals.getLast().nodeId());
    for (var literal : literals) {
      assertSame(literal, decoded.aggregate().node(literal.nodeId()).orElseThrow());
    }
    assertEquals("java-source/24", result.identity().sourceModelVersion());
    assertEquals("java-source-expanded/7", result.identity().astProfileVersion());
    assertEquals("javac-public/15", revision);
    assertEquals("ast-content:fc7f3f12440fc47683f0eb13f0002640d9511d69b76c73c65b45c84ee85a70d8", outcome.sourceSubjectDigest());
    assertNotEquals(outcome.sourceSubjectDigest(), reorderedOutcome.sourceSubjectDigest());
    assertEquals("c0b8ed4b49e6034d6bff533f6bede8fe9e2c333354331171389bf5542e0e2b8b",
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(retained)));
    assertArrayEquals(retained, AstCodec.encode(decoded));
  }

  @Test
  void givenEmptyCapturedSource_whenProduced_thenUnavailableSubjectRetainsItsExactContentDigest() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(ROOT, List.of());
    var request = new AstProductionRequest(snapshot, CONFIGURATION, PROFILE);

    // Act
    var outcome = JavaFrontends.standard().produce(request);

    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
    assertTrue(outcome.result().isEmpty());
    assertTrue(outcome.capturedEnvironment().isEmpty());
    assertEquals(Optional.of(snapshot), outcome.capturedInput());
    assertEquals(Optional.of(CONFIGURATION), outcome.requestedConfiguration());
    assertEquals(List.of(Category.EMPTY_SOURCE), outcome.qualification().reasons().stream().map(reason -> reason.category()).toList());
    assertEquals(snapshot, outcome.coverageQualification().orElseThrow().validationEvidence().writtenInventory().snapshot());
    assertEquals("source-subject:24c92d7e35c68ed1ea9bdeaeb5c4c9c02f3383c183101c335cab14e3963deab9", outcome.sourceSubjectDigest());
  }

  @Test
  void givenOppositeSetEncounterOrders_whenEncoded_thenUnsignedFramedMemberOrderDeterminesAllBytes() throws IOException {
    // Arrange
    var first = new LinkedHashSet<>(List.of("ab", "aa", "\u0080", "\u007f"));
    var second = new LinkedHashSet<>(List.of("\u007f", "\u0080", "aa", "ab"));
    var format = "source-value/1";
    var buffer = new ByteArrayOutputStream();
    try (var oracle = new DataOutputStream(buffer)) {
      text(oracle, format);
      text(oracle, "set");
      oracle.writeInt(4);
      for (var value : List.of("\u007f", "\u0080", "aa", "ab")) {
        text(oracle, "text");
        text(oracle, value);
      }
    }

    // Act
    var encoded = new ArrayList<byte[]>();
    for (var canonical : List.of(false, true)) {
      for (var value : List.of(first, second)) {
        var writer = new CanonicalValueWriter(format);
        new RetainedValueCodec(canonical).write(writer, value);
        encoded.add(writer.toByteArray());
      }
    }

    // Assert
    var expected = buffer.toByteArray();
    encoded.forEach(bytes -> assertArrayEquals(expected, bytes));
  }

  private static void text(DataOutputStream oracle, String value) throws IOException {
    oracle.writeInt(value.length());
    oracle.writeChars(value);
  }

  private static AnalysisDiagnostic diagnostic(String key, DiagnosticSeverity severity) {
    return new AnalysisDiagnostic("provided:" + key, "provided.observation", DiagnosticStage.FRONTEND, severity,
        "Supplied source " + key, List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }

  private static SyntaxAnalysisInput copiedInput(AstResult original, AnalysisEnvironmentSnapshot environment,
      List<AnalysisDiagnostic> diagnostics) {
    var evidence = original.coverage().validationEvidence();
    var emissions = new ArrayList<DiagnosticEmission>();
    for (var diagnostic : diagnostics) {
      var slot = emissions.size();
      emissions.add(new DiagnosticEmission(new DiagnosticEmission.Address("source-observation", "sample", slot), diagnostic,
          diagnostic.outcomeKind(), List.of(), slot));
    }
    var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), emissions);
    return new SyntaxAnalysisInput(original.snapshot(), environment, original.program(), Optional.of(original.aggregate()), diagnostics,
        coverage);
  }
}
