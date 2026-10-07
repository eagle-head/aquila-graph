package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.aquilagraph.identity.api.JavaRelease;
import org.junit.jupiter.api.Test;

class JavaReleaseTest {
  @Test
  void givenSupportedFeature_whenReleaseIsResolved_thenJava25IsReturned() {
    // Arrange
    var feature = 25;

    // Act
    var release = JavaRelease.fromFeature(feature);

    // Assert
    assertEquals(JavaRelease.JAVA_25, release);
    assertEquals(feature, release.feature());
  }

  @Test
  void givenUnsupportedFeature_whenReleaseIsResolved_thenSupportedFeaturesAreReported() {
    // Arrange
    var feature = 26;

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> JavaRelease.fromFeature(feature));

    // Assert
    assertEquals("Unsupported Java release: 26. Supported releases: 25", exception.getMessage());
  }
}
