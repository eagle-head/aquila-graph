package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.identity.api.JavaRelease;
import org.junit.jupiter.api.Test;

class AnalyzeCommandParserTest {
  private final AnalyzeCommandParser parser = new AnalyzeCommandParser(JavaRelease.JAVA_25);

  @Test
  void givenNoSourceRoot_whenArgumentsAreParsed_thenRootCardinalityIsRejected() {
    // Arrange
    var arguments = new String[0];

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(new ArgumentRejection.RootCount(0), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

  @Test
  void givenUnsupportedRelease_whenArgumentsAreParsed_thenSupportedReleaseIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--release", "26"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(new ArgumentRejection.UnsupportedRelease("26", 26), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

  @Test
  void givenMalformedRelease_whenArgumentsAreParsed_thenOriginalTokenIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--release", "latest"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(new ArgumentRejection.MalformedRelease("latest"), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

  @Test
  void givenReleaseWithoutValue_whenArgumentsAreParsed_thenMissingValueIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--release"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(ArgumentRejection.MissingRelease.INSTANCE, assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

  @Test
  void givenDuplicateReleaseWithValues_whenArgumentsAreParsed_thenDuplicateIsRejected() {
    // Arrange
    var arguments = new String[] {"source", "--release", "25", "--release", "25"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(new ArgumentRejection.DuplicateOption("--release"), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

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
    assertSame(PublicationIntent.DO_NOT_SAVE, parsed.arguments().publicationIntent());
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
    assertSame(PublicationIntent.SAVE_COMPLETED, parsed.arguments().publicationIntent());
  }

  @Test
  void givenReleaseFollowedByOption_whenArgumentsAreParsed_thenMissingReleaseValueIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--release", "--persist"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals(ArgumentRejection.MissingRelease.INSTANCE, rejected.reason());
  }

  @Test
  void givenDuplicatePersistenceOption_whenArgumentsAreParsed_thenDuplicateIsRejected() {
    // Arrange
    var arguments = new String[] {"source", "--persist", "--persist"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals(new ArgumentRejection.DuplicateOption("--persist"), rejected.reason());
  }

  @Test
  void givenUnknownOption_whenArgumentsAreParsed_thenUnknownOptionIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--unknown"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals(new ArgumentRejection.UnknownOption("--unknown"), rejected.reason());
  }

  @Test
  void givenInvalidPlatformPath_whenArgumentsAreParsed_thenInvalidSourceRootIsReported() {
    // Arrange
    var invalidPath = "\0";

    // Act
    var result = parser.parse(new String[] {invalidPath});

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals(new ArgumentRejection.InvalidRoot(invalidPath), rejected.reason());
  }

  @Test
  void givenMultipleSourceRoots_whenArgumentsAreParsed_thenCardinalityIsRejected() {
    // Arrange
    var arguments = new String[] {"first", "second"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    var rejected = assertInstanceOf(AnalyzeCommandParser.Rejected.class, result);
    assertEquals(new ArgumentRejection.RootCount(2), rejected.reason());
  }

  @Test
  void givenEarlierUnknownOptionAndLaterNullToken_whenArgumentsAreParsed_thenWholeArrayValidationPrecedesRefusal() {
    // Arrange
    var arguments = new String[] {"--unknown", null};

    // Act
    var failure = assertThrows(NullPointerException.class, () -> parser.parse(arguments));

    // Assert
    assertEquals("argument", failure.getMessage());
  }

  @Test
  void givenTwoRootsAndLaterUnknownOption_whenArgumentsAreParsed_thenTokenRefusalPrecedesRootCardinality() {
    // Arrange
    var arguments = new String[] {"first", "second", "--unknown"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(new ArgumentRejection.UnknownOption("--unknown"), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

  @Test
  void givenDuplicateReleaseWithoutFollowingValue_whenArgumentsAreParsed_thenDuplicatePrecedesMissingValue() {
    // Arrange
    var arguments = new String[] {"source", "--release", "25", "--release"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(new ArgumentRejection.DuplicateOption("--release"), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

  @Test
  void givenOverflowReleaseNumber_whenArgumentsAreParsed_thenUnchangedTokenIsReported() {
    // Arrange
    var arguments = new String[] {"source", "--release", "2147483648"};

    // Act
    var result = parser.parse(arguments);

    // Assert
    assertEquals(new ArgumentRejection.MalformedRelease("2147483648"), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
  }

  @Test
  void givenOptionTerminatorOrEqualsForm_whenArgumentsAreParsed_thenEachExactTokenRemainsUnknown() {
    // Arrange
    var unknownTokens = new String[] {"--", "--release=25"};

    for (var unknownToken : unknownTokens) {
      // Act
      var result = parser.parse("source", unknownToken);

      // Assert
      assertEquals(new ArgumentRejection.UnknownOption(unknownToken), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
    }
  }

  @Test
  void givenSingleDashBlankOrLeadingPaddedRoot_whenArgumentsAreParsed_thenEachPositionalValueIsPreserved() {
    // Arrange
    var roots = new String[] {"-", "", " source"};

    for (var root : roots) {
      // Act
      var result = parser.parse(root);

      // Assert
      var parsed = assertInstanceOf(AnalyzeCommandParser.Parsed.class, result);
      assertEquals(Path.of(root), parsed.arguments().sourceRoot());
      assertSame(PublicationIntent.DO_NOT_SAVE, parsed.arguments().publicationIntent());
    }
  }

  @Test
  void givenReleaseAndPersistenceInRemainingOrders_whenArgumentsAreParsed_thenTheSameArgumentsAreReturned() {
    // Arrange
    var sourceRoot = "source files";
    var argumentOrders = new String[][] {
        {sourceRoot, "--persist", "--release", "25"},
        {"--release", "25", sourceRoot, "--persist"},
        {"--persist", "--release", "25", sourceRoot}
    };
    var expected = new AnalyzeCommandArguments(Path.of(sourceRoot), JavaRelease.JAVA_25, PublicationIntent.SAVE_COMPLETED);

    for (var arguments : argumentOrders) {
      // Act
      var result = parser.parse(arguments);

      // Assert
      assertEquals(expected, assertInstanceOf(AnalyzeCommandParser.Parsed.class, result).arguments(),
          String.join(" ", arguments));
    }
  }

  @Test
  void givenUnsupportedReleaseAtNumericBoundaries_whenArgumentsAreParsed_thenReleaseIsReportedAsUnsupported() {
    // Arrange
    var unsupportedFeatures = new int[] {24, 0, Integer.MIN_VALUE, Integer.MAX_VALUE};

    for (var feature : unsupportedFeatures) {
      var token = Integer.toString(feature);

      // Act
      var result = parser.parse("source", "--release", token);

      // Assert
      assertEquals(new ArgumentRejection.UnsupportedRelease(token, feature),
          assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
    }
  }

  @Test
  void givenEmptyPaddedOrUnderflowingReleaseToken_whenArgumentsAreParsed_thenUnchangedTokenIsReportedAsInvalid() {
    // Arrange
    var malformedTokens = new String[] {"", " 25 ", "-2147483649"};

    for (var token : malformedTokens) {
      // Act
      var result = parser.parse("source", "--release", token);

      // Assert
      assertEquals(new ArgumentRejection.MalformedRelease(token), assertInstanceOf(AnalyzeCommandParser.Rejected.class, result).reason());
    }
  }

  @Test
  void givenSuccessfulParseWithBothOptions_whenParserIsReused_thenEachInvocationUsesItsOwnArguments() {
    // Arrange
    var first = parser.parse("first", "--release", "25", "--persist");
    var expectedSecond = new AnalyzeCommandArguments(Path.of("second"), JavaRelease.JAVA_25, PublicationIntent.DO_NOT_SAVE);
    var expectedThird = new AnalyzeCommandArguments(Path.of("third"), JavaRelease.JAVA_25, PublicationIntent.SAVE_COMPLETED);

    // Act
    var second = parser.parse("second");
    var third = parser.parse("third", "--release", "25", "--persist");

    // Assert
    assertEquals(new AnalyzeCommandArguments(Path.of("first"), JavaRelease.JAVA_25, PublicationIntent.SAVE_COMPLETED),
        assertInstanceOf(AnalyzeCommandParser.Parsed.class, first).arguments());
    assertEquals(expectedSecond, assertInstanceOf(AnalyzeCommandParser.Parsed.class, second).arguments());
    assertEquals(expectedThird, assertInstanceOf(AnalyzeCommandParser.Parsed.class, third).arguments());
  }

  @Test
  void givenBothOptionsWithoutRoot_whenParserIsReused_thenRefusalLeavesTheNextInvocationIndependent() {
    // Arrange
    var missingRoot = parser.parse("--persist", "--release", "25");
    var expected = new AnalyzeCommandArguments(Path.of("source"), JavaRelease.JAVA_25, PublicationIntent.DO_NOT_SAVE);

    // Act
    var result = parser.parse("source");

    // Assert
    assertEquals(new ArgumentRejection.RootCount(0), assertInstanceOf(AnalyzeCommandParser.Rejected.class, missingRoot).reason());
    assertEquals(expected, assertInstanceOf(AnalyzeCommandParser.Parsed.class, result).arguments());
  }
}
