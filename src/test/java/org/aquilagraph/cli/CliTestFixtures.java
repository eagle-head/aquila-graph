package org.aquilagraph.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.application.RepositorySavePolicy;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CfgEdge;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.TransferKind;

final class CliTestFixtures {
  private CliTestFixtures() {
  }

  static AnalyzeCommandArguments arguments() {
    return new AnalyzeCommandArguments(Path.of("source"), JavaRelease.JAVA_25, RepositorySavePolicy.DO_NOT_SAVE);
  }

  static AnalysisRun completedRun() {
    return completedRun(List.of());
  }

  static AnalysisRun completedRunWithWarning() {
    return completedRun(List.of(warningDiagnostic()));
  }

  static AnalysisRun incompleteRun() {
    return run("incomplete", inputSnapshot(), Optional.empty(), List.of(errorDiagnostic()), AnalysisCompletion.INCOMPLETE);
  }

  static AnalysisRun rejectedRun() {
    return run("rejected", emptyInputSnapshot(), Optional.empty(), List.of(errorDiagnostic()), AnalysisCompletion.REJECTED);
  }

  static AnalysisDiagnostic warningDiagnostic() {
    return diagnostic("cli.warning", DiagnosticSeverity.WARNING, "analysis warning");
  }

  static AnalysisDiagnostic errorDiagnostic() {
    return diagnostic("cli.error", DiagnosticSeverity.ERROR, "source failed");
  }

  private static AnalysisRun completedRun(List<AnalysisDiagnostic> diagnostics) {
    return run("completed", inputSnapshot(), Optional.of(programRepresentation()), diagnostics, AnalysisCompletion.COMPLETED);
  }

  private static AnalysisRun run(String identity, InputSnapshot inputSnapshot, Optional<ProgramRepresentation> representation,
      List<AnalysisDiagnostic> diagnostics, AnalysisCompletion completion) {
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var environment = new AnalysisEnvironmentSnapshot(configuration, "test-compiler", "test-vendor", "25-test",
        List.of("--release", "25"), List.of(), List.of());
    return new AnalysisRun(StableId.sha256("analysis-run", identity), inputSnapshot, environment, representation, diagnostics, completion,
        AnalysisRun.CURRENT_ANALYZER_VERSION, AnalysisRun.CURRENT_MODEL_VERSION);
  }

  private static InputSnapshot inputSnapshot() {
    var content = "class Example { void run() {} }";
    var source = new SourceFile(StableId.sha256("source", "Example.java", content), Path.of("Example.java"),
        StableId.sha256("source-content", content), content);
    return new InputSnapshot(sourceRoot(), "SHA-256", StableId.sha256("snapshot", source.sourceFileKey()), List.of(source));
  }

  private static InputSnapshot emptyInputSnapshot() {
    return new InputSnapshot(sourceRoot(), "SHA-256", StableId.sha256("snapshot", "empty"), List.of());
  }

  private static Path sourceRoot() {
    return Path.of(".").toAbsolutePath().normalize();
  }

  private static ProgramRepresentation programRepresentation() {
    var typeKey = StableId.sha256("type", "Example");
    var methodKey = StableId.sha256("method", "Example", "run()");
    var method = new JavaMethod(methodKey, typeKey, "run", "run()", "void", List.of(), Set.of(), false, true, true, List.of());
    var type = new JavaType(typeKey, "Example", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        List.of(), false, List.of(), List.of(method), List.of());

    var graphId = StableId.sha256("cfg", methodKey);
    var entry = new EntryNode(StableId.sha256("cfg-node", graphId, "entry"), graphId);
    var exit = new ExitNode(StableId.sha256("cfg-node", graphId, "exit"), graphId);
    var edge = new CfgEdge(StableId.sha256("cfg-edge", graphId, "entry-exit"), graphId, entry.nodeId(), exit.nodeId(),
        TransferKind.NORMAL, Optional.empty());
    var graph = new ControlFlowGraph(graphId, methodKey, entry, List.of(), List.of(), exit, List.of(edge));
    var analysis = new MethodAnalysis(StableId.sha256("method-analysis", methodKey), method, graph);
    return new ProgramRepresentation(new JavaProgram(List.of(type)), List.of(analysis));
  }

  private static AnalysisDiagnostic diagnostic(String code, DiagnosticSeverity severity, String message) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", code, message), code, DiagnosticStage.FRONTEND, severity, message,
        List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }
}
