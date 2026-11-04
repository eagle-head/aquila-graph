package org.aquilagraph.cli;

import static org.aquilagraph.cli.NativeReportAssertions.qualificationBlocks;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;

class AnalysisQualificationRendererTest {
  @Test
  void givenIncompleteSourceWithTwoNativeDiagnostics_whenQualificationIsRendered_thenApplicationSourceLinksBothOccurrences() {
    // Arrange
    var run = CliTestFixtures.analyze("""
        interface Example {
          static void first() { class FirstLocal {} }
          static void second() { class SecondLocal {} }
        }
        """);
    var ast = run.representation().ast();
    var sourceDiagnostics = ast.diagnostics();
    var streams = new CliStreams();
    var renderer = renderer(run, streams);

    // Act
    renderer.render();

    // Assert
    assertEquals(2, sourceDiagnostics.size());
    var sourceBlock = qualificationBlocks(streams.errorLines(), "APPLICATION").stream()
        .filter(block -> block.getFirst().equals("Qualification: APPLICATION SOURCE")).findFirst().orElseThrow();
    assertEquals(List.of(
        "  Related diagnostic: \"" + sourceDiagnostics.get(0).diagnosticKey() + "\" occurrence=1",
        "  Related diagnostic: \"" + sourceDiagnostics.get(1).diagnosticKey() + "\" occurrence=2"),
        sourceBlock.stream().filter(line -> line.startsWith("  Related diagnostic: ")).toList());
  }

  @Test
  void givenRejectedSelectionWithRepeatedDiagnostic_whenQualificationIsRendered_thenBothOwnersPreserveEveryOriginAndOccurrence() {
    // Arrange
    var ast = CliTestFixtures.completedRun().representation().ast();
    var result = ast.result().orElseThrow();
    var file = result.snapshot().sourceFiles().getFirst();
    var first = selectionDiagnostic("Unknown declaration", new SourceSpan(file.sourceFileKey(), 1, 3));
    var second = selectionDiagnostic("Duplicate declaration", new SourceSpan(file.sourceFileKey(), 4, 4));
    var request = new CfgRequest(result.identity(), List.of("unknown", "unknown"), CfgProfile.JAVA_BOUNDED);
    var runner = new SourceAnalysisRunner(ignored -> ast,
        (source, selected) -> new CfgOutcome(selected, CfgState.REJECTED, List.of(), List.of(first, second, first)));
    var run = runner.deriveExisting(ast, request);
    var streams = new CliStreams();
    var renderer = renderer(run, streams);
    var subject = "  Subject: \"" + result.identity().astResultKey() + "\"";
    var firstOrigin = "  Origin: \"" + file.sourceFileKey() + "\" file=\"Example.java\" range=[1,3)";
    var secondOrigin = "  Origin: \"" + file.sourceFileKey() + "\" file=\"Example.java\" range=[4,4)";

    // Act
    renderer.render();

    // Assert
    assertEquals(List.of(
        "Qualification: CFG REJECTED", "  Profile: \"java-cfg-bounded/1\"", subject,
        "  AST result: \"" + result.identity().astResultKey() + "\"",
        firstOrigin, secondOrigin, firstOrigin,
        "  Related diagnostic: \"cfg:selection\" occurrence=1",
        "  Related diagnostic: \"cfg:selection\" occurrence=2",
        "  Related diagnostic: \"cfg:selection\" occurrence=3",
        "Qualification: APPLICATION SELECTION", "  Profile: \"java-cfg-bounded/1\"", subject,
        firstOrigin, secondOrigin, firstOrigin,
        "  Related diagnostic: \"cfg:selection\" occurrence=1",
        "  Related diagnostic: \"cfg:selection\" occurrence=2",
        "  Related diagnostic: \"cfg:selection\" occurrence=3"), streams.errorLines());
  }

  @Test
  void givenAbsentAndEmptyFailureMessages_whenQualificationsAreRendered_thenUnavailableAndQuotedEmptyStayDistinct() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { static void run() {} }");
    var result = ast.result().orElseThrow();
    var method = result.program().methods().getFirst().methodKey();
    var request = new CfgRequest(result.identity(), List.of(method), CfgProfile.JAVA_BOUNDED);
    var absent = new SourceAnalysisRunner(ignored -> ast, RecoverableCfgFailures.atMethod(method, new IllegalStateException()));
    var empty = new SourceAnalysisRunner(ignored -> ast, RecoverableCfgFailures.atMethod(method, new IllegalStateException("")));
    var absentStreams = new CliStreams();
    var emptyStreams = new CliStreams();
    var absentRenderer = renderer(absent.deriveExisting(ast, request), absentStreams);
    var emptyRenderer = renderer(empty.deriveExisting(ast, request), emptyStreams);

    // Act
    absentRenderer.render();
    emptyRenderer.render();

    // Assert
    assertEquals(List.of("  Failure message: unavailable"),
        absentStreams.errorLines().stream().filter(line -> line.startsWith("  Failure message: ")).toList());
    assertEquals(List.of("  Failure message: \"\""),
        emptyStreams.errorLines().stream().filter(line -> line.startsWith("  Failure message: ")).toList());
  }

  private static AnalysisQualificationRenderer renderer(AnalysisRun run, CliStreams streams) {
    return new AnalysisQualificationRenderer(run, new AnalysisDiagnosticRenderer(run, streams.error()), streams.error());
  }

  private static AnalysisDiagnostic selectionDiagnostic(String message, SourceSpan origin) {
    return new AnalysisDiagnostic("cfg:selection", "control-flow.selection", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, message, List.of(origin), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
  }
}
