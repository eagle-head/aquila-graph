package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.MethodCoverageState;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cli.AnalysisRunSummary.ProgramSummary;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;

class AnalysisRunSummaryTest {
  @Test
  void givenCompletedRun_whenSummaryIsCreated_thenProgramAndRunMetricsAreProjected() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();

    // Act
    var summary = AnalysisRunSummary.from(run);

    // Assert
    assertSame(run, summary.run());
    assertEquals(run.identity().key(), summary.run().identity().key());
    assertEquals(SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature(), summary.softwareVersionSignature());
    assertSame(AnalysisCompletion.COMPLETE, summary.run().completion());
    assertEquals(25, summary.run().representation().ast().requestedConfiguration().orElseThrow().release().feature());
    assertEquals(1, summary.run().representation().ast().capturedInput().orElseThrow().sourceFileCount());
    assertEquals(List.of(CliTestFixtures.warningDiagnostic()), summary.run().diagnostics().values());
    var program = summary.program().orElseThrow();
    assertEquals(1, program.typeCount());
    assertEquals(1, program.methodCount());
    assertEquals(1, program.analyzedMethodBodyCount());
    assertEquals(0, program.controlFlowOperationCount());
    assertEquals(2, program.controlFlowNodeCount());
    assertEquals(1, program.controlFlowEdgeCount());
  }

  @Test
  void givenMultipleGraphsAndBodylessDeclaration_whenSummaryIsCreated_thenCountsIncludeAllGraphsAndExcludeValuesFromNodes() {
    // Arrange
    var run = CliTestFixtures.analyze("interface Example { static void first() {} void bodyless(); static int second() { return 1; } }");
    var graphs = run.representation().cfg().orElseThrow().methods().stream().flatMap(method -> method.graph().stream()).toList();
    // Act
    var summary = AnalysisRunSummary.from(run).program().orElseThrow();

    // Assert
    assertEquals(1, summary.typeCount());
    assertEquals(3, summary.methodCount());
    assertEquals(2, summary.analyzedMethodBodyCount());
    assertEquals(2, summary.controlFlowOperationCount());
    assertEquals(6, summary.controlFlowNodeCount());
    assertEquals(4, summary.controlFlowEdgeCount());
    assertFalse(graphs.stream().flatMap(graph -> graph.values().stream()).toList().isEmpty());
  }

  @Test
  void givenAstOnlyAndEmptyGraphSelection_whenSummarized_thenDeclarationCountsAndSoftwareSignatureRemainWithoutGraphCounts() {
    // Arrange
    var file = SourceFile.fromContent(Path.of("Example.java"), "interface Example { void bodyless(); static void concrete() {} }");
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(file));
    var runner = CliTestFixtures.runner();
    var sourceOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var ast = sourceOnly.representation().ast();
    var identity = ast.result().orElseThrow().identity();
    var selectedNone = runner.deriveExisting(ast, new CfgRequest(identity, List.of(), CfgProfile.JAVA_BOUNDED));
    var expectedProgram = new ProgramSummary(1, 2, 0, 0, 0, 0);

    // Act
    var sourceOnlySummary = AnalysisRunSummary.from(sourceOnly);
    var emptySelectionSummary = AnalysisRunSummary.from(selectedNone);

    // Assert
    assertEquals(Optional.empty(), sourceOnly.representation().cfg());
    assertEquals(CfgState.COMPLETE, selectedNone.representation().cfg().orElseThrow().state());
    assertEquals(Optional.of(expectedProgram), sourceOnlySummary.program());
    assertEquals(Optional.of(expectedProgram), emptySelectionSummary.program());
    assertEquals(identity.softwareSignature(), sourceOnlySummary.softwareVersionSignature());
    assertEquals(identity.softwareSignature(), emptySelectionSummary.softwareVersionSignature());
  }

  @Test
  void givenAvailableRefusedAndUnselectedBodies_whenPartialRunIsSummarized_thenOnlyAvailableGraphContributes() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("""
        interface Example {
          static int available() { return 1; }
          static int refused(int value) { (value) = 1; return value; }
          static int unselected() { return 2; }
        }
        """);
    var source = ast.result().orElseThrow();
    var methods = source.program().methods();
    var available = methods.stream().filter(method -> method.name().equals("available")).findFirst().orElseThrow().methodKey();
    var refused = methods.stream().filter(method -> method.name().equals("refused")).findFirst().orElseThrow().methodKey();
    var unselected = methods.stream().filter(method -> method.name().equals("unselected")).findFirst().orElseThrow().methodKey();
    var request = new CfgRequest(source.identity(), List.of(available, refused), CfgProfile.JAVA_BOUNDED);
    var run = CliTestFixtures.runner().deriveExisting(ast, request);

    // Act
    var summary = AnalysisRunSummary.from(run);

    // Assert
    assertEquals(CfgState.PARTIAL, run.representation().cfg().orElseThrow().state());
    assertEquals(MethodCoverageState.AVAILABLE, run.methodCoverage(available).orElseThrow().state());
    assertEquals(MethodCoverageState.UNSUPPORTED, run.methodCoverage(refused).orElseThrow().state());
    assertEquals(MethodCoverageState.NOT_REQUESTED, run.methodCoverage(unselected).orElseThrow().state());
    assertEquals(Optional.of(new ProgramSummary(1, 3, 1, 2, 4, 3)), summary.program());
  }

  @Test
  void givenCapturedSourceAndEnvironmentWithoutAnAdmittedProgram_whenSummarized_thenOnlySoftwareSignatureIsProjected() {
    // Arrange
    var run = CliTestFixtures.analyze("package example;");
    var ast = run.representation().ast();
    var expectedSignature = SoftwareVersion.from(ast.capturedInput().orElseThrow(), ast.capturedEnvironment().orElseThrow()).signature();

    // Act
    var summary = AnalysisRunSummary.from(run);

    // Assert
    assertEquals(Optional.empty(), summary.program());
    assertEquals(expectedSignature, summary.softwareVersionSignature());
  }
}
