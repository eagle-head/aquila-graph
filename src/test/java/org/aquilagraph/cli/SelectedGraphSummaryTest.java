package org.aquilagraph.cli;

import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelectedGraphSummaryTest {
  @TempDir Path sourceRoot;

  @Test
  void givenActualBoxingAndUnboxingGraphs_whenSummaryAndPresenterRun_thenNewOperationsCountAsNodesAndTheirEffectsCountAsEdges() throws IOException {
    // Arrange
    var source = "class Example { Number box(int input) { return input; } long unbox(Integer input) { return input; } }";
    var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var run = new SourceAnalysisFixture().analyze(input);
    var output = new ByteArrayOutputStream();
    var error = new ByteArrayOutputStream();

    // Act
    var summary = AnalysisRunSummary.from(run);
    int exitCode;
    try (var outputStream = new PrintStream(output, true, StandardCharsets.UTF_8);
        var errorStream = new PrintStream(error, true, StandardCharsets.UTF_8)) {
      exitCode = new AnalyzeCommandPresenter().present(
          new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)), outputStream, errorStream);
    }

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertTrue(run.diagnostics().isEmpty());
    assertEquals(0, exitCode);
    assertEquals(new AnalysisRunSummary.ProgramSummary(1, 3, 3, 7, 13, 13), summary.program().orElseThrow());
    assertEquals("Run: " + run.identity().key() + System.lineSeparator()
        + "Software version: " + SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature() + System.lineSeparator()
        + "Completion: COMPLETED" + System.lineSeparator()
        + "Java release: 25" + System.lineSeparator()
        + "Java source files: 1" + System.lineSeparator()
        + "Java types: 1" + System.lineSeparator()
        + "Java methods: 3" + System.lineSeparator()
        + "Analyzed method bodies: 3" + System.lineSeparator()
        + "CFG operations: 7" + System.lineSeparator()
        + "CFG nodes: 13" + System.lineSeparator()
        + "CFG edges: 13" + System.lineSeparator()
        + "Repository: NOT_REQUESTED" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
    assertEquals("", error.toString(StandardCharsets.UTF_8));
  }
}
