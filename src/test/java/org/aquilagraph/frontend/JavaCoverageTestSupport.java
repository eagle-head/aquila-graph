package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticEmission;
import org.aquilagraph.model.FeatureContextOutcome;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureCoverageResult;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.SourceContext;
import org.aquilagraph.model.SourceOwnerPath;
import org.aquilagraph.model.AttributedCoverageExpectations;
import org.aquilagraph.model.SourceCoverageInventory;
import org.aquilagraph.syntax.JavaAstAggregate;
import org.aquilagraph.syntax.SyntaxAnalysisInput;

/** Supplies altered actual evidence while retaining the independently captured expectation stages. */
final class JavaCoverageTestSupport {
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

  static List<AnalysisDiagnostic> diagnostics(SyntaxAnalysisInput original, String producer) {
    return TranslationDiagnosticEvidence.fromEvents(producer, original.languageFeatureCoverageResult().validationEvidence().diagnosticEmissions());
  }

  static SyntaxAnalysisInput withFacts(SyntaxAnalysisInput original, JavaProgram program, Optional<JavaAstAggregate> aggregate,
      List<AnalysisDiagnostic> diagnostics) {
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var reconciled = new JavaSourceCoverageReconciler(evidence.writtenInventory(), evidence.attributedExpectations(), program, aggregate,
        TranslationDiagnosticEvidence.events(diagnostics)).reconcileRetained(original.languageFeatureCoverage(), evidence.outcomes());
    return new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), program, aggregate, reconciled.diagnostics(), reconciled.coverageResult());
  }
}
