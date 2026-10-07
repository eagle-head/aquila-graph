package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.cli.AnalysisDiagnosticProjection.DiagnosticSummary;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

class AnalysisDiagnosticProjectionTest {
  @Test
  void givenBoundedAndExpandedProfiles_whenRepeatedRefusalsArePresented_thenOnlyBoundedConsoleUsesHistoricalLabels() throws IOException {
    // Arrange
    var source = "class Example { int value(int a) { (a) = 1; (a) = 2; return a; } }";
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var run = analyze(source, profile);
      var ast = run.representation().ast().result().orElseThrow();
      var cfg = run.representation().cfg().orElseThrow();
      var nativeDiagnostics = run.diagnostics();

      // Act
      var projection = AnalysisRunSummary.from(run).diagnostics();

      // Assert
      var expected = profile.equals(AstProfile.JAVA_SOURCE_BOUNDED)
          ? new DiagnosticSummary(DiagnosticSeverity.ERROR, "FRONTEND", "frontend.unsupported-syntax",
              "Unsupported Java construct in method body: PARENTHESIZED")
          : new DiagnosticSummary(DiagnosticSeverity.ERROR, "CONTROL_FLOW", "control-flow.unsupported",
              "Represented source construct is outside the selected CFG profile");
      assertEquals(List.of(expected, expected), projection);
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertEquals(List.of(), ast.diagnostics());
      assertSame(ast, run.representation().ast().result().orElseThrow());
      assertSame(cfg, run.representation().cfg().orElseThrow());
      assertEquals(nativeDiagnostics, run.diagnostics());
      assertEquals(List.of("control-flow.unsupported", "control-flow.unsupported"), nativeDiagnostics.stream().map(value -> value.code()).toList());
    }
  }

  @Test
  void givenNestedTargetsInMultipleBodies_whenPresented_thenCatalogPrefixAndWrittenBodyOrderArePreserved() throws IOException {
    // Arrange
    var run = analyze("""
        class Example {
          int first(int a) { if (a > 0) { (a) = 1; } return a; }
          int second(int a) { while (a > 0) { a = 0; } (a) = 2; return a; }
        }
        """, AstProfile.JAVA_SOURCE_BOUNDED);

    // Act
    var projection = AnalysisRunSummary.from(run).diagnostics();

    // Assert
    assertEquals(List.of(
        "Java compiler tree kind WHILE_LOOP is outside the supported semantic graph profile",
        "Unsupported Java construct in method body: PARENTHESIZED",
        "Unsupported Java construct in method body: WHILE_LOOP",
        "Unsupported Java construct in method body: PARENTHESIZED"), projection.stream().map(DiagnosticSummary::message).toList());
    assertEquals(List.of("FRONTEND", "FRONTEND", "FRONTEND", "FRONTEND"), projection.stream().map(DiagnosticSummary::stage).toList());
  }

  @Test
  void givenRepeatedApplicationSlotsEqualToGraphDiagnostic_whenPresented_thenApplicationOccurrencesRemainVisible() throws IOException {
    // Arrange
    var original = analyze("class Example { int value(int a) { (a) = 1; return a; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var graphDiagnostic = original.diagnostics().getFirst();
    var applicationDiagnostics = List.of(graphDiagnostic, graphDiagnostic);
    var run = new AnalysisRun(original.identity(), original.representation(), AnalysisCompletion.FAILED, applicationDiagnostics);

    // Act
    var projection = AnalysisRunSummary.from(run).diagnostics();

    // Assert
    assertEquals(List.of(new DiagnosticSummary(DiagnosticSeverity.ERROR, "FRONTEND", "frontend.unsupported-syntax",
        "Unsupported Java construct in method body: PARENTHESIZED"),
        new DiagnosticSummary(DiagnosticSeverity.ERROR, "CONTROL_FLOW", "control-flow.unsupported",
            "Represented source construct is outside the selected CFG profile"),
        new DiagnosticSummary(DiagnosticSeverity.ERROR, "CONTROL_FLOW", "control-flow.unsupported",
            "Represented source construct is outside the selected CFG profile")), projection);
    assertEquals(3, run.diagnostics().size());
    assertEquals(List.of(graphDiagnostic, graphDiagnostic), run.applicationDiagnostics());
  }

  private static AnalysisRun analyze(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
    return new SourceAnalysisFixture().analyze(ast);
  }
}
