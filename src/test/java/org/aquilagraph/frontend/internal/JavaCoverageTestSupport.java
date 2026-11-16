package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.internal.JavaSourceCoverageReconciler.ReconciledCoverage;
import org.aquilagraph.frontend.internal.JavaTypeKindObserver.ObservedTypeKinds;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath;

/** Supplies altered actual evidence while retaining the independently captured expectation stages. */
public final class JavaCoverageTestSupport {
  private JavaCoverageTestSupport() {
  }

  static LanguageFeatureCoverageResult emptyResult(LanguageFeatureCoverage coverage, InputSnapshot snapshot) {
    var written = new SourceCoverageInventory(snapshot, coverage.release(), coverage.contractIdentity(), List.of(), Optional.empty());
    return CoverageAdmission.fromEvidence(coverage, written, new AttributedCoverageExpectations(written, List.of()),
        List.of(), List.of(), List.of());
  }

  static LanguageFeatureCoverageResult alteredResult(SyntaxAnalysisInput original, List<LanguageFeatureOccurrence> occurrences,
      List<LanguageFeatureObservation> observations, List<AnalysisDiagnostic> capabilityDiagnostics) {
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var outcomes = new ArrayList<FeatureContextOutcome>();
    for (var outcome : evidence.outcomes()) {
      if (outcome.occurrence().isEmpty() || observations.contains(outcome.observation().orElseThrow())
          && occurrences.contains(outcome.occurrence().orElseThrow())) {
        outcomes.add(outcome);
      }
    }
    for (var observation : observations) {
      if (original.languageFeatureCoverageResult().observations().contains(observation)) {
        continue;
      }
      var address = new SourceContext.Address(observation.sourceFileKey(), SourceContext.Partition.TREE, SourceOwnerPath.root(), "extra-tree", outcomes.size());
      outcomes.add(new FeatureContextOutcome(address, Optional.of(new LanguageFeatureOccurrence(observation.sourceFileKey(), observation.classification(), observation.origins())),
          Optional.of(observation), Optional.empty(), new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.NOT_REQUIRED, List.of(), Optional.empty(), Optional.empty(), Optional.empty()), List.of()));
    }
    var events = new ArrayList<DiagnosticEmission>();
    for (var event : evidence.diagnosticEmissions()) {
      if (!original.languageFeatureCoverageResult().isCapabilityDiagnostic(event.diagnostic()) || capabilityDiagnostics.contains(event.diagnostic())) {
        events.add(event.atSlot(events.size()));
      }
    }
    return CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), events);
  }

  public static Captured capture(SyntaxAnalysisInput original, AstProfile profile) {
    var configuration = original.environmentSnapshot().compilerConfiguration();
    var policy = JavaTranslationPolicy.forProfile(profile);
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(configuration);
    var sources = new SnapshotSourceCatalog(original.snapshot());
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), sources)) {
      var parsed = session.parse();
      var capture = JavaSourceCoverageCapture.capture(original.snapshot(), parsed, sources, coverage, policy);
      var properties = new JavaDeclarationPropertyEncounterProducer(parsed.units(), sources, parsed.sourceEvidence(), coverage, policy);
      var written = properties.observeWritten();
      var annotations = JavaAnnotationSourceInventory.capture(parsed, sources);
      var trees = new JavaCompilerKindDiagnostics(sources, parsed.sourceEvidence(), coverage, policy).observe(parsed.units());
      var compilation = session.analyze(parsed, policy);
      var attributed = capture.captureAttributed(compilation.semantics());
      var attributedProperties = properties.observeAttributed(compilation.semantics());
      var types = new JavaTypeKindObserver(sources, compilation.semantics(), compilation.sourceEvidence(), compilation.treeInventory(), coverage, policy)
          .observe(compilation.units());
      var translated = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(), compilation.treeInventory(), policy)
          .translate(original.snapshot(), compilation.units(), compilation.diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      var completeness = new JavaAnnotationCompleteness(annotations, compilation.semantics(), translated.diagnostics());
      var emissions = DiagnosticEmissions.combine(new CompilerDiagnosticTranslator(sources).translate(compilation.diagnostics()), trees.emissions(),
          types.emissions(), translated.emissions(), JavaTypeUseCompleteness.reconcile(original.snapshot(), trees.observations(), translated.value().aggregate()),
          completeness.reconcile(translated.value().aggregate()));
      return new Captured(original, capture.inventory(), attributed, trees, types, written, attributedProperties, emissions);
    } catch (IOException failure) {
      throw new UncheckedIOException("Cannot capture compiler evidence for supplied-fact reconciliation", failure);
    }
  }

  public record Captured(SyntaxAnalysisInput original, SourceCoverageInventory inventory, AttributedCoverageExpectations attributed,
      ObservedLanguageFeatures trees, ObservedTypeKinds types, ObservedPropertyEncounters writtenProperties,
      ObservedPropertyEncounters attributedProperties, DiagnosticEmissions emissions) {

    ReconciledCoverage reconcile(JavaProgram program, Optional<JavaAstAggregate> aggregate, DiagnosticEmissions creating) {
      return reconcile(inventory, attributed, program, aggregate, creating);
    }

    ReconciledCoverage reconcile(SourceCoverageInventory written, AttributedCoverageExpectations expectations,
        JavaProgram program, Optional<JavaAstAggregate> aggregate, DiagnosticEmissions creating) {
      return new JavaSourceCoverageReconciler(written, expectations, program, aggregate, creating)
          .reconcile(original.languageFeatureCoverage(), trees, types, writtenProperties, attributedProperties);
    }

    public SyntaxAnalysisInput withFacts(JavaProgram program, Optional<JavaAstAggregate> aggregate) {
      return withFacts(program, aggregate, emissions);
    }

    public SyntaxAnalysisInput withInventoryFacts(SourceCoverageInventory suppliedInventory, JavaProgram program, Optional<JavaAstAggregate> aggregate) {
      var expectations = new AttributedCoverageExpectations(suppliedInventory, attributed.projections());
      var reconciled = reconcile(suppliedInventory, expectations, program, aggregate, emissions);
      return new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), program, aggregate, reconciled.diagnostics(), reconciled.coverageResult());
    }

    SyntaxAnalysisInput withFacts(JavaProgram program, Optional<JavaAstAggregate> aggregate, DiagnosticEmissions creating) {
      var reconciled = reconcile(program, aggregate, creating);
      return new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), program, aggregate, reconciled.diagnostics(), reconciled.coverageResult());
    }
  }
}
