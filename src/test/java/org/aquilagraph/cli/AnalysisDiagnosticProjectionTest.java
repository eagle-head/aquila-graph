package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.cli.AnalysisDiagnosticProjection.DiagnosticSummary;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class AnalysisDiagnosticProjectionTest {
  @TestFactory
  Stream<DynamicTest> givenUnsupportedDeclarationWithinExceptionClauses_whenPresented_thenEachSourceCauseRemainsOnceInWrittenOrder() {
    return Stream.of(
      "try { class Local {} } finally {}",
      "try {} catch (RuntimeException failure) { class Local {} } finally {}",
      "try {} finally { class Local {} }",
      "try (input) { class Local {} }",
      "try (AutoCloseable local = input) { class Local {} } finally {}")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var source = "class Example { void subject(AutoCloseable input) throws Exception { " + statement + " } }";
          var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
          var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var nativeBounded = bounded.diagnostics();
          var nativeExpanded = expanded.diagnostics();
          var boundedAst = bounded.representation().ast().result().orElseThrow();
          var boundedCfg = bounded.representation().cfg().orElseThrow();
          // Act
          var projected = AnalysisRunSummary.from(bounded).diagnostics();
          var expandedProjection = AnalysisRunSummary.from(expanded).diagnostics();
          // Assert
          assertEquals(List.of("Unsupported Java construct in method body: TRY", "Unsupported Java construct in method body: CLASS"),
              projected.stream().map(DiagnosticSummary::message).toList());
          assertEquals(List.of("Unsupported Java construct in method body: CLASS"),
              boundedAst.diagnostics().stream().map(value -> value.message()).toList());
          assertEquals(nativeExpanded.stream().map(DiagnosticSummary::from).toList(), expandedProjection);
          assertEquals(nativeBounded, bounded.diagnostics());
          assertEquals(nativeExpanded, expanded.diagnostics());
          assertSame(boundedAst, bounded.representation().ast().result().orElseThrow());
          assertSame(boundedCfg, bounded.representation().cfg().orElseThrow());
          assertEquals(AstCompletion.INCOMPLETE, boundedAst.completion());
          assertEquals(AstCompletion.INCOMPLETE, expanded.representation().ast().completion());
        }));
  }

  @Test
  void givenUnsupportedDeclarationWithinNestedTries_whenPresented_thenEachEnclosingAndDescendantCauseKeepsItsOrder() throws IOException {
    // Arrange
    var run = analyze("class Example { void subject() { try { try {} finally { class Local {} } } finally {} } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(run).diagnostics();
    // Assert
    assertEquals(List.of("Unsupported Java construct in method body: TRY", "Unsupported Java construct in method body: TRY",
        "Unsupported Java construct in method body: CLASS"), projected.stream().map(DiagnosticSummary::message).toList());
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @Test
  void givenExceptionClauseWithinHistoricalIterationBoundary_whenPresented_thenTheOuterStatementStillOwnsTheProjection() throws IOException {
    // Arrange
    var run = analyze("class Example { void subject(boolean flag) { while (flag) { try { class Local {} } finally {} } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(run).diagnostics();
    // Assert
    assertEquals(List.of("Java compiler tree kind WHILE_LOOP is outside the supported semantic graph profile",
        "Unsupported Java construct in method body: WHILE_LOOP"), projected.stream().map(DiagnosticSummary::message).toList());
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @Test
  void givenNestedExceptionSyntaxInBothProfiles_whenPresented_thenHistoricalTryAndNestedLoopDiagnosticsStayInTheConsoleBoundary() throws IOException {
    // Arrange
    var source = "class Example { void value(AutoCloseable input, boolean flag) throws Exception { try (input) { try { while(flag) {} } finally {} } finally {} } }";
    var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var originalBounded = bounded.diagnostics();
    var originalExpanded = expanded.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(bounded).diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).diagnostics();
    // Assert
    assertEquals(List.of("Java compiler tree kind WHILE_LOOP is outside the supported semantic graph profile",
        "Unsupported Java construct in method body: TRY", "Unsupported Java construct in method body: TRY",
        "Unsupported Java construct in method body: WHILE_LOOP"), projected.stream().map(DiagnosticSummary::message).toList());
    assertEquals(originalExpanded.stream().map(DiagnosticSummary::from).toList(), expandedProjection);
    assertEquals(originalBounded, bounded.diagnostics());
    assertEquals(originalExpanded, expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
    assertEquals(AstCompletion.INCOMPLETE, bounded.representation().ast().completion());
  }

  @Test
  void givenMultiCatchInBothProfiles_whenPresented_thenHistoricalUnionDiagnosticDoesNotBecomeANativeSourceError() throws IOException {
    // Arrange
    var source = "class Example { void value() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }";
    var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    // Act
    var projected = AnalysisRunSummary.from(bounded).diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).diagnostics();
    // Assert
    assertEquals(List.of("frontend.unsupported-tree-kind.union-type"), projected.stream().map(DiagnosticSummary::code).toList());
    assertEquals(List.of(), bounded.diagnostics());
    assertEquals(List.of(), expanded.diagnostics());
    assertEquals(List.of(), expandedProjection);
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
    assertEquals(AnalysisCompletion.COMPLETE, expanded.completion());
  }

  @Test
  void givenNestedAssertionsAndMonitor_whenBoundedPresentationRuns_thenCatalogAndOuterBodyBoundariesRemainDistinct() throws IOException {
    // Arrange
    var run = analyze("class Example { void value(Object lock, boolean flag) { assert flag; synchronized (lock) { assert flag; } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(run).diagnostics();
    // Assert
    assertEquals(List.of(
        "Java compiler tree kind ASSERT is outside the supported semantic graph profile",
        "Java compiler tree kind SYNCHRONIZED is outside the supported semantic graph profile",
        "Java compiler tree kind ASSERT is outside the supported semantic graph profile",
        "Unsupported Java construct in method body: ASSERT",
        "Unsupported Java construct in method body: SYNCHRONIZED"), projected.stream().map(DiagnosticSummary::message).toList());
    assertEquals(nativeDiagnostics, run.diagnostics());
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
  }

  @Test
  void givenNestedIterationInBothProfiles_whenPresented_thenOnlyBoundedPresentationKeepsTheHistoricalStatementBoundary() throws IOException {
    // Arrange
    var source = "class Example { void value(int n) { outer: while (n > 0) { for (int i = 0; i < n; i++) { continue outer; } break; } } }";
    var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var originalBounded = bounded.diagnostics();
    var originalExpanded = expanded.diagnostics();
    // Act
    var boundedProjection = AnalysisRunSummary.from(bounded).diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).diagnostics();
    // Assert
    assertEquals(List.of("frontend.unsupported-tree-kind.labeled-statement", "frontend.unsupported-tree-kind.while-loop",
        "frontend.unsupported-tree-kind.for-loop", "frontend.unsupported-tree-kind.continue", "frontend.unsupported-tree-kind.break",
        "frontend.unsupported-syntax"), boundedProjection.stream().map(DiagnosticSummary::code).toList());
    assertEquals("Unsupported Java construct in method body: LABELED_STATEMENT", boundedProjection.getLast().message());
    assertEquals(originalExpanded.stream().map(DiagnosticSummary::from).toList(), expandedProjection);
    assertEquals(originalBounded, bounded.diagnostics());
    assertEquals(originalExpanded, expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
  }

  @Test
  void givenRealCompilerErrorWithinAnIteration_whenPresented_thenEveryGenuineSourceErrorRemainsVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { void value() { while (missing) { } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var genuine = run.representation().ast().diagnostics().stream().filter(diagnostic -> !diagnostic.code().startsWith("frontend.unsupported-")).toList();
    // Act
    var projection = AnalysisRunSummary.from(run).diagnostics();
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
    assertEquals(true, genuine.stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    for (var diagnostic : genuine) {
      assertEquals(run.representation().ast().diagnostics().stream().filter(diagnostic::equals).count(),
          projection.stream().filter(summary -> summary.equals(DiagnosticSummary.from(diagnostic))).count());
    }
  }

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
