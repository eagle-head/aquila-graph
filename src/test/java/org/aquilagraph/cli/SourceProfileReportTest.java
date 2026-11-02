package org.aquilagraph.cli;

import static org.aquilagraph.cli.NativeReportAssertions.assertNativeReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class SourceProfileReportTest {
  @Test
  void givenClassLiteralOutsideTheBoundedCfgProfile_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { Class<?> subject() { return String.class; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection, runStreams);
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @TestFactory
  Stream<DynamicTest> givenNonassignableSourceTargets_whenPresented_thenActualCompilerAndGraphRefusalsRemainVisible() {
    return Stream.of(
        new InvalidAssignment("Literal", "void subject() { 1 = 2; }"),
        new InvalidAssignment("Invocation", "int value() { return 1; } void subject() { value() = 2; }"))
        .map(example -> DynamicTest.dynamicTest("given" + example.context() + "AssignmentTarget_whenPresented_thenNativeRefusalsRemain", () -> {
          // Arrange
          var run = CliTestFixtures.analyze("class Example { " + example.methods() + " }", AstProfile.JAVA_SOURCE_BOUNDED);
          var nativeDiagnostics = run.diagnostics();

          var runStreams = new CliStreams();

          // Act
          CliTestFixtures.present(run, runStreams);
          var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

          // Assert
          assertNativeReport(run, projection, runStreams);
          assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
          assertEquals(nativeDiagnostics, run.diagnostics());
        }));
  }

  @Test
  void givenMalformedBinaryExpression_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { int subject() { return 1 + ; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var diagnostics = run.representation().ast().diagnostics();
    var capability = diagnostics.stream().filter(value -> value.code().equals("frontend.unsupported-tree-kind.erroneous"))
        .findFirst().orElseThrow();

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection, runStreams);
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
    assertTrue(diagnostics.stream().anyMatch(value -> value.code().startsWith("compiler.")));
  }

  @TestFactory
  Stream<DynamicTest> givenBoundedSwitchLabels_whenPresented_thenSupportedCasesRemainFreeOfRefusals() {
    return Stream.of(
        new SwitchExample("NumericLabels", "int subject(int value) { return switch (value) { case 1, 2 -> 3; default -> 4; }; }"),
        new SwitchExample("StringLabels", "int subject(String value) { return switch (value) { case \"a\", \"b\" -> 1; default -> 2; }; }"),
        new SwitchExample("EnumLabels", "int subject(RetentionPolicy value) { return switch (value) { case SOURCE, CLASS -> 1; case RUNTIME -> 2; }; }"),
        new SwitchExample("QualifiedEnumLabel", "int subject(Object value) { return switch (value) { case RetentionPolicy.SOURCE -> 1; default -> 2; }; }"))
        .map(example -> DynamicTest.dynamicTest("given" + example.context() + "_whenPresented_thenNoRefusalsRemain", () -> {
          // Arrange
          var run = CliTestFixtures.analyze("import java.lang.annotation.RetentionPolicy; class Example { " + example.method() + " }",
              AstProfile.JAVA_SOURCE_BOUNDED);

          var runStreams = new CliStreams();

          // Act
          CliTestFixtures.present(run, runStreams);
          var summary = AnalysisRunSummary.from(run);

          // Assert
          assertNativeReport(run, summary.occurrences().diagnostics(), runStreams);
          assertEquals(AnalysisCompletion.COMPLETE, summary.run().completion());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenExpandedSwitchExpressionLabels_whenPresented_thenEachUnsupportedCaseKeepsItsBoundedRefusal() {
    return Stream.of(
        new SwitchExample("StatementGroups", "int subject(int value) { return switch (value) { case 1: yield 2; default: yield 3; }; }"),
        new SwitchExample("ConstantExpression", "int subject(int value) { return switch (value) { case 1 + 2 -> 3; default -> 4; }; }"),
        new SwitchExample("NullLabel", "int subject(String value) { return switch (value) { case null -> 0; default -> 1; }; }"),
        new SwitchExample("LocalConstant", "int subject(int value) { final int label = 1; return switch (value) { case label -> 2; default -> 3; }; }"))
        .map(example -> DynamicTest.dynamicTest("given" + example.context() + "_whenPresented_thenBoundedCaseRefusalsRemain", () -> {
          // Arrange
          var run = CliTestFixtures.analyze("class Example { " + example.method() + " }", AstProfile.JAVA_SOURCE_BOUNDED);
          var nativeDiagnostics = run.diagnostics();

          var runStreams = new CliStreams();

          // Act
          CliTestFixtures.present(run, runStreams);
          var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

          // Assert
          assertNativeReport(run, projection, runStreams);
          assertEquals(nativeDiagnostics, run.diagnostics());
        }));
  }

  @Test
  void givenGuardedSwitchExpression_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { int subject(Object value) { return switch (value) { "
        + "case String text when text.length() > 0 -> text.length(); default -> 0; }; } }", AstProfile.JAVA_SOURCE_BOUNDED);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection, runStreams);
  }

  @Test
  void givenAstOnlyRequestWithSourceErrors_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { int subject() { return missing; } }")));
    var runner = CliTestFixtures.runner();
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE);
    var run = runner.analyze(request);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection, runStreams);
    assertTrue(run.representation().cfg().isEmpty());
  }

  @Test
  void givenAlternativeCfgProfile_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { int subject(int value) { return switch (value) { case 1 -> 2; default -> 3; }; } }")));
    var runner = CliTestFixtures.runner();
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS)),
        PublicationIntent.DO_NOT_SAVE);
    var run = runner.analyze(request);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection, runStreams);
  }

  @Test
  void givenMalformedExpressionBesideIteration_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { int subject(boolean flag) { while (flag) {} return (); } }",
        AstProfile.JAVA_SOURCE_BOUNDED);
    var sourceDiagnostics = run.representation().ast().diagnostics();
    var capability = sourceDiagnostics.stream()
        .filter(value -> value.code().equals("frontend.unsupported-tree-kind.erroneous")).findFirst().orElseThrow();
    var compilerErrors = sourceDiagnostics.stream().filter(value -> value.code().startsWith("compiler."))
        .toList();

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection, runStreams);
    assertFalse(compilerErrors.isEmpty());
    assertEquals(sourceDiagnostics, run.representation().ast().diagnostics());
  }

  @TestFactory
  Stream<DynamicTest> givenUnsupportedDeclarationWithinExceptionClauses_whenPresented_thenEachSourceCauseRemainsOnceInWrittenOrder() {
    return Stream.of(
      "try { class Local {} } finally {}",
      "try {} catch (RuntimeException failure) { class Local {} } finally {}",
      "try {} finally { class Local {} }",
      "try (input) { class Local {} }",
      "try (AutoCloseable local = input) { class Local {} } finally {}")
        .map(statement -> DynamicTest.dynamicTest("givenUnsupportedLocalDeclarationIn" + statement + "_whenPresented_thenNativeEventsAndReasonsRemainVisible", () -> {
          // Arrange
          var source = "class Example { void subject(AutoCloseable input) throws Exception { " + statement + " } }";
          var bounded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
          var expanded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var nativeBounded = bounded.diagnostics();
          var nativeExpanded = expanded.diagnostics();
          var boundedAst = bounded.representation().ast().result().orElseThrow();
          var boundedCfg = bounded.representation().cfg().orElseThrow();
          var boundedStreams = new CliStreams();
          var expandedStreams = new CliStreams();

          // Act
          CliTestFixtures.present(bounded, boundedStreams);
          CliTestFixtures.present(expanded, expandedStreams);
          var projected = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
          var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
          // Assert
          assertNativeReport(bounded, projected, boundedStreams);
          assertNativeReport(expanded, expandedProjection, expandedStreams);
          assertEquals(List.of("Unsupported Java construct in method body: CLASS"),
              boundedAst.diagnostics().stream().map(value -> value.message()).toList());
          assertEquals(nativeBounded, bounded.diagnostics());
          assertEquals(nativeExpanded, expanded.diagnostics());
          assertSame(boundedAst, bounded.representation().ast().result().orElseThrow());
          assertSame(boundedCfg, bounded.representation().cfg().orElseThrow());
          assertEquals(AstCompletion.INCOMPLETE, boundedAst.completion());
          assertEquals(AstCompletion.INCOMPLETE, expanded.representation().ast().completion());
        }));
  }

  @Test
  void givenUnsupportedDeclarationWithinNestedTries_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { void subject() { try { try {} finally { class Local {} } } finally {} } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projected = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projected, runStreams);
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @Test
  void givenExceptionClauseWithinBoundedIterationBoundary_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { void subject(boolean flag) { while (flag) { try { class Local {} } finally {} } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projected = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projected, runStreams);
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @Test
  void givenNestedExceptionSyntaxInBothProfiles_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { void value(AutoCloseable input, boolean flag) throws Exception { try (input) { try { while(flag) {} } finally {} } finally {} } }";
    var bounded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var originalBounded = bounded.diagnostics();
    var originalExpanded = expanded.diagnostics();
    var boundedStreams = new CliStreams();
    var expandedStreams = new CliStreams();

    // Act
    CliTestFixtures.present(bounded, boundedStreams);
    CliTestFixtures.present(expanded, expandedStreams);
    var projected = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
    // Assert
    assertNativeReport(bounded, projected, boundedStreams);
    assertNativeReport(expanded, expandedProjection, expandedStreams);
    assertEquals(originalBounded, bounded.diagnostics());
    assertEquals(originalExpanded, expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
    assertEquals(AstCompletion.INCOMPLETE, bounded.representation().ast().completion());
  }

  @Test
  void givenMultiCatchInBothProfiles_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { void value() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }";
    var bounded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var boundedStreams = new CliStreams();
    var expandedStreams = new CliStreams();

    // Act
    CliTestFixtures.present(bounded, boundedStreams);
    CliTestFixtures.present(expanded, expandedStreams);
    var projected = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
    // Assert
    assertNativeReport(bounded, projected, boundedStreams);
    assertNativeReport(expanded, expandedProjection, expandedStreams);
    assertEquals(List.of(), bounded.diagnostics());
    assertEquals(List.of(), expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
    assertEquals(AnalysisCompletion.COMPLETE, expanded.completion());
  }

  @Test
  void givenNestedAssertionsAndMonitor_whenBoundedPresentationRuns_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { void value(Object lock, boolean flag) { assert flag; synchronized (lock) { assert flag; } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projected = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projected, runStreams);
    assertEquals(nativeDiagnostics, run.diagnostics());
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
  }

  @Test
  void givenNestedIterationInBothProfiles_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { void value(int n) { outer: while (n > 0) { for (int i = 0; i < n; i++) { continue outer; } break; } } }";
    var bounded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = CliTestFixtures.analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var originalBounded = bounded.diagnostics();
    var originalExpanded = expanded.diagnostics();
    var boundedStreams = new CliStreams();
    var expandedStreams = new CliStreams();

    // Act
    CliTestFixtures.present(bounded, boundedStreams);
    CliTestFixtures.present(expanded, expandedStreams);
    var boundedProjection = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
    // Assert
    assertNativeReport(bounded, boundedProjection, boundedStreams);
    assertNativeReport(expanded, expandedProjection, expandedStreams);
    assertEquals(originalBounded, bounded.diagnostics());
    assertEquals(originalExpanded, expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
  }

  @Test
  void givenRealCompilerErrorWithinAnIteration_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("class Example { void value() { while (missing) { } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var genuine = run.representation().ast().diagnostics().stream().filter(diagnostic -> !diagnostic.code().startsWith("frontend.unsupported-")).toList();
    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projection, runStreams);
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
    assertEquals(true, genuine.stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    for (var diagnostic : genuine) {
      assertEquals(run.representation().ast().diagnostics().stream().filter(diagnostic::equals).count(),
          projection.stream().filter(diagnostic::equals).count());
    }
  }

  @Test
  void givenBoundedAndExpandedProfiles_whenRepeatedRefusalsArePresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { int value(int a) { (a) = 1; (a) = 2; return a; } }";
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var run = CliTestFixtures.analyze(source, profile);
      var ast = run.representation().ast().result().orElseThrow();
      var cfg = run.representation().cfg().orElseThrow();
      var nativeDiagnostics = run.diagnostics();

      var runStreams = new CliStreams();

      // Act
      CliTestFixtures.present(run, runStreams);
      var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

      // Assert
      assertNativeReport(run, projection, runStreams);
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertEquals(List.of(), ast.diagnostics());
      assertSame(ast, run.representation().ast().result().orElseThrow());
      assertSame(cfg, run.representation().cfg().orElseThrow());
      assertEquals(nativeDiagnostics, run.diagnostics());
      assertEquals(List.of("control-flow.unsupported", "control-flow.unsupported"), nativeDiagnostics.stream().map(value -> value.code()).toList());
    }
  }

  @Test
  void givenNestedTargetsInMultipleBodies_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("""
        class Example {
          int first(int a) { if (a > 0) { (a) = 1; } return a; }
          int second(int a) { while (a > 0) { a = 0; } (a) = 2; return a; }
        }
        """, AstProfile.JAVA_SOURCE_BOUNDED);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection, runStreams);
  }

  private record SwitchExample(String context, String method) {}

  private record InvalidAssignment(String context, String methods) {}
}
