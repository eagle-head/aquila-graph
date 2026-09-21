package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import org.aquilagraph.application.RepositorySavePolicy;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;

class AnalyzeCommandParserTest {
  private final AnalyzeCommandParser parser = new AnalyzeCommandParser(JavaRelease.JAVA_25);

  @Test
  void givenSourceRootWithoutOptions_whenArgumentsAreParsed_thenDefaultsAreApplied() {
    // Arrange
    var arguments = new String[] {"source"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var parsed = assertInstanceOf(AnalyzeCommandParser.Parsed.class, result);
    assertEquals(Path.of("source"), parsed.arguments().sourceRoot());
    assertSame(JavaRelease.JAVA_25, parsed.arguments().release());
    assertSame(RepositorySavePolicy.DO_NOT_SAVE, parsed.arguments().repositorySavePolicy());
  }

  @Test
  void givenSupportedOptionsInAnyOrder_whenArgumentsAreParsed_thenExplicitValuesAreApplied() {
    // Arrange
    var arguments = new String[] {"--persist", "source", "--release", "25"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var parsed = assertInstanceOf(AnalyzeCommandParser.Parsed.class, result);
    assertEquals(Path.of("source"), parsed.arguments().sourceRoot());
    assertSame(JavaRelease.JAVA_25, parsed.arguments().release());
    assertSame(RepositorySavePolicy.SAVE_COMPLETED, parsed.arguments().repositorySavePolicy());
  }

  @Test
  void givenReleaseFollowedByOption_whenArgumentsAreParsed_thenMissingReleaseValueIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--release", "--persist"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals("--release requires a Java feature-release number", rejected.message());
  }

  @Test
  void givenDuplicatePersistenceOption_whenArgumentsAreParsed_thenDuplicateIsRejected() {
    // Arrange
    var arguments = new String[] {"source", "--persist", "--persist"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals("--persist may be supplied only once", rejected.message());
  }

  @Test
  void givenUnknownOption_whenArgumentsAreParsed_thenUnknownOptionIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--unknown"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals("Unknown option: --unknown", rejected.message());
  }

  @Test
  void givenInvalidPlatformPath_whenArgumentsAreParsed_thenInvalidSourceRootIsReported() {
    // Arrange
    var invalidPath = "\0";

    // Act
    var result = parser.parse(new String[] {invalidPath});

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals("Invalid source root: " + invalidPath, rejected.message());
  }

  @Test
  void givenMultipleSourceRoots_whenArgumentsAreParsed_thenCardinalityIsRejected() {
    // Arrange
    var arguments = new String[] {"first", "second"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals("Exactly one source root is required", rejected.message());
  }
}
