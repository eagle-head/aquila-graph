package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;

class AnalysisRequestTest {
  @Test
  void givenRelativeSourceRootAndExplicitConfiguration_whenRequestCreated_thenRootIsAbsoluteAndDependenciesAreRetained() {
    // Arrange
    var sourceRoot = Path.of("sources", "main");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var request = new AnalysisRequest(new SourceInput.Root(sourceRoot), configuration, AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.SAVE_COMPLETED);

    // Assert
    assertEquals(sourceRoot.toAbsolutePath().normalize(), ((SourceInput.Root) request.input()).path());
    assertSame(configuration, request.configuration());
    assertEquals(PublicationIntent.SAVE_COMPLETED, request.publicationIntent());
  }

  @Test
  void givenNullSourceRoot_whenRequestCreated_thenNullInputIsRejected() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var exception = assertThrows(NullPointerException.class,
        () -> new AnalysisRequest(null, configuration, AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertEquals("input", exception.getMessage());
  }

  @Test
  void givenNullCompilerConfiguration_whenRequestCreated_thenNullInputIsRejected() {
    // Arrange
    var sourceRoot = Path.of("sources");

    // Act
    var exception = assertThrows(NullPointerException.class,
        () -> new AnalysisRequest(new SourceInput.Root(sourceRoot), null, AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertEquals("configuration", exception.getMessage());
  }

  @Test
  void givenNullRepositoryPolicy_whenRequestCreated_thenNullInputIsRejected() {
    // Arrange
    var sourceRoot = Path.of("sources");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var exception = assertThrows(NullPointerException.class, () -> new AnalysisRequest(new SourceInput.Root(sourceRoot), configuration, AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), null));

    // Assert
    assertEquals("publicationIntent", exception.getMessage());
  }
}
