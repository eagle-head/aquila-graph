package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.junit.jupiter.api.Test;

class CompilerReleaseBindingTest {
  @Test
  void givenReleaseBoundConfiguration_whenCoverageAndInvocationAreCreated_thenBothUseTheConfiguredRelease() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var coverage = LanguageFeatureCoverageFactory.fromConfiguration(configuration, List.of());
    var invocation = JavaCompilerInvocation.from(configuration);
    var releaseOptionIndex = invocation.compilerOptions().indexOf("--release");

    // Assert
    assertEquals(configuration.release(), coverage.release());
    assertEquals(configuration.release().feature(), coverage.releaseNumber());
    assertEquals(Integer.toString(coverage.releaseNumber()), invocation.compilerOptions().get(releaseOptionIndex + 1));
  }
}
