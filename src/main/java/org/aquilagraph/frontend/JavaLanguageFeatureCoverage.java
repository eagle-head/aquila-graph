package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.LanguageFeatureCoverage;

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

}
