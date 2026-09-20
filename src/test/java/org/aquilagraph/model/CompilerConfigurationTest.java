package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompilerConfigurationTest {
  @Test
  void givenMutableCompilerInputs_whenConfigurationIsCreated_thenOwnershipIsImmutable() {
    // Arrange
    var classPath = new ArrayList<Path>();
    classPath.add(Path.of("library.jar"));
    var options = new ArrayList<String>();
    options.add("-Xlint:all");

    // Act
    var configuration = new CompilerConfiguration(25, classPath, List.of(), options);
    classPath.clear();
    options.clear();

    // Assert
    assertEquals(1, configuration.classPath().size());
    assertEquals(List.of("-Xlint:all"), configuration.options());
    assertThrows(
        UnsupportedOperationException.class,
        () -> configuration.options().add("-Xlint:none"));
  }

  @Test
  void givenManagedCompilerOption_whenConfigurationIsCreated_thenOptionIsRejected() {
    // Arrange
    var options = List.of("--release=24");

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CompilerConfiguration(25, List.of(), List.of(), options));

    // Assert
    assertEquals("Compiler option is managed by Aquila Graph: --release=24", exception.getMessage());
  }

  @Test
  void givenUncapturedCompilerInputOption_whenConfigurationIsCreated_thenOptionIsRejected() {
    // Arrange
    var options =
        List.of("--class-path=dependencies", "--source-path=generated", "@compiler-options");

    for (var option : options) {
      // Act
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new CompilerConfiguration(25, List.of(), List.of(), List.of(option)));

      // Assert
      assertEquals("Compiler option is managed by Aquila Graph: " + option, exception.getMessage());
    }
  }
}
