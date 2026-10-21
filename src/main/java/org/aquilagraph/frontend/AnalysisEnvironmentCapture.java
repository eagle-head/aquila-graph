package org.aquilagraph.frontend;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.tools.JavaCompiler;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.EnvironmentIdentityScheme;

final class AnalysisEnvironmentCapture {
  private final CompilerDependencyCapture dependencyCapture;
  private final CompilerModuleCapture moduleCapture = new CompilerModuleCapture();

  AnalysisEnvironmentCapture(CompilerDependencyCapture dependencyCapture) {
    this.dependencyCapture = Objects.requireNonNull(dependencyCapture, "dependencyCapture");
  }

  AnalysisEnvironmentSnapshot capture(JavaCompiler compiler, CompilerConfiguration configuration, List<String> analysisOptions)
      throws IOException {
    Objects.requireNonNull(compiler, "compiler");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(analysisOptions, "analysisOptions");
    return new AnalysisEnvironmentSnapshot(configuration, compiler.getClass().getName(), requiredSystemProperty("java.vendor"),
        requiredSystemProperty("java.runtime.version"), analysisOptions,
        moduleCapture.capture(compiler, configuration, dependencyCapture.capture(configuration.classPath()), false),
        moduleCapture.capture(compiler, configuration, dependencyCapture.capture(configuration.modulePath()), true),
        EnvironmentIdentityScheme.MODULE_SEMANTICS);
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
}
