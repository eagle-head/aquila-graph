package org.aquilagraph.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.identity.api.JavaRelease;

/** Parses command-line tokens into validated analysis arguments. */
final class AnalyzeCommandParser {
  static final String USAGE = "Usage: aquila-graph <source-root> [--release <feature>] [--persist]";

  private static final int REQUIRED_SOURCE_ROOT_COUNT = 1;
  private static final String PERSIST_OPTION = "--persist";
  private static final String RELEASE_OPTION = "--release";

  private final JavaRelease defaultRelease;

  AnalyzeCommandParser(JavaRelease defaultRelease) {
    this.defaultRelease = Objects.requireNonNull(defaultRelease, "defaultRelease");
  }

  Result parse(String... arguments) {
    var cursor = new ArgumentCursor(Objects.requireNonNull(arguments, "arguments"));
    var state = new ParseState(defaultRelease);
    while (cursor.hasNext()) {
      var token = cursor.next();
      var tokenResult = switch (token) {
        case PERSIST_OPTION -> state.enablePersistence();
        case RELEASE_OPTION -> parseRelease(cursor, state);
        default -> state.acceptPositionalOrUnknown(token);
      };
      if (tokenResult instanceof Rejected rejection) {
        return rejection;
      }
    }
    return state.finish();
  }

  private static TokenResult parseRelease(ArgumentCursor cursor, ParseState state) {
    if (state.hasExplicitRelease()) {
      return rejected(RELEASE_OPTION + " may be supplied only once");
    }
    if (!cursor.hasNext() || cursor.peek().startsWith("--")) {
      return rejected(RELEASE_OPTION + " requires a Java feature-release number");
    }

    var value = cursor.next();
    final int feature;
    try {
      feature = Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return rejected("Invalid Java release: " + value);
    }

    if (!JavaRelease.supportsFeature(feature)) {
      return rejected(JavaRelease.unsupportedFeatureMessage(feature));
    }
    state.useRelease(JavaRelease.fromFeature(feature));
    return Accepted.INSTANCE;
  }

  private static Rejected rejected(String message) {
    return new Rejected(message);
  }

  sealed interface Result permits Parsed, Rejected {
  }

  sealed interface TokenResult permits Accepted, Rejected {
  }

  private enum Accepted implements TokenResult {
    INSTANCE
  }

  record Parsed(AnalyzeCommandArguments arguments) implements Result {
    Parsed {
      Objects.requireNonNull(arguments, "arguments");
    }
  }

  record Rejected(String message) implements Result, TokenResult {
    Rejected {
      Objects.requireNonNull(message, "message");
    }
  }

  private static final class ArgumentCursor {
    private final List<String> arguments;
    private int index;

    ArgumentCursor(String... argumentValues) {
      Objects.requireNonNull(argumentValues, "argumentValues");
      var validatedArguments = new ArrayList<String>(argumentValues.length);
      for (var argument : argumentValues) {
        validatedArguments.add(Objects.requireNonNull(argument, "argument"));
      }
      arguments = validatedArguments;
    }

    boolean hasNext() {
      return index < arguments.size();
    }

    String peek() {
      return Objects.requireNonNull(arguments.get(index), "argument");
    }

    String next() {
      var argument = Objects.requireNonNull(arguments.get(index), "argument");
      index++;
      return argument;
    }
  }

  private static final class ParseState {
    private final List<String> positionalArguments = new ArrayList<>(1);
    private JavaRelease release;
    private boolean explicitRelease;
    private boolean persistenceEnabled;

    ParseState(JavaRelease defaultRelease) {
      release = defaultRelease;
    }

    TokenResult enablePersistence() {
      if (persistenceEnabled) {
        return rejected(PERSIST_OPTION + " may be supplied only once");
      }
      persistenceEnabled = true;
      return Accepted.INSTANCE;
    }

    TokenResult acceptPositionalOrUnknown(String token) {
      if (token.startsWith("--")) {
        return rejected("Unknown option: " + token);
      }
      positionalArguments.add(token);
      return Accepted.INSTANCE;
    }

    boolean hasExplicitRelease() {
      return explicitRelease;
    }

    void useRelease(JavaRelease selectedRelease) {
      release = Objects.requireNonNull(selectedRelease, "selectedRelease");
      explicitRelease = true;
    }

    Result finish() {
      if (positionalArguments.size() != REQUIRED_SOURCE_ROOT_COUNT) {
        return new Rejected("Exactly one source root is required");
      }
      try {
        var savePolicy = persistenceEnabled ? PublicationIntent.SAVE_COMPLETED : PublicationIntent.DO_NOT_SAVE;
        var sourceRoot = Objects.requireNonNull(Path.of(positionalArguments.getFirst()), "sourceRoot");
        return new Parsed(new AnalyzeCommandArguments(sourceRoot, release, savePolicy));
      } catch (InvalidPathException exception) {
        return new Rejected("Invalid source root: " + exception.getInput());
      }
    }
  }
}
