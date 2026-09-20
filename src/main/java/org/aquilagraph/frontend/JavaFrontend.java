package org.aquilagraph.frontend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.FrontendResult;
import org.aquilagraph.model.InputSnapshot;

public final class JavaFrontend {
  private final JavaSourceLoader sourceLoader;
  private final JavaCompilerAnalyzer compilerAnalyzer;

  public JavaFrontend(JavaSourceLoader sourceLoader) {
    this.sourceLoader = Objects.requireNonNull(sourceLoader, "sourceLoader");
    compilerAnalyzer = new JavaCompilerAnalyzer(new AnalysisEnvironmentCapture());
  }

  public FrontendResult analyze(Path sourceRoot, CompilerConfiguration configuration)
      throws IOException {
    Objects.requireNonNull(configuration, "configuration");
    return analyze(sourceLoader.discover(sourceRoot), configuration);
  }

  public FrontendResult analyze(InputSnapshot snapshot, CompilerConfiguration configuration)
      throws IOException {
    return compilerAnalyzer.analyze(snapshot, configuration);
  }
}
