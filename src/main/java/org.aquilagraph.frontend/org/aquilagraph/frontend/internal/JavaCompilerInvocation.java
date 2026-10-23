package org.aquilagraph.frontend.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;

record JavaCompilerInvocation(List<String> compilerOptions, List<String> analysisOptions, JavaRelease release) {
  private static final String MULTI_RELEASE_OPTION = "--multi-release";

  JavaCompilerInvocation {
    compilerOptions = List.copyOf(Objects.requireNonNull(compilerOptions, "compilerOptions"));
    analysisOptions = List.copyOf(Objects.requireNonNull(analysisOptions, "analysisOptions"));
    Objects.requireNonNull(release, "release");
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
    return new JavaCompilerInvocation(compilerOptions, analysisOptions, configuration.release());
  }

  void configureModuleView(StandardJavaFileManager manager, Path entry) throws IOException {
    if (manager.isSupportedOption(MULTI_RELEASE_OPTION) != 1
        || !manager.handleOption(MULTI_RELEASE_OPTION, List.of(Integer.toString(release.feature())).iterator())) {
      throw new IOException("The compiler file manager cannot configure target-selected multi-release module observation");
    }
    manager.setLocationFromPaths(StandardLocation.MODULE_PATH, List.of(entry));
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
