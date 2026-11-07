package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.nio.file.Path;
import org.aquilagraph.cli.ReportDiagnostic.Evidence;
import org.aquilagraph.cli.ReportDiagnostic.Link;
import org.aquilagraph.cli.ReportDiagnostic.Origin;
import org.aquilagraph.cli.ReportDiagnostic.Value;
import org.aquilagraph.cli.ReportDocument.Node;
import org.aquilagraph.cli.ReportDocument.Section;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;

class DiagnosticDocumentComposerTest {
  @Test
  void givenDiagnosticWithDelimitersControlsAndUnicode_whenNativeEventIsRendered_thenEachFieldStaysOnOneLine() {
    // Arrange
    var value = new Value("frontend\"\\entry", DiagnosticSeverity.WARNING, DiagnosticStage.FRONTEND,
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, "compiler\tcode\nnext",
        "literal \\n, actual \r\n, \u001b\u001f ~\u007f, caf\u00e9 \ud83d\ude80", List.of());
    var event = new ReportDiagnostic(12, new ReportDiagnostic.Owner.Application(CliTestFixtures.rejectedRun().identity()), 5, value);

    // Act
    var lines = render(new DiagnosticDocumentComposer().nativeEvent(event));

    // Assert
    assertEquals(List.of("Diagnostic: \"frontend\\\"\\\\entry\"", "  Occurrence: 12", "  Owner: APPLICATION", "  Native position: 5",
        "  Severity: WARNING", "  Stage: FRONTEND", "  Cause: OTHER_ANALYSIS_ERROR", "  Code: \"compiler\\tcode\\nnext\"",
        "  Message: \"literal \\\\n, actual \\r\\n, \\u001b\\u001f ~\\u007f, caf\u00e9 \ud83d\ude80\"",
        "  Origins: unavailable"), lines);
  }

  @Test
  void givenEvidenceFromSameNamedFilesAndUncapturedRevision_whenRendered_thenOrderedPathAvailabilityAndRoleRemainExplicit() {
    // Arrange
    var left = SourceFile.fromContent(Path.of("left sources", "Entry.java"), "package left; class Entry {}");
    var right = SourceFile.fromContent(Path.of("right sources", "Entry.java"), "package right; class Entry {}");
    var uncaptured = SourceFile.fromContent(left.relativePath(), "package left; class Entry { int value; }");
    var origins = List.of(new Origin(new SourceSpan(right.sourceFileKey(), 0, 0), Optional.of(right.relativePath().toString())),
        new Origin(new SourceSpan(uncaptured.sourceFileKey(), 1, 2), Optional.empty()),
        new Origin(new SourceSpan(left.sourceFileKey(), 1, 3), Optional.of(left.relativePath().toString())));
    var evidence = new Evidence(Optional.empty(), new Value("frontend:evidence", DiagnosticSeverity.ERROR, DiagnosticStage.FRONTEND,
        AnalysisOutcomeKind.COMPILER_ERROR, "compiler.evidence", "producer \"evidence\"", origins));
    var leftPath = left.relativePath().toString().replace("\\", "\\\\");
    var rightPath = right.relativePath().toString().replace("\\", "\\\\");

    // Act
    var lines = render(new DiagnosticDocumentComposer().evidence(evidence));

    // Assert
    assertEquals(List.of("Evidence diagnostic: \"frontend:evidence\"", "  Evidence role: not-applicable", "  Severity: ERROR", "  Stage: FRONTEND",
        "  Cause: COMPILER_ERROR", "  Code: \"compiler.evidence\"", "  Message: \"producer \\\"evidence\\\"\"",
        "  Origin: \"" + right.sourceFileKey() + "\" file=\"" + rightPath + "\" range=[0,0)",
        "  Origin: \"" + uncaptured.sourceFileKey() + "\" file=unavailable range=[1,2)",
        "  Origin: \"" + left.sourceFileKey() + "\" file=\"" + leftPath + "\" range=[1,3)"), lines);
  }

  @Test
  void givenRelatedDiagnosticKeyWithQuoteAndNewline_whenRendered_thenEscapedKeyAndLocalReferenceShareOneLine() {
    // Arrange
    var value = new Value("source\n\"entry", DiagnosticSeverity.INFO, DiagnosticStage.INPUT,
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, "source.notice", "Source notice", List.of());
    var event = new ReportDiagnostic(1, new ReportDiagnostic.Owner.Application(CliTestFixtures.rejectedRun().identity()), 0, value);

    // Act
    var lines = render(DiagnosticDocumentComposer.links(List.of(new Link(1, Optional.empty())), List.of(event), false));

    // Assert
    assertEquals(List.of("Related diagnostic: \"source\\n\\\"entry\" occurrence=1"), lines);
  }

  @Test
  void givenUnresolvedOriginKeyWithDelimiters_whenRendered_thenEscapedKeyAndHalfOpenRangeAreRetained() {
    // Arrange
    var origin = new Origin(new SourceSpan("source\n\"entry", 2, 5), Optional.empty());

    // Act
    var lines = render(DiagnosticDocumentComposer.origins(List.of(origin)));

    // Assert
    assertEquals(List.of("Origin: \"source\\n\\\"entry\" file=unavailable range=[2,5)"), lines);
  }

  private static List<String> render(Node node) {
    var document = new ReportDocument(new Section(List.of()), new Section(List.of(node)));
    return new CommandTextRenderer(System.lineSeparator()).render(document).error().lines().toList();
  }
}
