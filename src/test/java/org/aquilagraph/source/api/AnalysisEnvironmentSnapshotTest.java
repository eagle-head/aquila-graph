package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.junit.jupiter.api.Test;

class AnalysisEnvironmentSnapshotTest {
  @Test
  void givenPreviewOptionsInFlagAndAssignmentForms_whenEnvironmentIsCreated_thenEachOptionIsRejected() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var previewOptions = List.of("--enable-preview", "--enable-preview=", "--enable-preview=true", "--enable-preview=false");

    // Act
    var failures = previewOptions.stream().map(option -> assertThrows(IllegalArgumentException.class,
        () -> environment(configuration, "compiler.Implementation", "Vendor", "25.0.4+1", List.of("-Xlint:all", option)))).toList();

    // Assert
    assertEquals(4, failures.size());
    failures.forEach(failure -> assertEquals("Preview compiler option is not supported: --enable-preview", failure.getMessage()));
  }

  @Test
  void givenOptionsWithNamesNearThePreviewFlag_whenEnvironmentIsCreated_thenTheirExactValuesAndOrderAreRetained() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var analysisOptions = List.of("--enable-preview-extra", "--enable-previewing", "-Xlint:preview");

    // Act
    var snapshot = environment(configuration, "compiler.Implementation", "Vendor", "25.0.4+1", analysisOptions);

    // Assert
    assertEquals(analysisOptions, snapshot.analysisOptions());
  }

  @Test
  void givenCompilerIdentityChanges_whenEnvironmentDigestIsDerived_thenEachIdentityIsDistinct() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
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
