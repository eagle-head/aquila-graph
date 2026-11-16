package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticSubjectReconciliationTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final AstProfile PROFILE = AstProfile.JAVA_SOURCE_MODULE_SERVICES;
  @TempDir Path sourceRoot;

  @Test
  void givenOriginlessDeclarationDiagnostic_whenOpaqueOwnerChanges_thenExplicitSubjectAloneSelectsTheFailedContext() throws IOException {
    // Arrange
    var original = analyze("class Sample { int field; int method(int parameter) { return parameter; } }");
    var captured = JavaCoverageTestSupport.capture(original, PROFILE);
    var method = original.program().methods().stream().filter(value -> value.name().equals("method")).findFirst().orElseThrow();
    var context = captured.inventory().contexts().stream()
        .filter(value -> value.address().partition() == SourceContext.Partition.TREE
            && value.feature().orElseThrow().identifier().equals("RETURN")).findFirst().orElseThrow().address();
    var diagnostic = diagnostic("declaration-observation", DiagnosticSeverity.ERROR);
    var subject = new DiagnosticSubject.Declaration(method.methodKey());
    var first = entry("opaque-blue", diagnostic, subject);
    var renamed = entry("opaque-orange", diagnostic, subject);
    var wrongSubject = entry(method.methodKey() + ":declaration", diagnostic, new DiagnosticSubject.Declaration("another-declaration"));
    var absentSubject = entry(method.methodKey() + ":parameters", diagnostic, DiagnosticSubject.None.INSTANCE);

    // Act
    var firstResult = captured.reconcile(original.program(), Optional.empty(), withEntry(captured.emissions(), first));
    var renamedResult = captured.reconcile(original.program(), Optional.empty(), withEntry(captured.emissions(), renamed));
    var wrongResult = captured.reconcile(original.program(), Optional.empty(), withEntry(captured.emissions(), wrongSubject));
    var absentResult = captured.reconcile(original.program(), Optional.empty(), withEntry(captured.emissions(), absentSubject));

    // Assert
    assertEquals(List.of(), diagnostic.origins());
    assertEquals(List.of(first.event().address()), outcome(firstResult, context).emissions());
    assertEquals(List.of(renamed.event().address()), outcome(renamedResult, context).emissions());
    assertEquals(FeatureContextOutcome.Mode.FAILED, outcome(firstResult, context).support().mode());
    assertEquals(event(firstResult, first.event().address()).contexts(), event(renamedResult, renamed.event().address()).contexts());
    assertTrue(event(firstResult, first.event().address()).contexts().contains(context));
    assertFalse(outcome(wrongResult, context).emissions().contains(wrongSubject.event().address()));
    assertFalse(outcome(absentResult, context).emissions().contains(absentSubject.event().address()));
    assertEquals(List.of(), event(wrongResult, wrongSubject.event().address()).contexts());
    assertEquals(List.of(), event(absentResult, absentSubject.event().address()).contexts());
    assertSame(diagnostic, firstResult.diagnostics().get(event(firstResult, first.event().address()).slot()));
    assertEquals(diagnostic.outcomeKind(), event(firstResult, first.event().address()).cause());
    assertForwardAndReverseOwnership(firstResult, first.event().address());
    assertForwardAndReverseOwnership(renamedResult, renamed.event().address());
  }

  @Test
  void givenCapturedPolicyInstanceAndAnotherContext_whenRealReconciliationRuns_thenOneEventOwnsBothExactContextAssociations() throws IOException {
    // Arrange
    var original = analyze("class Sample { int method() { return (); } }");
    var captured = JavaCoverageTestSupport.capture(original, PROFILE);
    var observation = captured.trees().observations().stream()
        .filter(value -> value.classification().feature().identifier().equals("ERRONEOUS")).findFirst().orElseThrow();
    var diagnostic = observation.diagnostic().orElseThrow();
    var creating = captured.emissions().events().stream().filter(value -> value.diagnostic() == diagnostic).findFirst().orElseThrow();
    var selected = captured.inventory().contexts().stream()
        .filter(value -> value.address().partition() == SourceContext.Partition.TREE
            && value.feature().orElseThrow().equals(observation.classification().feature()) && value.origins().equals(observation.origins()))
        .findFirst().orElseThrow();
    var extraAddress = new SourceContext.Address(selected.address().sourceFileKey(), selected.address().partition(), selected.address().ownerPath(),
        selected.address().role(), captured.inventory().contexts().stream().mapToInt(value -> value.address().ordinal()).max().orElseThrow() + 1);
    var contexts = new ArrayList<>(captured.inventory().contexts());
    contexts.add(new SourceContext(extraAddress, selected.feature(), selected.origins(), selected.writtenValue(), selected.requiresAttribution()));
    contexts.sort(Comparator.comparing(value -> value.address().partition()));
    var inventory = new SourceCoverageInventory(original.snapshot(), original.languageFeatureCoverage().release(),
        original.languageFeatureCoverage().contractIdentity(), contexts, captured.inventory().writtenExpressions());
    var occurrences = new ArrayList<>(captured.trees().occurrences());
    var observations = new ArrayList<>(captured.trees().observations());
    occurrences.add(captured.trees().occurrences().get(captured.trees().observations().indexOf(observation)));
    observations.add(observation);
    var observedTrees = new ObservedLanguageFeatures(occurrences, observations, captured.trees().emissions());
    var expectations = new AttributedCoverageExpectations(inventory, captured.attributed().projections());

    // Act
    var result = new JavaSourceCoverageReconciler(inventory, expectations, original.program(), original.aggregate(), captured.emissions())
        .reconcile(original.languageFeatureCoverage(), observedTrees, captured.types(), captured.writtenProperties(), captured.attributedProperties());
    var evidence = result.coverageResult().validationEvidence();
    var readmitted = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), inventory, expectations,
        evidence.outcomes(), evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Assert
    var finalEvent = event(result, creating.address());
    assertEquals(1, evidence.diagnosticEmissions().stream().filter(value -> value.diagnostic() == diagnostic).count());
    assertEquals(captured.emissions().events().indexOf(creating), finalEvent.slot());
    assertEquals(List.of(creating.address()), outcome(result, selected.address()).emissions());
    assertEquals(List.of(creating.address()), outcome(result, extraAddress).emissions());
    assertTrue(finalEvent.contexts().containsAll(List.of(selected.address(), extraAddress)));
    assertEquals(1, finalEvent.contexts().stream().filter(extraAddress::equals).count());
    assertSame(diagnostic, result.diagnostics().get(finalEvent.slot()));
    assertEquals(creating.cause(), finalEvent.cause());
    assertEquals(creating.diagnostic().origins(), finalEvent.diagnostic().origins());
    assertEquals(evidence.diagnosticEmissions(), readmitted.validationEvidence().diagnosticEmissions());
    assertForwardAndReverseOwnership(result, creating.address());
  }

  @Test
  void givenEqualDistinctCreatingValues_whenRealReconciliationFinalizes_thenBothOrderedInstancesReachPublicDiagnostics() throws IOException {
    // Arrange
    var original = analyze("class Sample { int method() { return 1; } }");
    var captured = JavaCoverageTestSupport.capture(original, PROFILE);
    var first = diagnostic("same-observation", DiagnosticSeverity.INFO);
    var second = diagnostic("same-observation", DiagnosticSeverity.INFO);
    var firstEntry = entry("first-owner", first, DiagnosticSubject.None.INSTANCE);
    var secondEntry = entry("second-owner", second, DiagnosticSubject.None.INSTANCE);
    var creating = withEntry(withEntry(captured.emissions(), firstEntry), secondEntry);

    // Act
    var result = captured.reconcile(original.program(), original.aggregate(), creating);
    var admitted = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), original.aggregate(),
        result.diagnostics(), result.coverageResult());

    // Assert
    assertEquals(first, second);
    assertNotSame(first, second);
    var firstEvent = event(result, firstEntry.event().address());
    var secondEvent = event(result, secondEntry.event().address());
    assertEquals(captured.emissions().entries().size(), firstEvent.slot());
    assertEquals(firstEvent.slot() + 1, secondEvent.slot());
    assertEquals(List.of(), firstEvent.contexts());
    assertEquals(List.of(), secondEvent.contexts());
    assertSame(first, admitted.diagnostics().get(firstEvent.slot()));
    assertSame(second, admitted.diagnostics().get(secondEvent.slot()));
    assertEquals(creating.events().stream().map(DiagnosticEmission::address).toList(),
        admitted.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions().stream().map(DiagnosticEmission::address).toList());
  }

  private SyntaxAnalysisInput analyze(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(sourceRoot),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Sample.java")), source)));
    return new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()))
        .analyze(snapshot, CONFIGURATION, JavaTranslationPolicy.forProfile(PROFILE));
  }

  private static AnalysisDiagnostic diagnostic(String key, DiagnosticSeverity severity) {
    return new AnalysisDiagnostic(key, "frontend.declaration-observation", DiagnosticStage.FRONTEND, severity,
        "Supplied declaration observation", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }

  private static DiagnosticEntry entry(String owner, AnalysisDiagnostic diagnostic, DiagnosticSubject subject) {
    return new DiagnosticEntry(new DiagnosticEmission(new DiagnosticEmission.Address("declaration-source", owner, 0), diagnostic,
        diagnostic.outcomeKind(), List.of(), -1), subject);
  }

  private static DiagnosticEmissions withEntry(DiagnosticEmissions original, DiagnosticEntry entry) {
    return DiagnosticEmissions.combine(original, new DiagnosticEmissions(List.of(entry)));
  }

  private static DiagnosticEmission event(JavaSourceCoverageReconciler.ReconciledCoverage result, DiagnosticEmission.Address address) {
    return result.coverageResult().validationEvidence().diagnosticEmissions().stream().filter(value -> value.address().equals(address))
        .findFirst().orElseThrow();
  }

  private static FeatureContextOutcome outcome(JavaSourceCoverageReconciler.ReconciledCoverage result, SourceContext.Address address) {
    return result.coverageResult().validationEvidence().outcomes().stream().filter(value -> value.address().equals(address))
        .findFirst().orElseThrow();
  }

  private static void assertForwardAndReverseOwnership(JavaSourceCoverageReconciler.ReconciledCoverage result, DiagnosticEmission.Address address) {
    assertEquals(result.coverageResult().validationEvidence().outcomes().stream().filter(value -> value.emissions().contains(address))
        .map(FeatureContextOutcome::address).toList(), event(result, address).contexts());
  }
}
