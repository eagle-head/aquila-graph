package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import javax.lang.model.SourceVersion;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class JavaCompilerReleaseSupportTest {
  @Test
  void givenCompilerSupportingSelectedRelease_whenSupportIsRequired_thenValidationCompletes() {
    // Arrange
    var sourceVersions = Set.of(SourceVersion.RELEASE_25);

    // Act
    Executable validation =
        () -> JavaCompilerReleaseSupport.requireSupported(sourceVersions, JavaRelease.JAVA_25);

    // Assert
    assertDoesNotThrow(validation);
  }

  @Test
  void givenCompilerMissingSelectedRelease_whenSupportIsRequired_thenAnalysisIsRejected() {
    // Arrange
    var sourceVersions = Set.of(SourceVersion.RELEASE_24);

    // Act
    var exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                JavaCompilerReleaseSupport.requireSupported(
                    sourceVersions, JavaRelease.JAVA_25));

    // Assert
    assertEquals("The system compiler does not support Java release 25", exception.getMessage());
  }
}
