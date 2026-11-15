package org.aquilagraph.cli;

import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelectedGraphSummaryTest {
  @TempDir Path sourceRoot;

  @Test
  void givenActualBoxingAndUnboxingGraphs_whenSummaryAndPresenterRun_thenNewOperationsCountAsNodesAndTheirEffectsCountAsEdges() throws IOException {
    // Arrange
    var source = "class Example { Number box(int input) { return input; } long unbox(Integer input) { return input; } }";
    var input = CliTestFixtures.produce(sourceRoot, List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)),
        AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var run = CliTestFixtures.analyze(AstOutcome.available(input));
    var streams = new CliStreams(StandardCharsets.UTF_8);

    // Act
    var summary = CliTestFixtures.report(run).analysis();
    var exitCode = CliTestFixtures.present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)), streams);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertTrue(run.diagnostics().values().isEmpty());
    assertEquals(0, exitCode);
    assertEquals(new AnalysisRunSummary.ProgramSummary(1, 3, 3, 7, 13, 13), summary.program().orElseThrow());
    assertEquals("Run: \"" + run.identity().key() + "\"" + System.lineSeparator()
        + "Software version: \"" + SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature() + "\"" + System.lineSeparator()
        + "Completion: COMPLETE" + System.lineSeparator()
        + "Java release: 25" + System.lineSeparator()
        + "Java source files: 1" + System.lineSeparator()
        + "AST: COMPLETE" + System.lineSeparator()
        + "AST profile: \"java-source-bounded/1\"" + System.lineSeparator()
        + "CFG: COMPLETE" + System.lineSeparator()
        + "CFG profile: \"java-cfg-bounded/1\"" + System.lineSeparator()
        + "CFG graph model: \"java-cfg/2\"" + System.lineSeparator()
        + "CFG selection: ALL_BODIES" + System.lineSeparator()
        + "CFG binding: \"" + input.identity().astResultKey() + "\"" + System.lineSeparator()
        + input.program().methods().stream().filter(method -> method.hasBody()).map(method -> "Selected method: \"" + method.methodKey() + "\"" + System.lineSeparator()).collect(Collectors.joining())
        + "Java types: 1" + System.lineSeparator()
        + "Java methods: 3" + System.lineSeparator()
        + "Analyzed method bodies: 3" + System.lineSeparator()
        + "CFG operations: 7" + System.lineSeparator()
        + "CFG nodes: 13" + System.lineSeparator()
        + "CFG edges: 13" + System.lineSeparator()
        + input.program().methods().stream().map(method -> "Method CFG: \"" + method.methodKey() + "\" AVAILABLE" + System.lineSeparator()).collect(Collectors.joining())
        + "Repository: NOT_REQUESTED" + System.lineSeparator() + String.join(System.lineSeparator(), ReportTextExpectations.noPublication(run)) + System.lineSeparator(), streams.outputText());
    assertEquals("", streams.errorText());
  }
}
