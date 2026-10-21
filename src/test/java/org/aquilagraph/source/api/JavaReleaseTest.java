package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.aquilagraph.identity.api.JavaRelease;
import org.junit.jupiter.api.Test;

class JavaReleaseTest {
  @Test
  void givenSupportedAndUnsupportedFeatureNumbers_whenSupportIsQueried_thenOnlyTheDeclaredReleaseIsSupported() {
    // Arrange
    var features = List.of(-1, 0, 24, 25, 26, Integer.MAX_VALUE);

    // Act
    var supported = features.stream().map(JavaRelease::supportsFeature).toList();

    // Assert
    assertEquals(List.of(false, false, false, true, false, false), supported);
  }

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
