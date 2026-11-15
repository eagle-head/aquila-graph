package org.aquilagraph.cli;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisComputation;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationFailure;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationOutcome;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.internal.SourceAnalysisFixture;

final class CliTestFixtures {
  private CliTestFixtures() {}

  static AnalyzeCommandArguments arguments() {
    return new AnalyzeCommandArguments(Path.of("source"), JavaRelease.JAVA_25, PublicationIntent.DO_NOT_SAVE);
  }

  static AnalysisRun completedRun() {
    return analyze("interface Example { static void run() {} }");
  }

  static AnalysisRun completedRunWithWarning() {
    var run = completedRun();
    return AnalysisRun.create(run.identity(), run.representation(), List.of(warningDiagnostic()));
  }

  static AnalysisRun incompleteRun() {
    var run = completedRun();
    return AnalysisRun.create(run.identity(), run.representation(), List.of(errorDiagnostic()));
  }

  static AnalysisRun rejectedRun() {
    var run = analyze("");
    return AnalysisRun.create(run.identity(), run.representation(), List.of(errorDiagnostic()));
  }

  static AnalysisRun analyze(String source) {
    try {
      return analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  static AnalysisRun analyze(String source, AstProfile profile) throws IOException {
    return analyze(produce(source, profile));
  }

  static AnalysisRun analyze(AstOutcome outcome) {
    return new SourceAnalysisFixture().analyze(outcome);
  }

  static AnalysisRun analyze(AstResult result) {
    return analyze(AstOutcome.available(result));
  }

  static AstOutcome produce(InputSnapshot snapshot, AstProfile profile) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
  }

  static AstOutcome produce(String source, AstProfile profile) throws IOException {
    var sources = source.isEmpty() ? List.<SourceFile>of() : List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source));
    return produce(Path.of("."), sources, profile);
  }

  static AstOutcome produce(String source) throws IOException {
    return produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
  }

  static AstOutcome produce(Path root, List<SourceFile> sources, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(root), sources);
    return produce(snapshot, profile);
  }

  static AnalysisComputation runner() {
    return AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
  }

  static AnalysisUseCase service(AnalysisComputation runner, AnalysisRunRepositoryFactory repositories) {
    return AnalysisCapabilities.execution(runner, context -> { throw new AssertionError("AST repository must not open"); }, repositories);
  }

  static AnalysisRunRepositoryFactory repositoriesMustNotOpen() {
    return context -> { throw new AssertionError("Repository must not open"); };
  }

  static AnalysisRun withRefusal(AstResult ast, CfgBlocker blocker) {
    var request = new CfgRequest(ast.identity(), List.of(blocker.methodKey()), CfgProfile.JAVA_BOUNDED);
    var method = new MethodCfgOutcome(ast.identity(), blocker.methodKey(), blocker.kind(), ast.bodyRef(blocker.methodKey()),
        Optional.empty(), List.of(blocker), Optional.empty(), List.of(blocker.diagnostic()));
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), method.diagnostics());
    var outcome = AstOutcome.available(ast);
    return AnalysisCapabilities.existingSourceDerivation((source, selected) -> cfg).deriveExisting(outcome, request);
  }

  static int present(AnalysisRun run, CliStreams streams) {
    return present(new AnalyzeCommandOutcome.Executed(withoutRepositoryRequest(run)), streams);
  }

  static int present(AnalyzeCommandOutcome outcome, CliStreams streams) {
    var prepared = prepared(outcome);
    var emission = new CommandOutputEmitter().emit(outcome, prepared, CommandDestination.printStream(streams.output()),
        CommandDestination.printStream(streams.error()));
    return new CommandInvocation(outcome, emission).exitCode();
  }

  static PreparedCommandOutput prepared(AnalyzeCommandOutcome outcome) {
    var report = new CommandReportProjector(failure -> Optional.empty()).project(outcome);
    return new CommandTextRenderer(System.lineSeparator()).render(new CommandDocumentComposer().compose(report));
  }

  static CommandReport.Executed report(AnalysisRun run) {
    return (CommandReport.Executed) new CommandReportProjector(failure -> Optional.empty())
        .project(new AnalyzeCommandOutcome.Executed(withoutRepositoryRequest(run)));
  }

  static AnalysisExecution withFailedPublication(AnalysisRun run, RepositoryException primary, Throwable... cleanup) {
    var context = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var carrier = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, primary);
    for (var failure : cleanup) {
      carrier.addSuppressed(failure);
    }
    var publication = new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, Optional.of(context.subject()), PublicationStatus.FAILED,
        CommitKnowledge.UNKNOWN, Optional.of(new PublicationFailure(carrier)));
    return new AnalysisExecution(run, publication);
  }

  static AnalysisExecution withoutRepositoryRequest(AnalysisRun run) {
    return new AnalysisExecution(run, new PublicationOutcome(PublicationIntent.DO_NOT_SAVE,
        run.representation().ast().result().map(ast -> new PublicationSubjectIdentity.Ast(ast.identity())),
        PublicationStatus.NOT_REQUESTED, CommitKnowledge.NOT_STARTED, Optional.empty()));
  }

  static AnalysisExecution notEligibleForRepository(AnalysisRun run, PublicationIntent intent) {
    return new AnalysisExecution(run, new PublicationOutcome(intent,
        Optional.of(new PublicationSubjectIdentity.CompletedRun(run.identity().key())), PublicationStatus.NOT_ELIGIBLE,
        CommitKnowledge.NOT_STARTED, Optional.empty()));
  }

  static AnalysisExecution saved(AnalysisRun run) {
    return new AnalysisExecution(run, new PublicationOutcome(PublicationIntent.SAVE_COMPLETED,
        Optional.of(new PublicationSubjectIdentity.CompletedRun(run.identity().key())), PublicationStatus.SAVED,
        CommitKnowledge.KNOWN_COMMIT, Optional.empty()));
  }

  static AnalysisDiagnostic warningDiagnostic() {
    return diagnostic("cli.warning", DiagnosticSeverity.WARNING, "analysis warning");
  }

  static AnalysisDiagnostic errorDiagnostic() {
    return diagnostic("cli.error", DiagnosticSeverity.ERROR, "source failed");
  }

  private static AnalysisDiagnostic diagnostic(String code, DiagnosticSeverity severity, String message) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", code, message), code, DiagnosticStage.FRONTEND, severity, message,
        List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }
}
