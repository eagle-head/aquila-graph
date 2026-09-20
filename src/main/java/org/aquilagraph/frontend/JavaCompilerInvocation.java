package org.aquilagraph.frontend;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aquilagraph.model.CompilerConfiguration;

record JavaCompilerInvocation(List<String> compilerOptions, List<String> analysisOptions) {
  JavaCompilerInvocation {
    compilerOptions = List.copyOf(Objects.requireNonNull(compilerOptions, "compilerOptions"));
    analysisOptions = List.copyOf(Objects.requireNonNull(analysisOptions, "analysisOptions"));
  }

  static JavaCompilerInvocation from(CompilerConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    var compilerOptions = new ArrayList<String>();
    var analysisOptions = new ArrayList<String>();

    addSharedOption(compilerOptions, analysisOptions, "--release", Integer.toString(configuration.release().feature()));
    addSharedOption(compilerOptions, analysisOptions, "-encoding", StandardCharsets.UTF_8.name());
    compilerOptions.add("-proc:none");
    analysisOptions.add("-proc:none");

    compilerOptions.add("--class-path");
    compilerOptions.add(joinPaths(configuration.classPath()));
    if (!configuration.modulePath().isEmpty()) {
      compilerOptions.add("--module-path");
      compilerOptions.add(joinPaths(configuration.modulePath()));
    }

    compilerOptions.addAll(configuration.options());
    analysisOptions.addAll(configuration.options());
    return new JavaCompilerInvocation(compilerOptions, analysisOptions);
  }

  private static void addSharedOption(List<String> compilerOptions, List<String> analysisOptions, String option, String value) {
    compilerOptions.add(option);
    compilerOptions.add(value);
    analysisOptions.add(option);
    analysisOptions.add(value);
  }

  private static String joinPaths(List<Path> paths) {
    return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
  }
}
