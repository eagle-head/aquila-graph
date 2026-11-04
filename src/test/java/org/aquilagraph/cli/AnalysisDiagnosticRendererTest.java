package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;

class AnalysisDiagnosticRendererTest {
  @Test
  void givenDiagnosticWithDelimitersControlsAndUnicode_whenNativeEventIsRendered_thenEachFieldStaysOnOneLine() {
    // Arrange
    var streams = new CliStreams();
    var renderer = new AnalysisDiagnosticRenderer(CliTestFixtures.rejectedRun(), streams.error());
    var diagnostic = new AnalysisDiagnostic("frontend\"\\entry", "compiler\tcode\nnext", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "literal \\n, actual \r\n, \u001b\u001f ~\u007f, caf\u00e9 \ud83d\ude80", List.of(),
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);

    // Act
    renderer.nativeEvent(diagnostic, 12);

    // Assert
    assertEquals(List.of("Diagnostic: \"frontend\\\"\\\\entry\"", "  Occurrence: 12", "  Severity: WARNING",
        "  Stage: FRONTEND", "  Cause: OTHER_ANALYSIS_ERROR", "  Code: \"compiler\\tcode\\nnext\"",
        "  Message: \"literal \\\\n, actual \\r\\n, \\u001b\\u001f ~\\u007f, caf\u00e9 \ud83d\ude80\"",
        "  Origins: unavailable"), streams.errorLines());
  }

  @Test
  void givenEvidenceFromSameNamedFilesAndAnUncapturedRevision_whenRendered_thenOriginsResolveByCapturedIdentityInOrder()
      throws IOException {
    // Arrange
    var left = SourceFile.fromContent(Path.of("left sources", "Entry.java"), "package left; class Entry {}");
    var right = SourceFile.fromContent(Path.of("right sources", "Entry.java"), "package right; class Entry {}");
    var uncaptured = SourceFile.fromContent(left.relativePath(), "package left; class Entry { int value; }");
    var ast = CliTestFixtures.produce(Path.of("."), List.of(left, right), AstProfile.JAVA_SOURCE_BOUNDED);
    var streams = new CliStreams();
    var renderer = new AnalysisDiagnosticRenderer(CliTestFixtures.analyze(ast), streams.error());
    var origins = List.of(new SourceSpan(right.sourceFileKey(), 0, 0), new SourceSpan(uncaptured.sourceFileKey(), 1, 2),
        new SourceSpan(left.sourceFileKey(), 1, 3));
    var diagnostic = new AnalysisDiagnostic("frontend:evidence", "compiler.evidence", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "producer \"evidence\"", origins, AnalysisOutcomeKind.COMPILER_ERROR);
    var leftPath = left.relativePath().toString().replace("\\", "\\\\");
    var rightPath = right.relativePath().toString().replace("\\", "\\\\");

    // Act
    renderer.evidence(diagnostic);

    // Assert
    assertEquals(List.of("  Evidence diagnostic: \"frontend:evidence\"", "    Severity: ERROR", "    Stage: FRONTEND",
        "    Cause: COMPILER_ERROR", "    Code: \"compiler.evidence\"", "    Message: \"producer \\\"evidence\\\"\"",
        "    Origin: \"" + right.sourceFileKey() + "\" file=\"" + rightPath + "\" range=[0,0)",
        "    Origin: \"" + uncaptured.sourceFileKey() + "\" file=unavailable range=[1,2)",
        "    Origin: \"" + left.sourceFileKey() + "\" file=\"" + leftPath + "\" range=[1,3)"), streams.errorLines());
  }

  @Test
  void givenRelatedDiagnosticKeyWithQuoteAndNewline_whenRendered_thenEscapedKeyAndOccurrenceShareOneLine() {
    // Arrange
    var streams = new CliStreams();
    var renderer = new AnalysisDiagnosticRenderer(CliTestFixtures.rejectedRun(), streams.error());
    var diagnostic = new AnalysisDiagnostic("source\n\"entry", "source.notice", DiagnosticStage.INPUT,
        DiagnosticSeverity.INFO, "Source notice", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);

    // Act
    renderer.related(diagnostic, 12);

    // Assert
    assertEquals(List.of("  Related diagnostic: \"source\\n\\\"entry\" occurrence=12"), streams.errorLines());
  }

  @Test
  void givenUnresolvedOriginKeyWithDelimiters_whenRendered_thenEscapedKeyAndHalfOpenRangeAreRetained() {
    // Arrange
    var streams = new CliStreams();
    var renderer = new AnalysisDiagnosticRenderer(CliTestFixtures.rejectedRun(), streams.error());
    var origin = new SourceSpan("source\n\"entry", 2, 5);

    // Act
    renderer.origins(List.of(origin), "  ");

    // Assert
    assertEquals(List.of("  Origin: \"source\\n\\\"entry\" file=unavailable range=[2,5)"), streams.errorLines());
  }
}
