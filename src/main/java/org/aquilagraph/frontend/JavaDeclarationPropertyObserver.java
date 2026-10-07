package org.aquilagraph.frontend;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.LanguageFeaturePolicy;

/** Creates inseparable diagnostics and contextual observations for declaration properties. */
final class JavaDeclarationPropertyObserver {
  private final LanguageFeatureCoverage coverage;

  JavaDeclarationPropertyObserver(LanguageFeatureCoverage coverage) {
    this.coverage = Objects.requireNonNull(coverage, "coverage");
  }

  void observe(List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> occurrences,
      List<LanguageFeatureObservation> observations, SourceFile sourceFile, String context, String code, String message,
      Optional<SourceSpan> origin, String... featureIdentifiers) {
    Objects.requireNonNull(diagnostics, "diagnostics");
    Objects.requireNonNull(occurrences, "occurrences");
    Objects.requireNonNull(observations, "observations");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(featureIdentifiers, "featureIdentifiers");
    if (featureIdentifiers.length == 0) {
      throw new IllegalArgumentException("A declaration-property occurrence requires at least one exact feature");
    }
    var classifications = Arrays.stream(featureIdentifiers).map(identifier -> validatedClassification(identifier, code)).toList();
    var diagnostic = FrontendDiagnosticFactory.capabilityError(context, code, message, origin);
    TranslationDiagnosticEvidence.emit(diagnostics, "JavaDeclarationPropertyObserver", diagnostic);
    occurrences.addAll(classifications.stream()
        .map(classification -> new LanguageFeatureOccurrence(sourceFile.sourceFileKey(), classification, diagnostic.origins())).toList());
    observations.addAll(classifications.stream().map(classification -> new LanguageFeatureObservation(sourceFile.sourceFileKey(),
        classification, diagnostic.origins(), Optional.of(diagnostic))).toList());
  }

  private LanguageFeatureClassification validatedClassification(String identifier, String code) {
    var feature = new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, identifier);
    var classification = coverage.classificationOf(feature)
        .orElseThrow(() -> new IllegalArgumentException("Declaration property is absent from the active coverage contract: " + identifier));
    if (classification.policy() != LanguageFeaturePolicy.DIAGNOSED || !classification.diagnosticCode().orElseThrow().equals(code)) {
      throw new IllegalArgumentException("Declaration property does not declare the emitted diagnostic: " + identifier);
    }
    return classification;
  }
}
