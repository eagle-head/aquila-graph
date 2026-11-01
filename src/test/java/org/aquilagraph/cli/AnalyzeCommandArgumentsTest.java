package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.identity.api.JavaRelease;
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
  void givenValidatedCommandArguments_whenConvertedToAnalysisRequest_thenDomainValuesArePreserved() {
    // Arrange
    var sourceRoot = Path.of("source");
    var arguments = new AnalyzeCommandArguments(sourceRoot, JavaRelease.JAVA_25, PublicationIntent.SAVE_COMPLETED);

    // Act
    var request = arguments.toAnalysisRequest();

    // Assert
    assertEquals(sourceRoot.toAbsolutePath().normalize(), ((SourceInput.Root) request.input()).path());
    assertSame(JavaRelease.JAVA_25, request.configuration().release());
    assertSame(PublicationIntent.SAVE_COMPLETED, request.publicationIntent());
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
