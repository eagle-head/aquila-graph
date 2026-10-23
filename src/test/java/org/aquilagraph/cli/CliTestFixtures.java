package org.aquilagraph.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationOutcome;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;

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
    return new AnalysisRun(run.identity(), run.representation(), run.completion(), List.of(warningDiagnostic()));
  }

  static AnalysisRun incompleteRun() {
    var run = completedRun();
    return new AnalysisRun(run.identity(), run.representation(), AnalysisCompletion.FAILED, List.of(errorDiagnostic()));
  }

  static AnalysisRun rejectedRun() {
    var run = analyze("");
    return new AnalysisRun(run.identity(), run.representation(), AnalysisCompletion.REJECTED, List.of(errorDiagnostic()));
  }

  static AnalysisRun analyze(String source) {
    var sources = source.isEmpty() ? List.<SourceFile>of() : List.of(SourceFile.fromContent(Path.of("Example.java"), source));
    var snapshot = InputSnapshot.fromSources(Path.of("."), sources);
    try {
      return new SourceAnalysisFixture().analyze(new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
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
