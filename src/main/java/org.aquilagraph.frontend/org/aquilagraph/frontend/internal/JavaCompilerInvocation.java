package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AnalysisInputException;

record JavaCompilerInvocation(List<String> compilerOptions, List<String> analysisOptions, JavaRelease release, List<Path> classPath,
    List<Path> modulePath) {
  private static final String MULTI_RELEASE_OPTION = "--multi-release";

  JavaCompilerInvocation {
    compilerOptions = List.copyOf(Objects.requireNonNull(compilerOptions, "compilerOptions"));
    analysisOptions = List.copyOf(Objects.requireNonNull(analysisOptions, "analysisOptions"));
    Objects.requireNonNull(release, "release");
    classPath = List.copyOf(classPath);
    modulePath = List.copyOf(modulePath);
  }

  static JavaCompilerInvocation from(CompilerConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    var compilerOptions = new ArrayList<String>();
    var analysisOptions = new ArrayList<String>();

    addSharedOption(compilerOptions, analysisOptions, "--release", Integer.toString(configuration.release().feature()));
    addSharedOption(compilerOptions, analysisOptions, "-encoding", StandardCharsets.UTF_8.name());
    compilerOptions.add("-proc:none");
    analysisOptions.add("-proc:none");

    compilerOptions.addAll(configuration.options());
    analysisOptions.addAll(configuration.options());
    return new JavaCompilerInvocation(compilerOptions, analysisOptions, configuration.release(), nativePaths(configuration.classPath()),
        nativePaths(configuration.modulePath()));
  }

  void configurePaths(StandardJavaFileManager manager) throws IOException {
    // Keep class lookup within the explicitly configured dependency entries.
    manager.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
    if (!modulePath.isEmpty()) {
      manager.setLocationFromPaths(StandardLocation.MODULE_PATH, modulePath);
    }
  }

  static void configureModuleView(StandardJavaFileManager manager, JavaRelease release, Path entry) throws IOException {
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

  private static List<Path> nativePaths(List<CapturedLocation> locations) {
    try {
      return locations.stream().map(NativeLocations::toNative).toList();
    } catch (IllegalArgumentException exception) {
      throw new AnalysisInputException("The compiler rejected the analysis configuration", exception);
    }
  }
}
