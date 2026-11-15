package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.tools.JavaCompiler;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencyKind;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;

final class AnalysisEnvironmentCapture {
  private final DependencyContentCapture dependencyCapture;
  private final CompilerModuleCapture moduleCapture = new CompilerModuleCapture();

  AnalysisEnvironmentCapture(DependencyContentCapture dependencyCapture) {
    this.dependencyCapture = Objects.requireNonNull(dependencyCapture, "dependencyCapture");
  }

  AnalysisEnvironmentSnapshot capture(JavaCompiler compiler, CompilerConfiguration configuration, List<String> analysisOptions)
      throws IOException {
    Objects.requireNonNull(compiler, "compiler");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(analysisOptions, "analysisOptions");
    return new AnalysisEnvironmentSnapshot(configuration, compiler.getClass().getName(), requiredSystemProperty("java.vendor"),
        requiredSystemProperty("java.runtime.version"), analysisOptions,
        captureDependencies(compiler, configuration, configuration.classPath(), false),
        captureDependencies(compiler, configuration, configuration.modulePath(), true));
  }

  private List<DependencySnapshot> captureDependencies(JavaCompiler compiler, CompilerConfiguration configuration,
      List<CapturedLocation> locations, boolean modular) throws IOException {
    var physicalCaptures = new ArrayList<DependencyOccurrence>(locations.size());
    for (var location : locations) {
      final Path path;
      try {
        path = NativeLocations.toNative(location);
      } catch (IllegalArgumentException exception) {
        throw new AnalysisInputException("The compiler rejected the analysis configuration", exception);
      }
      physicalCaptures.add(new DependencyOccurrence(location, path, dependencyCapture.capture(path)));
    }
    var dependencies = new ArrayList<DependencySnapshot>(physicalCaptures.size());
    for (var capture : physicalCaptures) {
      var physical = capture.physical();
      var semantics = semantics(compiler, configuration, capture.path(), physical.kind(), modular);
      dependencies.add(new DependencySnapshot(capture.location(), physical.kind(), physical.byteCount(), physical.entryCount(),
          physical.fingerprint(), semantics));
    }
    return List.copyOf(dependencies);
  }

  private DependencySemantics semantics(JavaCompiler compiler, CompilerConfiguration configuration, Path path, DependencyKind kind,
      boolean modular) throws IOException {
    if (!modular) {
      return new DependencySemantics(DependencySemantics.State.CLASS_PATH, List.of());
    }
    return kind == DependencyKind.MISSING
        ? new DependencySemantics(DependencySemantics.State.MISSING, List.of())
        : moduleCapture.observe(compiler, configuration.release(), path);
  }

  void verifyUnchanged(JavaCompiler compiler, CompilerConfiguration configuration, List<String> analysisOptions,
      AnalysisEnvironmentSnapshot expected) throws IOException {
    Objects.requireNonNull(expected, "expected");
    var actual = capture(compiler, configuration, analysisOptions);
    if (!expected.equals(actual)) {
      throw new IOException("Compiler dependencies changed during analysis");
    }
  }

  private static String requiredSystemProperty(String name) {
    var value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new AnalysisEnvironmentException("Required system property is unavailable: " + name);
    }
    return value;
  }

  @FunctionalInterface
  interface DependencyContentCapture {
    PhysicalDependency capture(Path location) throws IOException;
  }

  private record DependencyOccurrence(CapturedLocation location, Path path, PhysicalDependency physical) {
  }
}
