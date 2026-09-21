package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.aquilagraph.application.RepositorySavePolicy;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AnalyzeCommandArgumentsTest {
  @Test
  void givenValidatedCommandArguments_whenConvertedToAnalysisRequest_thenDomainValuesArePreserved() {
    // Arrange
    var sourceRoot = Path.of("source");
    var arguments = new AnalyzeCommandArguments(sourceRoot, JavaRelease.JAVA_25, RepositorySavePolicy.SAVE_COMPLETED);

    // Act
    var request = arguments.toAnalysisRequest();

    // Assert
    assertEquals(sourceRoot.toAbsolutePath().normalize(), request.sourceRoot());
    assertSame(JavaRelease.JAVA_25, request.compilerConfiguration().release());
    assertSame(RepositorySavePolicy.SAVE_COMPLETED, request.repositorySavePolicy());
  }

  @Test
  void givenNullCommandArgumentComponent_whenArgumentsAreCreated_thenInvalidStateIsRejected() {
    // Arrange
    var sourceRoot = Path.of("source");

    // Act
    Executable nullSourceRoot = () -> new AnalyzeCommandArguments(null, JavaRelease.JAVA_25, RepositorySavePolicy.DO_NOT_SAVE);
    Executable nullRelease = () -> new AnalyzeCommandArguments(sourceRoot, null, RepositorySavePolicy.DO_NOT_SAVE);
    Executable nullSavePolicy = () -> new AnalyzeCommandArguments(sourceRoot, JavaRelease.JAVA_25, null);

    // Assert
    assertAll(() -> assertThrows(NullPointerException.class, nullSourceRoot),
        () -> assertThrows(NullPointerException.class, nullRelease),
        () -> assertThrows(NullPointerException.class, nullSavePolicy));
  }
}
