package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Complete caller configuration for one joint production or test compilation. */
record CompilationRequest(Path root, String batch, Path sources, Path resources, Path output, boolean force, List<String> arguments,
    Map<String, String> environment) {
  private static final int HEADER_ARGUMENTS = 6;
  static final String TEST_BATCH = "tests";

  CompilationRequest {
    arguments = List.copyOf(arguments);
    environment = Map.copyOf(environment);
    if (!List.of("production", TEST_BATCH).contains(batch)) {
      throw new IllegalArgumentException("Unknown compilation batch: " + batch);
    }
  }

  static CompilationRequest parse(String... arguments) throws IOException {
    if (arguments.length <= HEADER_ARGUMENTS) {
      throw new IllegalArgumentException("Compilation requires root, batch, sources, resources, output, force and compiler arguments");
    }
    var root = Path.of(arguments[0]).toRealPath();
    var force = arguments[5];
    if (!List.of("true", "false").contains(force)) {
      throw new IllegalArgumentException("Compilation force must be true or false");
    }
    return new CompilationRequest(root, arguments[1], root.resolve(arguments[2]).normalize(), root.resolve(arguments[3]).normalize(),
        PreparationFiles.owned(root, root.resolve(arguments[4])), Boolean.parseBoolean(force),
        List.of(arguments).subList(HEADER_ARGUMENTS, arguments.length), System.getenv());
  }

  Path state() throws IOException {
    return PreparationFiles.owned(root, root.resolve("target/compilation").resolve(batch));
  }

  List<String> effectiveArguments() throws IOException {
    var result = new ArrayList<>(environmentArguments(environment));
    for (var argument : arguments) {
      if (argument.startsWith("@")) {
        var file = root.resolve(argument.substring(1));
        var text = Files.readString(file);
        if (text.contains("\\\n") || text.contains("\\\r")) {
          throw new IOException("Compilation argument files require literal elements without continuations: " + file);
        }
        var elements = ArgumentWords.parse(text);
        if (elements.stream().anyMatch(value -> value.startsWith("@"))) {
          throw new IOException("Nested compilation argument files are unsupported: " + file);
        }
        result.addAll(elements);
      } else {
        result.add(argument);
      }
    }
    return List.copyOf(result);
  }

  static List<String> environmentArguments(Map<String, String> environment) {
    for (var key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) {
      if (!environment.getOrDefault(key, "").isBlank()) {
        throw new IllegalArgumentException("Unsupported compilation environment: " + key);
      }
    }
    var result = ArgumentWords.parse(environment.getOrDefault("JDK_JAVAC_OPTIONS", ""));
    for (var option : result) {
      if (!List.of("-parameters", "-g", "-g:lines,vars,source").contains(option)) {
        throw new IllegalArgumentException(
            "Unsupported JDK_JAVAC_OPTIONS; supported elements are -parameters, -g and -g:lines,vars,source");
      }
    }
    return result;
  }
}
