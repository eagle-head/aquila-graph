package org.aquilagraph.cli;

import static org.aquilagraph.cli.NativeReportAssertions.assertNativeReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.internal.SourcePositionFixtures;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.Test;

class MissingSourcePositionReportTest {
  @Test
  void givenLocatedSourceUnitWithUnavailableBodyPosition_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var ast = SourcePositionFixtures.produce("class Example { int subject() { return 1; } }", tree -> tree.getKind() == Tree.Kind.BLOCK);
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var declaration = ast.aggregate().roots().getFirst();
    var diagnostic = new AnalysisDiagnostic("cfg:body-position", "control-flow.source-unavailable", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "The selected written body has unavailable source facts", declaration.origin().stream().toList(),
        AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var blocker = new CfgBlocker(MethodCfgState.SOURCE_UNAVAILABLE, ast.identity(), method.methodKey(), ast.occurrence(declaration),
        "body", diagnostic);
    var run = CliTestFixtures.withRefusal(ast, blocker);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().values();

    // Assert
    assertNativeReport(run, projection, runStreams);
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertTrue(ast.bodyRef(method.methodKey()).isEmpty());
    assertTrue(blocker.occurrence().isPresent());
  }

  @Test
  void givenUnpublishedBodyAfterDescendantPositionLoss_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var ast = SourcePositionFixtures.produce("class Example { void subject(boolean flag) { assert flag; while (flag) {} } }",
        tree -> tree.getKind() == Tree.Kind.WHILE_LOOP);
    var run = CliTestFixtures.analyze(ast);
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var loop = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.sourceKind().name().equals("WHILE_LOOP")).findFirst().orElseThrow();
    var assertion = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ASSERT).findFirst().orElseThrow();

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().values();

    // Assert
    var rendered = assertNativeReport(run, projection, runStreams);
    assertTrue(rendered.contains("  Source occurrence: unavailable"));
    assertTrue(loop.origin().isEmpty());
    assertTrue(ast.occurrence(loop).isEmpty());
    assertTrue(assertion.origin().isPresent());
    assertTrue(ast.occurrence(assertion).isEmpty());
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
  }

  @Test
  void givenUnsupportedSourceWithoutOccurrencePosition_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var ast = SourcePositionFixtures.produce("class Example { void subject() { class Local {} } }",
        tree -> tree instanceof ClassTree declaration && declaration.getSimpleName().contentEquals("Local"));
    var run = CliTestFixtures.analyze(ast);
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var unsupported = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED)
        .findFirst().orElseThrow();

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().values();

    // Assert
    assertNativeReport(run, projection, runStreams);
    assertTrue(unsupported.origin().isEmpty());
    assertTrue(ast.occurrence(unsupported).isEmpty());
  }

  @Test
  void givenBodyLevelRefusalForUnavailableDescendant_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = original.representation().ast().result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var root = ast.body(method.methodKey()).orElseThrow().root();
    var diagnostic = new AnalysisDiagnostic("cfg:body-source", "control-flow.source-unavailable", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "The selected body contains unavailable source facts", root.origin().stream().toList(),
        AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var blocker = new CfgBlocker(MethodCfgState.SOURCE_UNAVAILABLE, ast.identity(), method.methodKey(), ast.occurrence(root), "body",
        diagnostic);
    var run = CliTestFixtures.withRefusal(ast, blocker);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var projection = AnalysisRunSummary.from(run).occurrences().values();

    // Assert
    assertNativeReport(run, projection, runStreams);
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
  }

  @Test
  void givenAdmittedSourceWithUnavailableImplicitConstructorPosition_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("class Example { int subject() { return 1; } }", AstProfile.JAVA_SOURCE_BOUNDED)
        .representation().ast().result().orElseThrow();
    var constructorKey = original.program().methods().stream().filter(value -> value.constructor()).findFirst().orElseThrow().methodKey();
    var copied = SourceValueCapture.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> body.methodKey().equals(constructorKey)
        ? new CopiedAstValues.Body(body.methodKey(), body.root(), MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, body.uses(),
            body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())
        : body).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), original.coverage());
    var ast = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
    var run = CliTestFixtures.analyze(ast);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var summary = AnalysisRunSummary.from(run);

    // Assert
    assertNativeReport(run, summary.occurrences().values(), runStreams);
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.PARTIAL, summary.run().completion());
    assertEquals(List.of(MethodCfgState.SOURCE_UNAVAILABLE, MethodCfgState.AVAILABLE),
        run.representation().cfg().orElseThrow().methods().stream().map(value -> value.state()).toList());
    assertEquals(1, summary.program().orElseThrow().analyzedMethodBodyCount());
  }
}
