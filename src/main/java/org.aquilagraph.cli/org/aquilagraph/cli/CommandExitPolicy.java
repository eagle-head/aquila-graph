package org.aquilagraph.cli;

import org.aquilagraph.cli.ChannelEmission.Status;

/** Semantic exit classification independent of report composition and destination adapters. */
public enum CommandExitPolicy {
  ;

  /** Completed analysis and requested publication without observed output failure. */
  public static final int EXIT_COMPLETED = 0;
  /** Incomplete analysis or named environment failure. */
  public static final int EXIT_INCOMPLETE = 1;
  /** Rejected arguments or input. */
  public static final int EXIT_REJECTED = 2;
  /** Publication failed, taking precedence over computation and output classification. */
  public static final int EXIT_REPOSITORY_FAILED = 3;
  /** Semantic success followed by observed output failure. */
  public static final int EXIT_OUTPUT_FAILED = 4;

  static int classify(AnalyzeCommandOutcome outcome, CommandEmission emission) {
    var outputFailed = failed(emission.output());
    var errorFailed = failed(emission.error());
    var semantic = semantic(outcome);
    return semantic == EXIT_COMPLETED && (outputFailed || errorFailed) ? EXIT_OUTPUT_FAILED : semantic;
  }

  private static boolean failed(ChannelEmission observation) {
    return switch (observation) {
      case Status status -> switch (status) {
        case UNUSED, COMPLETED -> false;
        case UNATTEMPTED -> throw new IllegalArgumentException("An ordinary invocation cannot contain unattempted output");
      };
      case ChannelEmission.AbruptFailure _ -> throw new IllegalArgumentException("An abrupt emission cannot be an ordinary invocation");
      case ChannelEmission.IoFailure _ -> true;
      case ChannelEmission.StreamError _ -> true;
    };
  }

  private static int semantic(AnalyzeCommandOutcome outcome) {
    return switch (outcome) {
      case AnalyzeCommandOutcome.UsageRejected _ -> EXIT_REJECTED;
      case AnalyzeCommandOutcome.InputRejected _ -> EXIT_REJECTED;
      case AnalyzeCommandOutcome.EnvironmentFailed _ -> EXIT_INCOMPLETE;
      case AnalyzeCommandOutcome.Executed executed -> {
        if (executed.execution().publication().failure().isPresent()) {
          yield EXIT_REPOSITORY_FAILED;
        }
        yield switch (executed.execution().run().completion()) {
          case COMPLETE -> EXIT_COMPLETED;
          case PARTIAL, FAILED -> EXIT_INCOMPLETE;
          case REJECTED -> EXIT_REJECTED;
        };
      }
    };
  }
}
