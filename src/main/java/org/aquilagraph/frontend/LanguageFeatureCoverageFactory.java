package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.LanguageFeatureClassification;
import org.aquilagraph.model.LanguageFeatureCoverage;

/** Creates release-bound language coverage values while compiler configuration is available. */
final class LanguageFeatureCoverageFactory {
  private LanguageFeatureCoverageFactory() {
  }

  static LanguageFeatureCoverage fromConfiguration(CompilerConfiguration configuration,
      List<LanguageFeatureClassification> classifications) {
    Objects.requireNonNull(configuration, "configuration");
    return LanguageFeatureCoverage.forRelease(configuration.release(), classifications);
  }
}
