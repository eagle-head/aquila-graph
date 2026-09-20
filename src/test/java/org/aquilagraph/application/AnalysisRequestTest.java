package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;

class AnalysisRequestTest {
  @Test
  void givenRelativeSourceRootAndExplicitConfiguration_whenRequestCreated_thenRootIsAbsoluteAndDependenciesAreRetained() {
    // Arrange
    var sourceRoot = Path.of("sources", "main");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var request = new AnalysisRequest(sourceRoot, configuration, RepositorySavePolicy.SAVE_COMPLETED);

    // Assert
    assertEquals(sourceRoot.toAbsolutePath().normalize(), request.sourceRoot());
    assertSame(configuration, request.compilerConfiguration());
    assertEquals(RepositorySavePolicy.SAVE_COMPLETED, request.repositorySavePolicy());
  }

  @Test
  void givenNullSourceRoot_whenRequestCreated_thenNullInputIsRejected() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var exception = assertThrows(NullPointerException.class,
        () -> new AnalysisRequest(null, configuration, RepositorySavePolicy.DO_NOT_SAVE));

    // Assert
    assertEquals("sourceRoot", exception.getMessage());
  }

  @Test
  void givenNullCompilerConfiguration_whenRequestCreated_thenNullInputIsRejected() {
    // Arrange
    var sourceRoot = Path.of("sources");

    // Act
    var exception = assertThrows(NullPointerException.class,
        () -> new AnalysisRequest(sourceRoot, null, RepositorySavePolicy.DO_NOT_SAVE));

    // Assert
    assertEquals("compilerConfiguration", exception.getMessage());
  }

  @Test
  void givenNullRepositoryPolicy_whenRequestCreated_thenNullInputIsRejected() {
    // Arrange
    var sourceRoot = Path.of("sources");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var exception = assertThrows(NullPointerException.class, () -> new AnalysisRequest(sourceRoot, configuration, null));

    // Assert
    assertEquals("repositorySavePolicy", exception.getMessage());
  }
}
