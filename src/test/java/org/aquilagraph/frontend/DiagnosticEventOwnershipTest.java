package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.FeatureContextOutcome.Support;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.UnmatchedSourceFact;
import org.aquilagraph.source.api.CoverageActualFacts;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;

class DiagnosticEventOwnershipTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @Test
  void givenOwnedCapabilityDiagnostic_whenAnEqualUnownedEventIsAdded_thenFactoryRejectsTheOrphanAddress() throws IOException {
    // Arrange
    var input = analyze("class Example { void value() { while (false) { } } }");
    var event = capabilityEvent(input);
    var events = appended(input, event.diagnostic(), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> withEvents(input, events));

    // Assert
    assertEquals("Every coverage event requires exact address-based context ownership", failure.getMessage());
  }

  @Test
  void givenOwnedCapabilityDiagnostic_whenAnEqualEventClaimsAnUnusedContext_thenFactoryRejectsTheFalseReverseAssociation() throws IOException {
    // Arrange
    var input = analyze("class Example { void value() { while (false) { } } }");
    var event = capabilityEvent(input);
    var events = appended(input, event.diagnostic(), event.contexts());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> withEvents(input, events));

    // Assert
    assertEquals("Every coverage event requires exact address-based context ownership", failure.getMessage());
  }

  @Test
  void givenFailedExpectedSupport_whenAnEqualUnownedGeneralizedEventIsAdded_thenFactoryRejectsTheOrphanAddress() throws IOException {
    // Arrange
    var input = withheldFacts();
    var event = input.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().stream()
        .filter(value -> value.diagnostic().code().equals("frontend.missing-source-evidence")).findFirst().orElseThrow();
    var events = appended(input, event.diagnostic(), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> withEvents(input, events));

    // Assert
    assertEquals("Every coverage event requires exact address-based context ownership", failure.getMessage());
  }

  @Test
  void givenFailedExpectedSupport_whenAnEqualEventClaimsAnUnusedContext_thenFactoryRejectsTheFalseReverseAssociation() throws IOException {
    // Arrange
    var input = withheldFacts();
    var event = input.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().stream()
        .filter(value -> value.diagnostic().code().equals("frontend.missing-source-evidence")).findFirst().orElseThrow();
    var events = appended(input, event.diagnostic(), event.contexts());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> withEvents(input, events));

    // Assert
    assertEquals("Every coverage event requires exact address-based context ownership", failure.getMessage());
  }

  @Test
  void givenEqualDiagnosedContexts_whenOneCreatingEventOwnsBoth_thenOneFinalSlotRemains() throws IOException {
    // Arrange
    var input = analyze("class Example { void value() { while (false) { } } }");
    var originalOwners = capabilityEvent(input).contexts();

    // Act
    var retained = repeatedContext(input, true);

    // Assert
    var events = retained.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().stream()
        .filter(event -> event.diagnostic().code().equals("frontend.unsupported-tree-kind.while-loop")).toList();
    assertEquals(1, events.size());
    assertEquals(originalOwners.size() + 1, events.getFirst().contexts().size());
    assertEquals(2, retained.languageFeatureCoverageResult().observations().stream()
        .filter(value -> value.classification().feature().identifier().equals("WHILE_LOOP")).count());
  }

  @Test
  void givenEqualDiagnosedContexts_whenDistinctCreatingEventsOwnThem_thenTwoEqualSlotsRemainDistinct() throws IOException {
    // Arrange
    var input = analyze("class Example { void value() { while (false) { } } }");

    // Act
    var retained = repeatedContext(input, false);

    // Assert
    var events = retained.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().stream()
        .filter(event -> event.diagnostic().code().equals("frontend.unsupported-tree-kind.while-loop")).toList();
    assertEquals(2, events.size());
    assertEquals(events.getFirst().diagnostic(), events.getLast().diagnostic());
    assertFalse(events.getFirst().address().equals(events.getLast().address()));
    assertEquals(2, retained.languageFeatureCoverageResult().capabilityDiagnostics().stream()
        .filter(value -> value.equals(events.getFirst().diagnostic())).count());
  }

  @Test
  void givenOrdinaryCompilerErrorWithoutCoverageContext_whenItsEventIsAdmitted_thenItsExactSlotRemains() throws IOException {
    // Arrange
    var input = analyze("class Example { int value() { return 1; } }");
    var diagnostic = new AnalysisDiagnostic(StableId.sha256("diagnostic", "compiler-observation"), "compiler.err.observation",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, "Compiler observation", List.of(), AnalysisOutcomeKind.COMPILER_ERROR);

    // Act
    var retained = admittedEvents(input, appended(input, diagnostic, List.of()));

    // Assert
    assertEquals(List.of(diagnostic), retained.diagnostics());
    assertEquals(List.of(), retained.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().getFirst().contexts());
  }

  @Test
  void givenOrdinaryInfoWithoutCoverageContext_whenItsEventIsAdmitted_thenItsExactSlotRemains() throws IOException {
    // Arrange
    var input = analyze("class Example { int value() { return 1; } }");
    var diagnostic = new AnalysisDiagnostic(StableId.sha256("diagnostic", "informational-observation"), "frontend.note",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.INFO, "Informational observation", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);

    // Act
    var retained = admittedEvents(input, appended(input, diagnostic, List.of()));

    // Assert
    assertEquals(List.of(diagnostic), retained.diagnostics());
    assertEquals(List.of(), retained.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().getFirst().contexts());
  }

  @Test
  void givenOwnerBoundUnmatchedFact_whenItsCreatingEventIsAdmitted_thenActualInputRetainsTheFactAndBothFailureSlots() throws IOException {
    // Arrange
    var input = analyze("class Example { int value() { return 1; } }");
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var selected = evidence.outcomes().stream()
        .filter(outcome -> outcome.occurrence().orElseThrow().classification().feature().identifier().equals("INT_LITERAL"))
        .findFirst().orElseThrow();
    var fact = selected.support().facts().stream().filter(value -> value.value() instanceof CoverageFactValue.Source).findFirst().orElseThrow();
    var actual = CoverageActualFacts.capture(input.program(), input.aggregate());
    var missing = evidenceDiagnostic("frontend.missing-source-evidence", selected);
    var unexpected = evidenceDiagnostic("frontend.unexpected-source-evidence", selected);
    var missingEvent = new DiagnosticEmission(new DiagnosticEmission.Address("missing-literal", "literal-owner", 0), missing,
        missing.outcomeKind(), List.of(selected.address()), 0);
    var unexpectedEvent = new DiagnosticEmission(new DiagnosticEmission.Address("unmatched-literal", "literal-owner", 0), unexpected,
        unexpected.outcomeKind(), List.of(selected.address()), 1);
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new Support(Mode.FAILED, List.of(), Optional.empty(), Optional.empty(), Optional.empty()), List.of(missingEvent.address()))
        : outcome).toList();
    var unmatched = new UnmatchedSourceFact(selected.address(), fact, actual.indexOf(fact), UnmatchedSourceFact.Reason.EXTRA,
        unexpectedEvent.address());

    // Act
    var result = LanguageFeatureCoverageResult.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), outcomes, List.of(unmatched), List.of(missingEvent, unexpectedEvent));
    var retained = new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
        List.of(missing, unexpected), result);

    // Assert
    assertEquals(List.of(unmatched), retained.languageFeatureCoverageResult().validationEvidence().unmatchedFacts());
    assertEquals(List.of(missing, unexpected), retained.diagnostics());
  }

  private static SyntaxAnalysisInput analyze(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/context-check/sources"),
        List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CONFIGURATION);
  }

  private static SyntaxAnalysisInput withheldFacts() throws IOException {
    var input = analyze("class Example { int value() { return 1; } }");
    return JavaCoverageTestSupport.withFacts(input, new JavaProgram(List.of()), Optional.empty(),
        JavaCoverageTestSupport.diagnostics(input, "withheld-declaration"));
  }

  private static DiagnosticEmission capabilityEvent(SyntaxAnalysisInput input) {
    return input.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().stream()
        .filter(event -> input.languageFeatureCoverageResult().isCapabilityDiagnostic(event.diagnostic())).findFirst().orElseThrow();
  }

  private static List<DiagnosticEmission> appended(SyntaxAnalysisInput input, AnalysisDiagnostic diagnostic,
      List<SourceContext.Address> contexts) {
    var events = new ArrayList<>(input.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions());
    events.add(new DiagnosticEmission(new DiagnosticEmission.Address("separate-emission", "source-owner", 0), diagnostic,
        diagnostic.outcomeKind(), contexts, events.size()));
    return events;
  }

  private static LanguageFeatureCoverageResult withEvents(SyntaxAnalysisInput input, List<DiagnosticEmission> events) {
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    return LanguageFeatureCoverageResult.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), events);
  }

  private static SyntaxAnalysisInput admittedEvents(SyntaxAnalysisInput input, List<DiagnosticEmission> events) {
    return new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
        events.stream().map(DiagnosticEmission::diagnostic).toList(), withEvents(input, events));
  }

  private static SyntaxAnalysisInput repeatedContext(SyntaxAnalysisInput input, boolean shared) {
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var event = capabilityEvent(input);
    var original = evidence.outcomes().stream().filter(outcome -> outcome.emissions().contains(event.address())
        && outcome.observation().flatMap(value -> value.diagnostic()).filter(event.diagnostic()::equals).isPresent()).findFirst().orElseThrow();
    var captured = evidence.writtenInventory().contexts().stream().filter(context -> context.address().equals(original.address())).findFirst()
        .orElseThrow();
    var address = new SourceContext.Address(original.address().sourceFileKey(), original.address().partition(), original.address().ownerPath(),
        original.address().role(), original.address().ordinal() + 1);
    var context = new SourceContext(address, captured.feature(), captured.origins(), captured.writtenValue(), captured.requiresAttribution());
    var creatingAddress = shared ? event.address() : new DiagnosticEmission.Address("separate-emission", "same-source-span", 0);
    var outcome = new FeatureContextOutcome(address, original.occurrence(), original.observation(), original.encounteredValue(),
        original.support(), List.of(creatingAddress));
    var contexts = new ArrayList<>(evidence.writtenInventory().contexts());
    contexts.add(context);
    contexts.sort(Comparator.comparing(value -> value.address().partition()));
    var outcomes = new ArrayList<>(evidence.outcomes());
    outcomes.add(outcome);
    outcomes.sort(Comparator.comparing(value -> value.address().partition()));
    var written = new SourceCoverageInventory(input.snapshot(), input.languageFeatureCoverage().release(),
        input.languageFeatureCoverage().contractIdentity(), contexts);
    var events = new ArrayList<>(evidence.diagnosticEmissions());
    if (shared) {
      var owners = new ArrayList<>(event.contexts());
      owners.add(address);
      events.set(event.slot(), new DiagnosticEmission(event.address(), event.diagnostic(), event.cause(), owners, event.slot()));
    } else {
      events.add(new DiagnosticEmission(creatingAddress, event.diagnostic(), event.cause(), List.of(address), events.size()));
    }
    var result = LanguageFeatureCoverageResult.fromEvidence(input.languageFeatureCoverage(), written,
        new AttributedCoverageExpectations(written, evidence.attributedExpectations().projections()), outcomes, evidence.unmatchedFacts(), events);
    return new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
        events.stream().map(DiagnosticEmission::diagnostic).toList(), result);
  }

  private static AnalysisDiagnostic evidenceDiagnostic(String code, FeatureContextOutcome outcome) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", code), code, DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR,
        "Retained literal support requires reconciliation", outcome.occurrence().orElseThrow().origins(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }
}
