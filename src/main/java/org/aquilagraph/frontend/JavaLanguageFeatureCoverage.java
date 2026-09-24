package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureCoverageResult;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;

/** Provides the complete release-bound language-feature contract used by the Java frontend. */
final class JavaLanguageFeatureCoverage {
  private JavaLanguageFeatureCoverage() {
  }

  static LanguageFeatureCoverage fromConfiguration(CompilerConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    var compilerKinds = JavaCompilerKindCoverage.fromConfiguration(configuration);
    var typeKinds = JavaTypeKindCoverage.fromConfiguration(configuration);
    var declarationProperties = JavaDeclarationPropertyCoverage.fromConfiguration(configuration);
    var classifications = new ArrayList<>(compilerKinds.classifications());
    classifications.addAll(typeKinds.classifications());
    classifications.addAll(declarationProperties.classifications());
    return LanguageFeatureCoverageFactory.fromConfiguration(configuration, classifications);
  }

  static LanguageFeatureCoverageResult result(LanguageFeatureCoverage coverage, List<LanguageFeatureOccurrence> occurrences,
      List<LanguageFeatureObservation> observations, List<AnalysisDiagnostic> diagnostics) {
    Objects.requireNonNull(coverage, "coverage");
    Objects.requireNonNull(occurrences, "occurrences");
    Objects.requireNonNull(observations, "observations");
    Objects.requireNonNull(diagnostics, "diagnostics");
    Set<String> capabilityCodes = coverage.classifications().stream().filter(classification -> classification.blocksCompletion())
        .map(classification -> classification.diagnosticCode().orElseThrow()).collect(Collectors.toUnmodifiableSet());
    var capabilityDiagnostics = diagnostics.stream().filter(diagnostic -> capabilityCodes.contains(diagnostic.code())).toList();
    return new LanguageFeatureCoverageResult(coverage, occurrences, observations, capabilityDiagnostics);
  }
}
