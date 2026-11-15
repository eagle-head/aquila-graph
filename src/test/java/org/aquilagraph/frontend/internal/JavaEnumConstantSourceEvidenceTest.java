package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.Test;

class JavaEnumConstantSourceEvidenceTest {
  @Test
  void givenCompleteBoundedConstants_whenArgumentFormIsRead_thenTriviaAndDelimitersRetainTheirMeaning() {
    // Arrange
    var examples = List.of(new Example("VALUE", false), new Example("VALUE()", true),
        new Example("VALUE {}", false), new Example("VALUE, NEXT", false), new Example("VALUE;", false),
        new Example("VALUE /* () */ ()", true), new Example("VALUE // ()\r\n {}", false),
        new Example("VALUE\t\f\r\n ()", true),
        new Example("V" + escape("0000") + "ALUE " + escape("0028") + escape("0029"), true),
        new Example(escape("d801") + escape("dc00") + " /* ) */ ()", true),
        new Example("\\uuuu0056ALUE " + escape("002f") + escape("002a") + " () */ ()", true));

    for (var example : examples) {
      // Act
      var result = read(example.source(), 0, example.source().length(), 0);

      // Assert
      assertEquals(Optional.of(example.arguments()), result, example.source());
    }
  }

  @Test
  void givenIncompleteOrInvalidEvidence_whenArgumentFormIsRead_thenUncertaintyIsPreserved() {
    // Arrange
    var examples = List.of("VALUE /*", "VALUE /* ()", "VALUE /", "VALUE +", "VALUE\u00a0()",
        "VALUE\u2003()", "VALUE " + escape("00xz"), "VALUE " + escape("00"),
        "VALUE " + escape("００２８"), "1VALUE()", "VALUE \\u005cu0028");

    for (var source : examples) {
      // Act
      var result = read(source, 0, source.length(), 0);

      // Assert
      assertTrue(result.isEmpty(), source);
    }
  }

  @Test
  void givenSplitRawBoundaries_whenArgumentFormIsRead_thenEscapesIdentifiersAndCommentsAreNotInvented() {
    // Arrange
    var identifier = escape("0056") + "ALUE";
    var source = identifier + " /* comment */ " + escape("0028") + ")";
    var supplementary = escape("d801") + escape("dc00") + "()";

    // Act
    var splitEscapeStart = read(source, 0, source.length(), 2);
    var splitEscapeEnd = read(source, 0, 3, 0);
    var splitIdentifier = read(source, 0, identifier.length() - 1, 0);
    var splitComment = read(source, 0, source.indexOf("*/") + 1, 0);
    var splitDelimiter = read(source, 0, source.length() - 3, 0);
    var splitSurrogate = read(supplementary, 0, 6, 0);
    var afterIdentifier = read(source, 0, identifier.length(), 0);

    // Assert
    assertEquals(List.of(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
        List.of(splitEscapeStart, splitEscapeEnd, splitIdentifier, splitComment, splitDelimiter, splitSurrogate));
    assertEquals(Optional.of(false), afterIdentifier);
  }

  @Test
  void givenOutOfRangeOffsetsAndPriorEscapeContext_whenArgumentFormIsRead_thenCapturedUnitBoundariesRemainAuthoritative() {
    // Arrange
    var source = "VALUE()";
    var ineligible = "\\\\u0056ALUE()";

    // Act
    var outside = List.of(read(source, 1, source.length(), 0), read(source, 0, source.length(), -1),
        read(source, 0, source.length(), source.length()), read(source, 0, source.length() + 1, 0));
    var resetWouldInventIdentifier = read(ineligible, 1, ineligible.length(), 1);

    // Assert
    assertTrue(outside.stream().allMatch(Optional::isEmpty));
    assertTrue(resetWouldInventIdentifier.isEmpty());
  }

  private static Optional<Boolean> read(String content, int start, int end, long identifierStart) {
    var source = SourceFile.fromContent(capturedPath(Path.of("Example.java")), content);
    var tokens = JavaSourceTokens.capture(content);
    return JavaEnumConstantSourceEvidence.argumentList(tokens.source(), new SourceSpan(source.sourceFileKey(), start, end), identifierStart);
  }

  private static String escape(String digits) {
    return "\\" + "u" + digits;
  }

  private record Example(String source, boolean arguments) {
  }
}
