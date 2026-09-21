package org.aquilagraph.cli;

import java.io.PrintStream;
import java.util.Objects;

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
    output.println("Repository: " + execution.repositoryStatus());
    return switch (summary.completion()) {
      case COMPLETED -> AnalyzeCommand.EXIT_COMPLETED;
      case INCOMPLETE -> AnalyzeCommand.EXIT_INCOMPLETE;
      case REJECTED -> AnalyzeCommand.EXIT_REJECTED;
    };
  }

  private static int presentRepositoryFailure(AnalyzeCommandOutcome.RepositoryFailed outcome, PrintStream output, PrintStream error) {
    printSummary(AnalysisRunSummary.from(outcome.run()), output, error);
    error.println("Repository save failed: " + outcome.message());
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
    output.printf("Run: %s%n", summary.runId());
    output.printf("Software version: %s%n", summary.softwareVersionSignature());
    output.printf("Completion: %s%n", summary.completion());
    output.printf("Java release: %d%n", summary.javaRelease());
    output.printf("Java source files: %d%n", summary.sourceFileCount());
    summary.program().ifPresent(program -> printProgramSummary(program, output));
    for (var diagnostic : summary.diagnostics()) {
      error.printf("%s %s %s: %s%n", diagnostic.severity(), diagnostic.stage(), diagnostic.code(), diagnostic.message());
    }
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
