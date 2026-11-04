package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;

class AnalysisRequestTest {
  @Test
  void givenRelativeSourceRootAndExplicitConfiguration_whenRequestCreated_thenRootIsAbsoluteAndDependenciesAreRetained() {
    // Arrange
    var sourceRoot = Path.of("sources", "main");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    var source = new AstProductionRequest(sourceRoot, configuration, AstProfile.JAVA_SOURCE_BOUNDED);
    var computation = new AnalysisComputationRequest(source, Optional.empty());

    // Act
    var request = new AnalysisRequest(computation, PublicationIntent.SAVE_COMPLETED);

    // Assert
    assertEquals(sourceRoot.toAbsolutePath().normalize(), ((SourceInput.Root) request.computation().source().input()).path());
    assertSame(configuration, request.computation().source().configuration());
    assertSame(source, request.computation().source());
    assertSame(computation, request.computation());
    assertEquals(PublicationIntent.SAVE_COMPLETED, request.publicationIntent());
  }

  @Test
  void givenMissingComputation_whenCreatingExecutionRequest_thenTheComputationIsRejected() {
    // Arrange
    var intent = PublicationIntent.DO_NOT_SAVE;

    // Act
    var failure = assertThrows(NullPointerException.class, () -> new AnalysisRequest(null, intent));

    // Assert
    assertEquals("computation", failure.getMessage());
  }

  @Test
  void givenMissingSourceOrSelection_whenCreatingComputationRequest_thenEachRequiredComponentIsRejected() {
    // Arrange
    var source = new AstProductionRequest(Path.of("sources"), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);

    // Act
    var missingSource = assertThrows(NullPointerException.class, () -> new AnalysisComputationRequest(null, Optional.empty()));
    var missingSelection = assertThrows(NullPointerException.class, () -> new AnalysisComputationRequest(source, null));

    // Assert
    assertEquals("source", missingSource.getMessage());
    assertEquals("cfgSelection", missingSelection.getMessage());
  }

  @Test
  void givenMissingIntent_whenCreatingExecutionRequest_thenTheIntentIsRejected() {
    // Arrange
    var source = new AstProductionRequest(Path.of("sources"), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);
    var computation = new AnalysisComputationRequest(source, Optional.empty());

    // Act
    var failure = assertThrows(NullPointerException.class, () -> new AnalysisRequest(computation, null));

    // Assert
    assertEquals("publicationIntent", failure.getMessage());
  }
}
