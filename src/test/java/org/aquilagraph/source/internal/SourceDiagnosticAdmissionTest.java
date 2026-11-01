package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.retention.AstCodec;




import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CapturedValueEncoding;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SourceQualification;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.CoverageValidationEvidence;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class SourceDiagnosticAdmissionTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  private static final List<DiagnosticStage> FOREIGN_STAGES = List.of(DiagnosticStage.CONTROL_FLOW, DiagnosticStage.PERSISTENCE);
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  @TempDir Path directory;

  @Test
  void givenSuppliedOrdinaryForeignError_whenAdmittingSource_thenItsCreatingOwnerCannotChangeAstQualification() {
    verifyForeignAdmission(List.of(DiagnosticSeverity.ERROR));
  }

  @Test
  void givenSuppliedOrdinaryForeignNoteOrWarning_whenAdmittingSource_thenTheNativeStreamRejectsTheForeignOwner() {
    verifyForeignAdmission(List.of(DiagnosticSeverity.INFO, DiagnosticSeverity.WARNING));
  }

  @Test
  void givenSuppliedForeignDiagnostic_whenAdmittingUnavailableSource_thenAbsenceDoesNotBypassCreatingOwnerAdmission() {
    verifyProfiles(original -> assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () -> {
      for (var severity : DiagnosticSeverity.values()) {
        // Arrange
        var diagnostic = diagnostic(stage, severity, "unavailable");
        // Act
        var rejection = assertThrows(IllegalArgumentException.class, () -> new AdmittedAstOutcome(original.profile(), unavailableQualification(original), Optional.empty(),
            Optional.of(original.snapshot()), Optional.of(original.environment()), Optional.of(CONFIGURATION), Optional.empty(),
            List.of(diagnostic), "provided-source-subject"));
        // Assert
        assertTrue(rejection.getMessage().contains("Source diagnostics require"));
      }
    })));
  }

  @Test
  void givenSelfConsistentStoredSourceWithForeignError_whenDecoding_thenSourceAdmissionRejectsItBeforeIdentityQualification() {
    verifyForeignCodec(List.of(DiagnosticSeverity.ERROR));
  }

  @Test
  void givenSelfConsistentStoredSourceWithForeignNoteOrWarning_whenDecoding_thenSourceAdmissionRejectsTheForeignOwner() {
    verifyForeignCodec(List.of(DiagnosticSeverity.INFO, DiagnosticSeverity.WARNING));
  }

  @Test
  void givenOwnedFrontendNotesAndWarnings_whenAdmittingAndClosingCodec_thenCompleteSourcePreservesRepeatedOrderedEvents() {
    verifyProfiles(original -> {
      var note = diagnostic(DiagnosticStage.FRONTEND, DiagnosticSeverity.INFO, "note");
      var warning = diagnostic(DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "warning");
      var expected = List.of(note, warning, note);
      var retained = admitted(original, expected);
      assertEquals(AstCompletion.COMPLETE, retained.completion());
      assertRetained(original, retained, expected);
    });
  }

  @Test
  void givenOwnedFrontendError_whenAdmittingAndClosingCodec_thenIncompleteSourceKeepsItsActualErrorAndFacts() {
    verifyProfiles(original -> {
      var error = diagnostic(DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, "error");
      var retained = admitted(original, List.of(error));
      assertEquals(AstCompletion.INCOMPLETE, retained.completion());
      assertRetained(original, retained, List.of(error));
    });
  }

  @Test
  void givenOwnedInputAndFrontendDiagnostics_whenAdmittingUnavailableSource_thenExactStagesOrderAndOccurrencesRemain() {
    verifyProfiles(original -> {
      // Arrange
      var input = diagnostic(DiagnosticStage.INPUT, DiagnosticSeverity.ERROR, "input");
      var frontend = diagnostic(DiagnosticStage.FRONTEND, DiagnosticSeverity.INFO, "frontend");
      var expected = List.of(input, frontend, input);
      // Act
      var unavailable = new AdmittedAstOutcome(original.profile(), unavailableQualification(original), Optional.empty(), Optional.of(original.snapshot()),
          Optional.of(original.environment()), Optional.of(CONFIGURATION), Optional.empty(), expected, "provided-source-subject");
      // Assert
      assertEquals(AstCompletion.UNAVAILABLE, unavailable.completion());
      assertTrue(unavailable.result().isEmpty());
      assertEquals(expected, unavailable.diagnostics());
      assertSame(input, unavailable.diagnostics().getFirst());
      assertSame(input, unavailable.diagnostics().getLast());
    });
  }

  @Test
  void givenRealCompilerError_whenProducingEitherProfile_thenTheOwnedCompilerDiagnosticAndIncompleteQualificationRemain() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var snapshot = snapshot("class SourceDiagnostics { int value() { return missing; } }");
      var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CONFIGURATION, profile);
      assertTrue(outcome.completion() != AstCompletion.COMPLETE);
      assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.stage() == DiagnosticStage.FRONTEND
          && value.isError() && value.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR));
      assertTrue(outcome.diagnostics().stream().allMatch(value -> value.stage() == DiagnosticStage.FRONTEND));
    }));
  }

  @Test
  void givenEqualSourceErrorsInDistinctEmissionSlots_whenAdmittedAndDecoded_thenReasonsReferenceEachOriginalEvent() {
    // Arrange
    verifyProfiles(original -> {
      var error = diagnostic(DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, "repeated");
      // Act
      var admitted = admitted(original, List.of(error, error));
      var decoded = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());
      // Assert
      for (var result : List.of(admitted, decoded)) {
        var references = result.qualification().reasons().stream().flatMap(reason -> reason.diagnostics().stream()).toList();
        assertEquals(List.of(0, 1), references.stream().map(SourceQualificationReason.DiagnosticReference::position).toList());
        assertEquals(result.coverage().validationEvidence().diagnosticEmissions().stream().map(DiagnosticEmission::address).toList(),
            references.stream().map(reference -> reference.emission().orElseThrow()).toList());
        assertTrue(references.stream().allMatch(reference -> reference.owner().equals(result.diagnostics())));
        assertEquals(List.of(error, error), references.stream().map(SourceQualificationReason.DiagnosticReference::diagnostic).toList());
      }
      assertEquals(admitted.qualification(), decoded.qualification());
    });
  }

  private void verifyForeignAdmission(List<DiagnosticSeverity> severities) {
    verifyProfiles(original -> assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () -> {
      for (var severity : severities) {
        var diagnostic = diagnostic(stage, severity, "foreign");
        assertThrows(IllegalArgumentException.class, () -> AstProductionAdmission.outcome(inputWith(original, List.of(diagnostic)),
            original.profile(), original.identity().semanticProductionRevision()));
        assertEquals(AstCompletion.COMPLETE, original.completion());
      }
    })));
  }

  private void verifyForeignCodec(List<DiagnosticSeverity> severities) {
    verifyProfiles(original -> assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () -> {
      for (var severity : severities) {
        var retained = admitted(original, List.of(diagnostic(DiagnosticStage.FRONTEND, severity, "stored")));
        var unchanged = stored(retained, DiagnosticStage.FRONTEND);
        assertEquals(retained.identity(), unchanged.identity());
        assertArrayEquals(AstCodec.encode(retained), unchanged.bytes());
        assertEquals(retained.diagnostics(), AstCodec.decode(unchanged.bytes(), unchanged.identity()).diagnostics());
        var foreign = stored(retained, stage);
        var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(foreign.bytes(), foreign.identity()));
        Throwable cause = failure;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("Source diagnostics require"), failure.toString());
      }
    })));
  }

  private static void assertRetained(AstResult original, AstResult retained, List<AnalysisDiagnostic> expected) {
    assertEquals(original.program(), retained.program());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(expected, retained.diagnostics());
    var events = retained.coverage().validationEvidence().diagnosticEmissions();
    assertEquals(expected, events.stream().map(DiagnosticEmission::diagnostic).toList());
    assertEquals(List.of(0, 1, 2).subList(0, expected.size()), events.stream().map(DiagnosticEmission::slot).toList());
    assertEquals(events.size(), events.stream().map(DiagnosticEmission::address).distinct().count());
    assertEquals(expected.stream().map(AnalysisDiagnostic::outcomeKind).toList(), events.stream().map(DiagnosticEmission::cause).toList());
    var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
    assertEquals(retained.completion(), decoded.completion());
    assertEquals(retained.qualification(), decoded.qualification());
    assertEquals(retained.program(), decoded.program());
    assertEquals(retained.aggregate(), decoded.aggregate());
    assertEquals(expected, decoded.diagnostics());
    assertEquals(events, decoded.coverage().validationEvidence().diagnosticEmissions());
  }

  private void verifyProfiles(FixtureAssertion assertion) {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot("class SourceDiagnostics { int value() { return 7; } }"),
          CONFIGURATION, profile);
      assertEquals(AstCompletion.COMPLETE, outcome.completion());
      var original = outcome.result().orElseThrow();
      assertTrue(original.diagnostics().isEmpty());
      assertion.verify(original);
    }));
  }

  private InputSnapshot snapshot(String source) throws IOException {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("SourceDiagnostics.java"), source)));
  }

  private static AnalysisDiagnostic diagnostic(DiagnosticStage stage, DiagnosticSeverity severity, String key) {
    return new AnalysisDiagnostic("provided:" + key, "provided.observation", stage, severity, "Supplied source diagnostic", List.of(),
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }

  private static SourceQualification unavailableQualification(AstResult original) {
    return new SourceQualification(AstCompletion.UNAVAILABLE, original.profile(), Optional.of(original.identity().semanticProductionRevision()),
        List.of(new SourceQualificationReason(SourceQualificationReason.Category.UNAVAILABLE_ROOTS,
            SourceQualificationReason.EvidenceRole.SOURCE_ROOTS, SourceQualificationReason.Capture.INSTANCE, List.of(), List.of(), List.of())));
  }

  private static AstResult admitted(AstResult original, List<AnalysisDiagnostic> diagnostics) {
    return AstResultAdmission.admit(inputWith(original, diagnostics), original.profile(), original.identity().semanticProductionRevision());
  }

  private static SyntaxAnalysisInput inputWith(AstResult original, List<AnalysisDiagnostic> diagnostics) {
    var evidence = original.coverage().validationEvidence();
    var emissions = new ArrayList<>(evidence.diagnosticEmissions());
    var values = new ArrayList<>(original.diagnostics());
    for (var diagnostic : diagnostics) {
      var ordinal = emissions.size();
      emissions.add(new DiagnosticEmission(new DiagnosticEmission.Address("ProvidedSourceDiagnostics", "source", ordinal), diagnostic,
          diagnostic.outcomeKind(), List.of(), ordinal));
      values.add(diagnostic);
    }
    var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), emissions);
    return new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()), values,
        coverage);
  }

  private static StoredSourceValue stored(AstResult original, DiagnosticStage stage) {
    var diagnostics = original.diagnostics().stream().map(value -> new AnalysisDiagnostic(value.diagnosticKey(), value.code(), stage,
        value.severity(), value.message(), value.origins(), value.outcomeKind())).toList();
    var emissions = original.coverage().validationEvidence().diagnosticEmissions().stream().map(event ->
        new DiagnosticEmission(event.address(), diagnostics.get(event.slot()), event.cause(), event.contexts(), event.slot())).toList();
    var canonical = new CanonicalValueWriter("canonical-java-source/1");
    canonical.text(original.identity().sourceModelVersion());
    canonical.text(original.profile().version());
    canonical.text(original.identity().semanticProductionRevision());
    canonical.bytes(CapturedValueEncoding.encode(Optional.of(original.snapshot()), Optional.of(original.environment())));
    writeInput(canonical, new RetainedValueCodec(true, original.identity().sourceModelVersion()), original, diagnostics, emissions);
    var identity = AstIdentity.fromContent(SoftwareVersion.from(original.snapshot(), original.environment()).signature(),
        original.identity().sourceModelVersion(), original.profile().version(), original.identity().semanticProductionRevision(), canonical.digest("ast-content"));
    var full = new CanonicalValueWriter(original.identity().sourceModelVersion());
    full.text(original.profile().version());
    full.text(original.identity().semanticProductionRevision());
    full.text(original.completion().name());
    var codec = new RetainedValueCodec(false, original.identity().sourceModelVersion());
    codec.write(full, identity);
    writeInput(full, codec, original, diagnostics, emissions);
    return new StoredSourceValue(identity, full.toByteArray());
  }

  private static void writeInput(CanonicalValueWriter writer, RetainedValueCodec codec, AstResult original,
      List<AnalysisDiagnostic> diagnostics, List<DiagnosticEmission> emissions) {
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var shape = AstValueSchema.type(SyntaxAnalysisInput.class);
    var components = shape.components(input);
    writer.text(shape.tag());
    writer.count(shape.fields().size());
    for (var index = 0; index < shape.fields().size(); index++) {
      var field = shape.fields().get(index);
      writer.text(field);
      if (field.equals("diagnostics")) {
        codec.write(writer, diagnostics);
      } else if (field.equals("languageFeatureCoverageResult")) {
        writer.text("source-coverage-result");
        codec.write(writer, original.coverage().coverage());
        writeEvidence(writer, codec, original.coverage().validationEvidence(), emissions);
      } else {
        codec.write(writer, components.get(index));
      }
    }
  }

  private static void writeEvidence(CanonicalValueWriter writer, RetainedValueCodec codec, CoverageValidationEvidence evidence,
      List<DiagnosticEmission> emissions) {
    var shape = AstValueSchema.type(CoverageValidationEvidence.class);
    var components = shape.components(evidence);
    writer.text(shape.tag());
    writer.count(shape.fields().size());
    for (var index = 0; index < shape.fields().size(); index++) {
      var field = shape.fields().get(index);
      writer.text(field);
      codec.write(writer, field.equals("diagnosticEmissions") ? emissions : components.get(index));
    }
  }

  private record StoredSourceValue(AstIdentity identity, byte[] bytes) {
  }

  @FunctionalInterface
  private interface FixtureAssertion {
    void verify(AstResult original);
  }
}
