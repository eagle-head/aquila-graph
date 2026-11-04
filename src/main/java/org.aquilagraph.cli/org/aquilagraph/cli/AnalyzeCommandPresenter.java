package org.aquilagraph.cli;

import static org.aquilagraph.cli.AnalysisDiagnosticRenderer.quote;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cli.AnalysisRunSummary.ProgramSummary;

/** Renders CLI outcomes and assigns their process exit codes. */
final class AnalyzeCommandPresenter {
  int present(AnalyzeCommandParser.Rejected rejection, PrintStream error) {
    Objects.requireNonNull(rejection, "rejection");
    Objects.requireNonNull(error, "error");
    error.println(rejection.message());
    error.println(AnalyzeCommandParser.USAGE);
    return AnalyzeCommand.EXIT_REJECTED;
  }

  int present(AnalyzeCommandOutcome outcome, PrintStream output, PrintStream error) {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");
    return switch (outcome) {
      case AnalyzeCommandOutcome.Executed executed -> presentExecution(executed, output, error);
      case AnalyzeCommandOutcome.RepositoryFailed failed -> presentRepositoryFailure(failed, output, error);
      case AnalyzeCommandOutcome.InputRejected rejected -> presentInputRejection(rejected, error);
      case AnalyzeCommandOutcome.EnvironmentFailed failed -> presentEnvironmentFailure(failed, error);
    };
  }

  private static int presentExecution(AnalyzeCommandOutcome.Executed outcome, PrintStream output, PrintStream error) {
    var execution = outcome.execution();
    var summary = AnalysisRunSummary.from(execution.run());
    printSummary(summary, output, error);
    output.println("Repository: " + execution.publication().status());
    return switch (summary.run().completion()) {
      case COMPLETE -> AnalyzeCommand.EXIT_COMPLETED;
      case PARTIAL, FAILED -> AnalyzeCommand.EXIT_INCOMPLETE;
      case REJECTED -> AnalyzeCommand.EXIT_REJECTED;
    };
  }

  private static int presentRepositoryFailure(AnalyzeCommandOutcome.RepositoryFailed outcome, PrintStream output, PrintStream error) {
    printSummary(AnalysisRunSummary.from(outcome.failure().analysisRun()), output, error);
    output.println("Repository: FAILED");
    error.println("Repository save failed: " + outcome.failure().repositoryFailure().description());
    return AnalyzeCommand.EXIT_REPOSITORY_FAILED;
  }

  private static int presentInputRejection(AnalyzeCommandOutcome.InputRejected outcome, PrintStream error) {
    error.println("Input rejected: " + outcome.message());
    return AnalyzeCommand.EXIT_REJECTED;
  }

  private static int presentEnvironmentFailure(AnalyzeCommandOutcome.EnvironmentFailed outcome, PrintStream error) {
    error.println("Analysis failed: " + outcome.message());
    return AnalyzeCommand.EXIT_INCOMPLETE;
  }

  private static void printSummary(AnalysisRunSummary summary, PrintStream output, PrintStream error) {
    var run = summary.run();
    var ast = run.representation().ast();
    output.println("Run: " + run.identity().key());
    output.println("Software version: " + summary.softwareVersionSignature());
    output.println("Completion: " + run.completion());
    output.println("Java release: " + ast.requestedConfiguration().orElseThrow().release().feature());
    output.println("Java source files: " + ast.capturedInput().map(snapshot -> snapshot.sourceFileCount()).orElse(0));
    output.println("AST: " + ast.completion());
    output.println("AST profile: " + ast.profile().version());
    printCfgSummary(run, output);
    summary.program().ifPresent(program -> printProgramSummary(program, output));
    printMethodCoverage(run, output);
    var diagnostics = new AnalysisDiagnosticRenderer(run, error);
    var occurrences = run.diagnostics();
    for (var occurrence : occurrences.occurrences()) {
      diagnostics.nativeEvent(occurrence.diagnostic(), occurrences.positionOf(occurrence) + 1);
    }
    new AnalysisQualificationRenderer(run, diagnostics, error).render();
  }

  private static void printCfgSummary(AnalysisRun run, PrintStream output) {
    var cfg = run.representation().cfg();
    output.println("CFG: " + cfg.map(value -> value.state().name())
        .orElse(run.identity().selection() instanceof NotRequested ? "NOT_REQUESTED" : "UNAVAILABLE"));
    switch (run.identity().selection()) {
      case NotRequested _ -> {
        output.println("CFG profile: not-requested");
        output.println("CFG graph model: not-requested");
        output.println("CFG selection: NOT_REQUESTED");
        output.println("CFG binding: NOT_REQUESTED");
      }
      case ProductionSelection production -> printSelection(production.selection().profile(), production.selection().scope(),
          production.boundIdentity().map(value -> value.astResultKey()).orElse("UNBOUND"), production.methodKeys(), output);
      case ExistingSelection existing -> printSelection(existing.request().profile(), CfgSelection.Scope.METHODS,
          existing.request().astIdentity().astResultKey(), existing.request().methodKeys(), output);
    }
  }

  private static void printMethodCoverage(AnalysisRun run, PrintStream output) {
    run.representation().ast().result().ifPresent(result -> {
      for (var method : result.program().methods()) {
        var methodKey = method.methodKey();
        var coverage = run.methodCoverage(methodKey).orElseThrow();
        output.println("Method CFG: " + quote(methodKey) + " " + coverage.state());
      }
    });
  }

  private static void printSelection(CfgProfile profile, CfgSelection.Scope scope, String binding, List<String> methods,
      PrintStream output) {
    output.println("CFG profile: " + quote(profile.version()));
    output.println("CFG graph model: " + quote(profile.graphModelVersion()));
    output.println("CFG selection: " + scope);
    output.println("CFG binding: " + binding);
    methods.forEach(method -> output.println("Selected method: " + quote(method)));
  }

  private static void printProgramSummary(ProgramSummary summary, PrintStream output) {
    output.println("Java types: " + summary.typeCount());
    output.println("Java methods: " + summary.methodCount());
    output.println("Analyzed method bodies: " + summary.analyzedMethodBodyCount());
    output.println("CFG operations: " + summary.controlFlowOperationCount());
    output.println("CFG nodes: " + summary.controlFlowNodeCount());
    output.println("CFG edges: " + summary.controlFlowEdgeCount());
  }
}
