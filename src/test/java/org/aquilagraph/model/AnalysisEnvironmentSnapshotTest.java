package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalysisEnvironmentSnapshotTest {
  @Test
  void givenCompilerIdentityChanges_whenEnvironmentDigestIsDerived_thenEachIdentityIsDistinct() {
    // Arrange
    var configuration = CompilerConfiguration.java25();
    var analysisOptions = List.of("--release", "25", "-encoding", "UTF-8", "-proc:none");
    var baseline =
        environment(configuration, "compiler.Implementation", "Vendor", "25.0.4+1", analysisOptions);
    var differentImplementation =
        environment(configuration, "other.Implementation", "Vendor", "25.0.4+1", analysisOptions);
    var differentVendor =
        environment(configuration, "compiler.Implementation", "Other Vendor", "25.0.4+1", analysisOptions);
    var differentVersion =
        environment(configuration, "compiler.Implementation", "Vendor", "25.0.4+2", analysisOptions);

    // Act
    var digests =
        Set.of(
            baseline.environmentDigest(),
            differentImplementation.environmentDigest(),
            differentVendor.environmentDigest(),
            differentVersion.environmentDigest());

    // Assert
    assertEquals(4, digests.size());
  }

  private static AnalysisEnvironmentSnapshot environment(
      CompilerConfiguration configuration,
      String implementation,
      String vendor,
      String version,
      List<String> analysisOptions) {
    return new AnalysisEnvironmentSnapshot(
        configuration,
        implementation,
        vendor,
        version,
        analysisOptions,
        List.of(),
        List.of());
  }
}
