package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxAnalysisInput;

/** Supplies altered actual evidence while retaining the independently captured expectation stages. */
public final class JavaCoverageTestSupport {
  private JavaCoverageTestSupport() {
  }

  static LanguageFeatureCoverageResult emptyResult(LanguageFeatureCoverage coverage, InputSnapshot snapshot) {
    var written = new SourceCoverageInventory(snapshot, coverage.release(), coverage.contractIdentity(), List.of());
    return LanguageFeatureCoverageResult.fromEvidence(coverage, written, new AttributedCoverageExpectations(written, List.of()),
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
    return LanguageFeatureCoverageResult.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), events);
  }

  public static List<DiagnosticEmission> emissions(SyntaxAnalysisInput original) {
    return original.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions();
  }

  static DiagnosticEmissionCollector collector(SyntaxAnalysisInput original, String producer) {
    var collector = new DiagnosticEmissionCollector(producer, "retained-events");
    collector.include(new DiagnosticEmissions(emissions(original)));
    return collector;
  }

  public static SyntaxAnalysisInput withFacts(SyntaxAnalysisInput original, JavaProgram program, Optional<JavaAstAggregate> aggregate,
      List<DiagnosticEmission> emissions) {
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var reconciled = new JavaSourceCoverageReconciler(evidence.writtenInventory(), evidence.attributedExpectations(), program, aggregate,
        new DiagnosticEmissions(emissions)).reconcileRetained(original.languageFeatureCoverage(), evidence.outcomes());
    return new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), program, aggregate, reconciled.diagnostics(), reconciled.coverageResult());
  }
}
