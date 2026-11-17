package org.aquilagraph.cli;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CfgResultPresentationTest {
  @TempDir Path directory;

  @Test
  void givenRecoverableConstructionFailure_whenPresented_thenFailedCompletionRetainsSourceFailureAndExactMethodCause() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { static int run() { return 1; } }");
    var key = ast.result().orElseThrow().program().methods().getFirst().methodKey();
    var cause = new IllegalStateException("construction\nfailed\t\"once\"");
    var runner = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(key, cause));
    var run = runner.deriveExisting(ast, new CfgRequest(ast.result().orElseThrow().identity(), List.of(key), CfgProfile.JAVA_BOUNDED));
    var streams = new CliStreams();
    // Act
    var code = CliTestFixtures.present(run, streams);
    // Assert
    assertEquals(1, code);
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertSame(cause, run.representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
    assertTrue(streams.outputLines().contains("Completion: FAILED"));
    assertTrue(streams.outputLines().contains("AST: COMPLETE"));
    assertTrue(streams.outputLines().contains("CFG: PARTIAL"));
    assertTrue(streams.errorLines().contains("Qualification: CFG FAILED"));
    assertTrue(streams.errorLines().contains("  Failure type: \"java.lang.IllegalStateException\""));
    assertTrue(streams.errorLines().contains("  Failure message: \"construction\\nfailed\\t\\\"once\\\"\""));
    assertTrue(streams.errorLines().contains("  Related diagnostic: \"" + run.diagnostics().values().getFirst().diagnosticKey() + "\" occurrence=1"));
  }

  @Test
  void givenAstOnlyAndEmptyExplicitSelections_whenPresented_thenAbsentRequestAndEmptyRequestRemainDistinct() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "interface Example { void absent(); static void run() {} }")));
    var runner = CliTestFixtures.runner();
    var astOnly = runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED), Optional.empty()));
    var empty = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(astOnly.representation().ast(), new CfgRequest(astOnly.representation().ast().result().orElseThrow().identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var absentStreams = new CliStreams();
    var emptyStreams = new CliStreams();
    // Act
    CliTestFixtures.present(astOnly, absentStreams);
    CliTestFixtures.present(empty, emptyStreams);
    // Assert
    assertTrue(absentStreams.outputLines().containsAll(List.of("CFG: NOT_REQUESTED", "CFG profile: not-requested", "CFG graph model: not-requested", "CFG selection: NOT_REQUESTED", "CFG binding: NOT_REQUESTED")));
    assertTrue(emptyStreams.outputLines().containsAll(List.of("CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/3\"", "CFG selection: METHODS")));
    assertFalse(emptyStreams.outputLines().stream().anyMatch(line -> line.startsWith("Selected method:")));
    assertTrue(absentStreams.outputLines().stream().filter(line -> line.startsWith("Method CFG:")).allMatch(line -> line.endsWith(" NOT_REQUESTED")));
    assertEquals(List.of(), absentStreams.errorLines());
    assertEquals(List.of(), emptyStreams.errorLines());
  }

  @Test
  void givenRejectedProfilesAndDuplicateUnknownSelections_whenPresented_thenRequestedTextBindingAndEverySelectionSlotSurvive() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { static void run() {} }");
    var key = ast.result().orElseThrow().program().methods().getFirst().methodKey();
    var profile = new CfgProfile("policy\r\n\"\\\t\u0001\u007f", "model\r\n\"\\\t\u0002");
    var foreign = CliTestFixtures.produce("interface Example { static void run() { return; } }");
    var request = new CfgRequest(foreign.result().orElseThrow().identity(), List.of(key, key, "unknown"), profile);
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast, request);
    var streams = new CliStreams();
    var sentinel = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast,
        new CfgRequest(request.astIdentity(), List.of(), new CfgProfile("not-requested", "not-requested")));
    var literal = new CliStreams();
    // Act
    var code = CliTestFixtures.present(run, streams);
    CliTestFixtures.present(sentinel, literal);
    // Assert
    assertEquals(2, code);
    assertSame(request, run.representation().cfg().orElseThrow().request());
    assertTrue(streams.outputLines().contains("CFG profile: \"policy\\r\\n\\\"\\\\\\t\\u0001\\u007f\""));
    assertTrue(streams.outputLines().contains("CFG graph model: \"model\\r\\n\\\"\\\\\\t\\u0002\""));
    assertFalse(ast.result().orElseThrow().identity().equals(request.astIdentity()));
    assertEquals(1, streams.outputLines().stream().filter(line -> line.startsWith("CFG profile:")).count());
    assertEquals(1, streams.outputLines().stream().filter(line -> line.startsWith("CFG graph model:")).count());
    assertEquals(List.of("Selected method: \"" + key + "\"", "Selected method: \"" + key + "\"", "Selected method: \"unknown\""), streams.outputLines().stream().filter(line -> line.startsWith("Selected method:")).toList());
    assertTrue(streams.outputLines().contains("CFG binding: \"" + request.astIdentity().astResultKey() + "\""));
    assertTrue(streams.errorLines().contains("Qualification: CFG REJECTED"));
    assertTrue(literal.outputLines().containsAll(List.of("CFG profile: \"not-requested\"", "CFG graph model: \"not-requested\"")));
  }

  @Test
  void givenSuppliedBodylessMethodError_whenPresented_thenNativeEventAndApplicationReasonRemainWithoutSyntheticBlockers() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { void absent(); }");
    var source = ast.result().orElseThrow();
    var key = source.program().methods().getFirst().methodKey();
    var diagnostic = new AnalysisDiagnostic("cfg:bodyless", "control-flow.notice", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "Supplied bodyless method error", List.of(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var method = new MethodCfgOutcome(source.identity(), key, MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(),
        List.of(), Optional.empty(), List.of(diagnostic));
    var request = new CfgRequest(source.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), List.of(diagnostic));
    var run = AnalysisCapabilities.existingSourceDerivation((input, selected) -> cfg).deriveExisting(ast, request);
    var streams = new CliStreams();

    // Act
    var code = CliTestFixtures.present(run, streams);

    // Assert
    assertEquals(1, code);
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertTrue(streams.outputLines().contains("Method CFG: \"" + key + "\" NOT_APPLICABLE"));
    assertEquals(List.of("Diagnostic: \"cfg:bodyless\"", "  Occurrence: 1", "  Owner: CFG_METHOD", "  Native position: 0", "  Owner method: \"" + key + "\"", "  Severity: ERROR", "  Stage: CONTROL_FLOW",
        "  Cause: CONTROL_FLOW_UNAVAILABLE", "  Code: \"control-flow.notice\"", "  Message: \"Supplied bodyless method error\"",
        "  Origins: unavailable", "Qualification: APPLICATION METHOD_DIAGNOSTIC", "  Profile: \"java-cfg-bounded/1\"",
        "  Subject: \"" + key + "\"", "  AST result: \"" + source.identity().astResultKey() + "\"", "  Body: unavailable", "  Origins: unavailable", "  Related diagnostic: \"cfg:bodyless\" occurrence=1"), streams.errorLines());
    assertEquals(List.of(), method.blockers());
  }
}
