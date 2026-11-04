package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CfgSelection.Scope;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AnalyzeCommandArgumentsTest {
  @Test
  void givenAstRetentionIntent_whenCommandArgumentsAreCreated_thenUnsupportedPublicationIsRejected() {
    // Arrange
    var sourceRoot = Path.of("source");

    // Act
    Executable createArguments = () -> new AnalyzeCommandArguments(sourceRoot, JavaRelease.JAVA_25,
        PublicationIntent.SAVE_COMPLETE_AST);

    // Assert
    assertThrows(IllegalArgumentException.class, createArguments);
  }

  @Test
  void givenRelativeRootAndBothSupportedIntents_whenArgumentsAreConverted_thenBoundedRequestsRetainEachIntent() {
    // Arrange
    var sourceRoot = Path.of("checkout", "..", "source");
    var readOnly = new AnalyzeCommandArguments(sourceRoot, JavaRelease.JAVA_25, PublicationIntent.DO_NOT_SAVE);
    var publish = new AnalyzeCommandArguments(sourceRoot, JavaRelease.JAVA_25, PublicationIntent.SAVE_COMPLETED);
    var expectedInput = new SourceInput.Root(Path.of("source").toAbsolutePath());
    var expectedConfiguration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
    var expectedSelection = Optional.of(new CfgSelection(List.of(), CfgProfile.JAVA_BOUNDED, Scope.ALL_BODIES));
    var expectedReadOnly = new AnalysisRequest(expectedInput, expectedConfiguration, AstProfile.JAVA_SOURCE_BOUNDED,
        expectedSelection, PublicationIntent.DO_NOT_SAVE);
    var expectedPublication = new AnalysisRequest(expectedInput, expectedConfiguration, AstProfile.JAVA_SOURCE_BOUNDED,
        expectedSelection, PublicationIntent.SAVE_COMPLETED);

    // Act
    var readOnlyRequest = readOnly.toAnalysisRequest();
    var publicationRequest = publish.toAnalysisRequest();

    // Assert
    assertAll(() -> assertEquals(expectedReadOnly, readOnlyRequest),
        () -> assertEquals(expectedPublication, publicationRequest));
  }

  @Test
  void givenNullCommandArgumentComponent_whenArgumentsAreCreated_thenInvalidStateIsRejected() {
    // Arrange
    var sourceRoot = Path.of("source");

    // Act
    Executable nullSourceRoot = () -> new AnalyzeCommandArguments(null, JavaRelease.JAVA_25, PublicationIntent.DO_NOT_SAVE);
    Executable nullRelease = () -> new AnalyzeCommandArguments(sourceRoot, null, PublicationIntent.DO_NOT_SAVE);
    Executable nullSavePolicy = () -> new AnalyzeCommandArguments(sourceRoot, JavaRelease.JAVA_25, null);

    // Assert
    assertAll(() -> assertThrows(NullPointerException.class, nullSourceRoot),
        () -> assertThrows(NullPointerException.class, nullRelease),
        () -> assertThrows(NullPointerException.class, nullSavePolicy));
  }
}
