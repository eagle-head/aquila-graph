package org.aquilagraph.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.RepositorySavePolicy;
import org.aquilagraph.application.port.AnalysisEnvironmentException;
import org.aquilagraph.application.port.AnalysisInputException;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.JavaRelease;

/** Parses analysis command arguments and presents execution results. */
public final class AnalyzeCommand {
  private static final String PERSIST_OPTION = "--persist";
  private static final String RELEASE_OPTION = "--release";
  private static final int REQUIRED_POSITIONAL_ARGUMENTS = 1;

  /** Exit code for a completed analysis and any requested repository save. */
  public static final int EXIT_COMPLETED = 0;
  /** Exit code for an analysis that produced an incomplete result. */
  public static final int EXIT_INCOMPLETE = 1;
  /** Exit code for rejected input or arguments. */
  public static final int EXIT_REJECTED = 2;
  /** Exit code for a completed analysis whose repository save failed. */
  public static final int EXIT_REPOSITORY_FAILED = 3;

  private final AnalysisApplicationService analysisService;

  /**
   * Creates a command backed by the application service.
   *
   * @param analysisService
   *          analysis use case
   */
  public AnalyzeCommand(AnalysisApplicationService analysisService) {
    this.analysisService = Objects.requireNonNull(analysisService, "analysisService");
  }

  /**
   * Executes one command invocation.
   *
   * @param arguments
   *          command-line arguments
   * @param output
   *          standard result stream
   * @param error
   *          diagnostic stream
   * @return one of the declared exit codes
   */
  public int run(String[] arguments, PrintStream output, PrintStream error) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");

    final CommandArguments parsed;
    try {
      parsed = CommandArguments.parse(arguments);
    } catch (IllegalArgumentException exception) {
      error.println(exception.getMessage());
      error.println("Usage: java -jar aquila-graph.jar <source-root> --release <feature> [--persist]");
      return EXIT_REJECTED;
    }

    final AnalysisExecution execution;
    try {
      var repositorySavePolicy = parsed.persist() ? RepositorySavePolicy.SAVE_COMPLETED : RepositorySavePolicy.DO_NOT_SAVE;
      execution = analysisService
          .execute(new AnalysisRequest(parsed.sourceRoot(), CompilerConfiguration.forRelease(parsed.release()), repositorySavePolicy));
    } catch (AnalysisRepositoryException exception) {
      printRun(exception.analysisRun(), output, error);
      var cause = Objects.requireNonNull(exception.getCause(), "repository cause");
      error.println("Repository save failed: " + Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()));
      return EXIT_REPOSITORY_FAILED;
    } catch (AnalysisInputException exception) {
      error.println("Input rejected: " + exception.getMessage());
      return EXIT_REJECTED;
    } catch (AnalysisEnvironmentException exception) {
      error.println("Analysis failed: " + exception.getMessage());
      return EXIT_INCOMPLETE;
    }

    var run = execution.run();
    printRun(run, output, error);
    output.println("Repository: " + execution.repositoryStatus());
    if (run.completion() != AnalysisCompletion.COMPLETED) {
      return run.completion() == AnalysisCompletion.REJECTED ? EXIT_REJECTED : EXIT_INCOMPLETE;
    }
    return EXIT_COMPLETED;
  }

  private static void printRun(AnalysisRun run, PrintStream output, PrintStream error) {
    output.printf("Run: %s%n", run.runId());
    output.printf("Software version: %s%n", run.softwareVersion().signature());
    output.printf("Completion: %s%n", run.completion());
    output.printf("Java release: %d%n", run.compilerConfiguration().release().feature());
    output.printf("Java source files: %d%n", run.inputSnapshot().sourceFileCount());
    run.representation().ifPresent(representation -> {
      output.printf("Java types: %d%n", representation.program().types().size());
      output.printf("Java methods: %d%n", representation.program().methods().size());
      output.printf("Analyzed method bodies: %d%n", representation.methodAnalyses().size());
      output.printf("CFG operations: %d%n",
          representation.methodAnalyses().stream().mapToInt(analysis -> analysis.controlFlowGraph().operationNodes().size()).sum());
      output.printf("CFG nodes: %d%n",
          representation.methodAnalyses().stream().mapToInt(analysis -> analysis.controlFlowGraph().nodes().size()).sum());
      output.printf("CFG edges: %d%n",
          representation.methodAnalyses().stream().mapToInt(analysis -> analysis.controlFlowGraph().edges().size()).sum());
    });
    for (var diagnostic : run.diagnostics()) {
      error.printf("%s %s %s: %s%n", diagnostic.severity(), diagnostic.stage(), diagnostic.code(), diagnostic.message());
    }
  }

  private record CommandArguments(Path sourceRoot, JavaRelease release, boolean persist) {
    private static CommandArguments parse(String... arguments) {
      var positional = new ArrayList<String>();
      Optional<JavaRelease> release = Optional.empty();
      var persist = false;
      var index = 0;
      while (index < arguments.length) {
        var argument = arguments[index];
        index++;
        if (PERSIST_OPTION.equals(argument)) {
          if (persist) {
            throw new IllegalArgumentException(PERSIST_OPTION + " may be supplied only once");
          }
          persist = true;
        } else if (RELEASE_OPTION.equals(argument)) {
          if (release.isPresent()) {
            throw new IllegalArgumentException(RELEASE_OPTION + " may be supplied only once");
          }
          if (index >= arguments.length || arguments[index].startsWith("--")) {
            throw new IllegalArgumentException(RELEASE_OPTION + " requires a Java feature-release number");
          }
          release = Optional.of(parseRelease(arguments[index]));
          index++;
        } else if (argument.startsWith("--")) {
          throw new IllegalArgumentException("Unknown option: " + argument);
        } else {
          positional.add(argument);
        }
      }
      if (positional.size() != REQUIRED_POSITIONAL_ARGUMENTS) {
        throw new IllegalArgumentException("Exactly one source root is required");
      }
      if (release.isEmpty()) {
        throw new IllegalArgumentException(RELEASE_OPTION + " is required");
      }
      return new CommandArguments(Path.of(positional.getFirst()), release.orElseThrow(), persist);
    }

    private static JavaRelease parseRelease(String value) {
      final int feature;
      try {
        feature = Integer.parseInt(value);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("Invalid Java release: " + value, exception);
      }
      return JavaRelease.fromFeature(feature);
    }
  }
}
