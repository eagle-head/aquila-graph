package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.MethodCoverageState;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.cli.AnalysisRunSummary.MethodCoverage;
import org.aquilagraph.cli.AnalysisRunSummary.MethodDescriptor;
import org.aquilagraph.cli.AnalysisRunSummary.ProgramSummary;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceQualificationReason.Node;
import org.junit.jupiter.api.Test;

class AnalysisRunSummaryTest {
  @Test
  void givenCompletedRun_whenSummaryIsCreated_thenProgramAndRunMetricsAreProjected() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();

    // Act
    var summary = CliTestFixtures.report(run).analysis();

    // Assert
    assertEquals(run.identity(), summary.identity());
    assertEquals(run.identity().key(), summary.identity().key());
    assertEquals(SoftwareVersion.from(run.representation().ast().capturedInput().orElseThrow(), run.representation().ast().capturedEnvironment().orElseThrow()).signature(), summary.software().orElseThrow().signature());
    assertSame(AnalysisCompletion.COMPLETE, summary.completion());
    assertEquals(25, summary.release().orElseThrow().feature());
    assertEquals(1, summary.sourceFileCount());
    assertEquals(List.of(CliTestFixtures.warningDiagnostic().diagnosticKey()), CliTestFixtures.report(run).diagnostics().stream().map(diagnostic -> diagnostic.value().key()).toList());
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
    var summary = CliTestFixtures.report(run).analysis().program().orElseThrow();

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
  void givenAstOnlyAndEmptyGraphSelection_whenReported_thenBothChannelsPreserveTheirDistinctSelectionAndCounts() {
    // Arrange
    var file = SourceFile.fromContent(Path.of("Example.java"), "interface Example { void bodyless(); static void concrete() {} }");
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(file));
    var runner = CliTestFixtures.runner();
    var sourceOnly = runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED), Optional.empty()));
    var ast = sourceOnly.representation().ast();
    var identity = ast.result().orElseThrow().identity();
    var selectedNone = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast, new CfgRequest(identity, List.of(), CfgProfile.JAVA_BOUNDED));
    var expectedProgram = new ProgramSummary(1, 2, 0, 0, 0, 0);
    var sourceOnlyStreams = new CliStreams();
    var selectedNoneStreams = new CliStreams();

    // Act
    var sourceOnlySummary = CliTestFixtures.report(sourceOnly).analysis();
    var emptySelectionSummary = CliTestFixtures.report(selectedNone).analysis();
    var sourceOnlyExit = CliTestFixtures.present(sourceOnly, sourceOnlyStreams);
    var selectedNoneExit = CliTestFixtures.present(selectedNone, selectedNoneStreams);

    // Assert
    assertEquals(Optional.empty(), sourceOnly.representation().cfg());
    assertEquals(CfgState.COMPLETE, selectedNone.representation().cfg().orElseThrow().state());
    assertEquals(Optional.of(expectedProgram), sourceOnlySummary.program());
    assertEquals(Optional.of(expectedProgram), emptySelectionSummary.program());
    assertEquals(identity.softwareSignature(), sourceOnlySummary.software().orElseThrow().signature());
    assertEquals(identity.softwareSignature(), emptySelectionSummary.software().orElseThrow().signature());
    assertEquals(List.of(MethodCoverageState.NOT_REQUESTED, MethodCoverageState.NOT_REQUESTED),
        sourceOnlySummary.methods().stream().map(method -> method.state()).toList());
    assertEquals(sourceOnlySummary.methods(), emptySelectionSummary.methods());
    assertFalse(sourceOnlySummary.identity().equals(emptySelectionSummary.identity()));
    assertEquals(0, sourceOnlyExit);
    assertEquals(0, selectedNoneExit);
    var coverageLines = List.of("Method CFG: \"type:Example#bodyless()\" NOT_REQUESTED",
        "Method CFG: \"type:Example#concrete()\" NOT_REQUESTED");
    assertOutput(sourceOnlyStreams, sourceOnly, "COMPLETE", "COMPLETE",
        List.of("CFG: NOT_REQUESTED", "CFG profile: not-requested", "CFG graph model: not-requested",
            "CFG selection: NOT_REQUESTED", "CFG binding: NOT_REQUESTED"), expectedProgram, coverageLines);
    assertOutput(selectedNoneStreams, selectedNone, "COMPLETE", "COMPLETE",
        requestedCfg("COMPLETE", identity, List.of()), expectedProgram, coverageLines);
    assertEquals("", sourceOnlyStreams.errorText());
    assertEquals("", selectedNoneStreams.errorText());
  }

  @Test
  void givenReversedOrDuplicateSelection_whenReported_thenRequestOrderAndCanonicalCoverageRenderIndependently() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("""
        interface Example {
          void bodyless();
          static int first() { return 1; }
          static int second() { return 2; }
          static int unselected() { return 3; }
        }
        """);
    var source = ast.result().orElseThrow();
    var canonical = source.program().methods().stream().map(method -> method.methodKey()).toList();
    var first = canonical.get(1);
    var second = canonical.get(2);
    var unselected = canonical.get(3);
    var bodyless = new MethodDescriptor(source.identity(), canonical.getFirst(), Optional.empty());
    var firstBody = new MethodDescriptor(source.identity(), first, Optional.of(source.bodyRef(first).orElseThrow()));
    var secondBody = new MethodDescriptor(source.identity(), second, Optional.of(source.bodyRef(second).orElseThrow()));
    var unselectedBody = new MethodDescriptor(source.identity(), unselected, Optional.of(source.bodyRef(unselected).orElseThrow()));
    var reversed = new CfgRequest(source.identity(), List.of(second, first), CfgProfile.JAVA_BOUNDED);
    var rejected = new CfgRequest(source.identity(), List.of(second, first, first), CfgProfile.JAVA_BOUNDED);
    var derivation = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());
    var reversedRun = derivation.deriveExisting(ast, reversed);
    var rejectedRun = derivation.deriveExisting(ast, rejected);
    var reversedStreams = new CliStreams();
    var rejectedStreams = new CliStreams();

    // Act
    var reversedSummary = CliTestFixtures.report(reversedRun).analysis();
    var rejectedSummary = CliTestFixtures.report(rejectedRun).analysis();
    var reversedExit = CliTestFixtures.present(reversedRun, reversedStreams);
    var rejectedExit = CliTestFixtures.present(rejectedRun, rejectedStreams);
    var reversedPoint = canonical.stream().map(key -> reversedRun.methodCoverage(key).orElseThrow().state()).toList();
    var rejectedPoint = canonical.stream().map(key -> rejectedRun.methodCoverage(key).orElseThrow().state()).toList();

    // Assert
    assertSame(reversed, reversedRun.representation().cfg().orElseThrow().request());
    assertSame(rejected, rejectedRun.representation().cfg().orElseThrow().request());
    assertSame(reversedRun.identity(), reversedSummary.identity());
    assertSame(rejectedRun.identity(), rejectedSummary.identity());
    assertEquals(List.of("bodyless", "first", "second", "unselected"), source.program().methods().stream().map(method -> method.name()).toList());
    assertEquals(List.of(new MethodCoverage(bodyless, MethodCoverageState.NOT_REQUESTED),
        new MethodCoverage(firstBody, MethodCoverageState.AVAILABLE), new MethodCoverage(secondBody, MethodCoverageState.AVAILABLE),
        new MethodCoverage(unselectedBody, MethodCoverageState.NOT_REQUESTED)),
        reversedSummary.methods());
    assertEquals(List.of(new MethodCoverage(bodyless, MethodCoverageState.NOT_REQUESTED),
        new MethodCoverage(firstBody, MethodCoverageState.REQUEST_REJECTED), new MethodCoverage(secondBody, MethodCoverageState.REQUEST_REJECTED),
        new MethodCoverage(unselectedBody, MethodCoverageState.NOT_REQUESTED)),
        rejectedSummary.methods());
    assertEquals(reversedPoint, reversedSummary.methods().stream().map(MethodCoverage::state).toList());
    assertEquals(rejectedPoint, rejectedSummary.methods().stream().map(MethodCoverage::state).toList());
    assertEquals(CfgState.REJECTED, rejectedSummary.cfgState().orElseThrow());
    var reversedProgram = new ProgramSummary(1, 4, 2, 4, 8, 6);
    var rejectedProgram = new ProgramSummary(1, 4, 0, 0, 0, 0);
    assertEquals(Optional.of(reversedProgram), reversedSummary.program());
    assertEquals(Optional.of(rejectedProgram), rejectedSummary.program());
    var rejectedCfg = rejectedRun.representation().cfg().orElseThrow();
    assertTrue(rejectedCfg.methods().isEmpty());
    assertEquals(0, reversedExit);
    assertEquals(2, rejectedExit);
    assertOutput(reversedStreams, reversedRun, "COMPLETE", "COMPLETE",
        requestedCfg("COMPLETE", source.identity(), List.of(second, first)), reversedProgram,
        List.of("Method CFG: \"" + canonical.getFirst() + "\" NOT_REQUESTED", "Method CFG: \"" + first + "\" AVAILABLE",
            "Method CFG: \"" + second + "\" AVAILABLE", "Method CFG: \"" + unselected + "\" NOT_REQUESTED"));
    assertOutput(rejectedStreams, rejectedRun, "REJECTED", "COMPLETE",
        requestedCfg("REJECTED", source.identity(), List.of(second, first, first)), rejectedProgram,
        List.of("Method CFG: \"" + canonical.getFirst() + "\" NOT_REQUESTED", "Method CFG: \"" + first + "\" REQUEST_REJECTED",
            "Method CFG: \"" + second + "\" REQUEST_REJECTED", "Method CFG: \"" + unselected + "\" NOT_REQUESTED"));
    assertEquals("", reversedStreams.errorText());
    var diagnostic = rejectedCfg.diagnostics().getFirst();
    assertEquals(List.of(diagnostic), rejectedRun.diagnostics().values());
    assertSame(diagnostic, rejectedRun.diagnostics().values().getFirst());
    var reason = assertInstanceOf(RequestQualificationReason.Selection.class, rejectedRun.qualification().reasons().getFirst());
    assertSame(rejectedCfg, reason.outcome());
    assertEquals(List.of(rejectedRun.diagnostics().occurrences().getFirst()), rejectedRun.diagnostics().relatedTo(reason));
    var astKey = source.identity().astResultKey();
    var link = "  Related diagnostic: \"" + diagnostic.diagnosticKey() + "\" occurrence=1";
    assertEquals(reportText(List.of("Diagnostic: \"" + diagnostic.diagnosticKey() + "\"", "  Occurrence: 1",
        "  Owner: CFG_REQUEST", "  Native position: 0", "  Severity: ERROR", "  Stage: CONTROL_FLOW",
        "  Cause: CONTROL_FLOW_UNAVAILABLE", "  Code: \"control-flow.duplicate-selection\"",
        "  Message: \"The requested selection repeats a canonical method key\"", "  Origins: unavailable",
        "Qualification: CFG REJECTED", "  Profile: \"java-cfg-bounded/1\"", "  Subject: \"" + astKey + "\"",
        "  AST result: \"" + astKey + "\"", "  Origins: unavailable", link, "Qualification: APPLICATION SELECTION",
        "  Profile: \"java-cfg-bounded/1\"", "  Subject: \"" + astKey + "\"", "  Origins: unavailable", link)),
        rejectedStreams.errorText());
  }

  @Test
  void givenIncompleteSourceWithMixedOutcomes_whenSummarized_thenCanonicalCoverageAndOnlyAvailableGraphContribute() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("""
        interface Example {
          static int available() { return 1; }
          static int refused(int value) { (value) = 1; return value; }
          static int failed() { return 2; }
          static void unselected() {
            try {} catch (IllegalArgumentException | IllegalStateException failure) {}
          }
        }
        """);
    var source = ast.result().orElseThrow();
    var methods = source.program().methods();
    var keys = methods.stream().map(method -> method.methodKey()).toList();
    var request = new CfgRequest(source.identity(), keys.subList(0, 3), CfgProfile.JAVA_BOUNDED);
    var cause = new IllegalStateException("Controlled CFG construction failure");
    var run = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(keys.get(2), cause)).deriveExisting(ast, request);
    var streams = new CliStreams();

    // Act
    var summary = CliTestFixtures.report(run).analysis();
    var exit = CliTestFixtures.present(run, streams);

    // Assert
    assertEquals(List.of("available", "refused", "failed", "unselected"), methods.stream().map(method -> method.name()).toList());
    assertSame(run.identity(), summary.identity());
    assertEquals(AstCompletion.INCOMPLETE, summary.sourceState());
    assertEquals(AnalysisCompletion.FAILED, summary.completion());
    assertEquals(Optional.of(CfgState.PARTIAL), summary.cfgState());
    var cfg = run.representation().cfg().orElseThrow();
    assertSame(request, cfg.request());
    assertSame(cause, cfg.methods().getLast().failure().orElseThrow());
    assertEquals(List.of(MethodCoverageState.AVAILABLE, MethodCoverageState.UNSUPPORTED, MethodCoverageState.FAILED,
        MethodCoverageState.NOT_REQUESTED), summary.methods().stream().map(MethodCoverage::state).toList());
    assertEquals(keys.stream().map(key -> new MethodDescriptor(source.identity(), key, source.bodyRef(key))).toList(),
        summary.methods().stream().map(MethodCoverage::method).toList());
    var expectedProgram = new ProgramSummary(1, 4, 1, 2, 4, 3);
    assertEquals(Optional.of(expectedProgram), summary.program());
    assertEquals(1, exit);
    var astKey = source.identity().astResultKey();
    assertOutput(streams, run, "FAILED", "INCOMPLETE", requestedCfg("PARTIAL", source.identity(), keys.subList(0, 3)),
        expectedProgram, List.of("Method CFG: \"" + keys.get(0) + "\" AVAILABLE",
            "Method CFG: \"" + keys.get(1) + "\" UNSUPPORTED", "Method CFG: \"" + keys.get(2) + "\" FAILED",
            "Method CFG: \"" + keys.get(3) + "\" NOT_REQUESTED"));

    var sourceReason = ast.qualification().reasons().getFirst();
    var subject = assertInstanceOf(Node.class, sourceReason.subject());
    var sourceOrigin = originLine(sourceReason.origins().getFirst());
    var refused = cfg.methods().get(1);
    var failed = cfg.methods().getLast();
    var blocker = refused.blockers().getFirst();
    var refusedOrigin = originLine(blocker.diagnostic().origins().getFirst());
    var refusedLink = "  Related diagnostic: \"" + blocker.diagnostic().diagnosticKey() + "\" occurrence=1";
    var failedLink = "  Related diagnostic: \"" + failed.diagnostics().getFirst().diagnosticKey() + "\" occurrence=2";
    var expectedError = new ArrayList<>(methodDiagnosticLines(refused, 1, refusedOrigin));
    expectedError.addAll(methodDiagnosticLines(failed, 2, "  Origins: unavailable"));
    expectedError.addAll(List.of("Qualification: SOURCE PROFILE_RESTRICTION", "  Profile: \"java-source-bounded/1\"",
        "  Role: PROFILE_FORM", "  Subject: \"" + subject.nodeKey() + "\"", "  Subject kind: NODE",
        "  Subject source file: \"" + subject.sourceFileKey() + "\"", sourceOrigin, "Qualification: CFG UNSUPPORTED"));
    expectedError.addAll(methodHeader(refused));
    expectedError.addAll(List.of("  Source occurrence: \"" + blocker.occurrence().orElseThrow().nodeKey() + "\"",
        "  Slot: \"" + blocker.slot() + "\"", refusedOrigin, refusedLink, "Qualification: CFG FAILED"));
    expectedError.addAll(methodHeader(failed));
    expectedError.addAll(List.of("  Failure type: \"java.lang.IllegalStateException\"",
        "  Failure message: \"Controlled CFG construction failure\"", "  Failure reference: 1", "  Origins: unavailable",
        failedLink, "Qualification: APPLICATION SOURCE", "  Profile: \"java-source-bounded/1\"",
        "  Subject: \"" + astKey + "\"", sourceOrigin, "Qualification: APPLICATION METHOD"));
    expectedError.addAll(methodHeader(refused));
    expectedError.addAll(List.of(refusedOrigin, refusedLink, "Qualification: APPLICATION METHOD"));
    expectedError.addAll(methodHeader(failed));
    expectedError.addAll(List.of("  Origins: unavailable", failedLink));
    expectedError.addAll(ReportTextExpectations.simpleNode(1, "java.lang.IllegalStateException", "Controlled CFG construction failure"));
    assertEquals(reportText(expectedError), streams.errorText());
  }

  @Test
  void givenCapturedSourceAndEnvironmentWithoutAnAdmittedProgram_whenSummarized_thenOnlySoftwareSignatureIsProjected() {
    // Arrange
    var run = CliTestFixtures.analyze("package example;");
    var ast = run.representation().ast();
    var expectedSignature = SoftwareVersion.from(ast.capturedInput().orElseThrow(), ast.capturedEnvironment().orElseThrow()).signature();

    // Act
    var summary = CliTestFixtures.report(run).analysis();

    // Assert
    assertEquals(Optional.empty(), summary.program());
    assertEquals(expectedSignature, summary.software().orElseThrow().signature());
    assertEquals(List.of(), summary.methods());
  }

  private static void assertOutput(CliStreams streams, AnalysisRun run, String completion, String sourceState,
      List<String> cfgLines, ProgramSummary program, List<String> coverageLines) {
    var identity = run.representation().ast().result().orElseThrow().identity();
    var lines = new ArrayList<>(List.of("Run: \"" + run.identity().key() + "\"",
        "Software version: \"" + identity.softwareSignature() + "\"", "Completion: " + completion,
        "Java release: 25", "Java source files: 1", "AST: " + sourceState, "AST profile: \"java-source-bounded/1\""));
    lines.addAll(cfgLines);
    lines.addAll(List.of("Java types: " + program.typeCount(), "Java methods: " + program.methodCount(),
        "Analyzed method bodies: " + program.analyzedMethodBodyCount(), "CFG operations: " + program.controlFlowOperationCount(),
        "CFG nodes: " + program.controlFlowNodeCount(), "CFG edges: " + program.controlFlowEdgeCount()));
    lines.addAll(coverageLines);
    lines.add("Repository: NOT_REQUESTED");
    lines.addAll(ReportTextExpectations.noPublication(run));
    assertEquals(reportText(lines), streams.outputText());
  }

  private static List<String> requestedCfg(String state, AstIdentity identity, List<String> selectedKeys) {
    var lines = new ArrayList<>(List.of("CFG: " + state, "CFG profile: \"java-cfg-bounded/1\"",
        "CFG graph model: \"java-cfg/2\"", "CFG selection: METHODS", "CFG binding: \"" + identity.astResultKey() + "\""));
    selectedKeys.forEach(key -> lines.add("Selected method: \"" + key + "\""));
    return lines;
  }

  private static String reportText(List<String> lines) {
    return String.join(System.lineSeparator(), lines) + System.lineSeparator();
  }

  private static List<String> methodDiagnosticLines(MethodCfgOutcome method, int occurrence, String origin) {
    var diagnostic = method.diagnostics().getFirst();
    return List.of("Diagnostic: \"" + diagnostic.diagnosticKey() + "\"", "  Occurrence: " + occurrence,
        "  Owner: CFG_METHOD", "  Native position: 0", "  Owner method: \"" + method.methodKey() + "\"", "  Severity: ERROR",
        "  Stage: CONTROL_FLOW", "  Cause: CONTROL_FLOW_UNAVAILABLE", "  Code: \"" + diagnostic.code() + "\"",
        "  Message: \"" + diagnostic.message() + "\"", origin);
  }

  private static List<String> methodHeader(MethodCfgOutcome method) {
    return List.of("  Profile: \"java-cfg-bounded/1\"", "  Subject: \"" + method.methodKey() + "\"",
        "  AST result: \"" + method.astIdentity().astResultKey() + "\"", "  Body: WRITTEN",
        "  Body occurrence: \"" + method.body().orElseThrow().sourceOccurrenceKey().orElseThrow() + "\"");
  }

  private static String originLine(SourceSpan span) {
    return "  Origin: \"" + span.sourceFileKey() + "\" file=\"Example.java\" range=[" + span.startOffset()
        + "," + span.endOffsetExclusive() + ")";
  }
}
