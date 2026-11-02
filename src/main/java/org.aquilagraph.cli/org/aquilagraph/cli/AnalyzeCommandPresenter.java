package org.aquilagraph.cli;

import static org.aquilagraph.cli.AnalysisDiagnosticRenderer.quote;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.api.CfgProfile;

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
    output.printf("Run: %s%n", run.identity().key());
    output.printf("Software version: %s%n", summary.softwareVersionSignature());
    output.printf("Completion: %s%n", run.completion());
    output.printf("Java release: %d%n", ast.requestedConfiguration().orElseThrow().release().feature());
    output.printf("Java source files: %d%n", ast.capturedInput().map(snapshot -> snapshot.sourceFileCount()).orElse(0));
    output.println("AST: " + ast.completion());
    output.println("AST profile: " + ast.profile().version());
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
    summary.program().ifPresent(program -> printProgramSummary(program, output));
    ast.result().ifPresent(result -> result.program().methods().forEach(method -> output
        .println("Method CFG: " + quote(method.methodKey()) + " " + run.methodCoverage(method.methodKey()).orElseThrow().state())));
    var diagnostics = new AnalysisDiagnosticRenderer(run, error);
    var occurrences = summary.occurrences();
    for (var position = 0; position < occurrences.diagnostics().size(); position++) {
      diagnostics.nativeEvent(occurrences.diagnostics().get(position), position + 1);
    }
    new AnalysisQualificationRenderer(summary, diagnostics, error).render();
  }

  private static void printSelection(CfgProfile profile, CfgSelection.Scope scope, String binding, List<String> methods,
      PrintStream output) {
    output.println("CFG profile: " + quote(profile.version()));
    output.println("CFG graph model: " + quote(profile.graphModelVersion()));
    output.println("CFG selection: " + scope);
    output.println("CFG binding: " + binding);
    methods.forEach(method -> output.println("Selected method: " + quote(method)));
  }

  private static void printProgramSummary(AnalysisRunSummary.ProgramSummary summary, PrintStream output) {
    output.printf("Java types: %d%n", summary.typeCount());
    output.printf("Java methods: %d%n", summary.methodCount());
    output.printf("Analyzed method bodies: %d%n", summary.analyzedMethodBodyCount());
    output.printf("CFG operations: %d%n", summary.controlFlowOperationCount());
    output.printf("CFG nodes: %d%n", summary.controlFlowNodeCount());
    output.printf("CFG edges: %d%n", summary.controlFlowEdgeCount());
  }
}
