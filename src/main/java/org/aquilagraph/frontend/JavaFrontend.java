package org.aquilagraph.frontend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.FrontendResult;
import org.aquilagraph.model.InputSnapshot;

/** Owns source discovery, compiler invocation, and translation into the analysis model. */
public final class JavaFrontend {
  private final JavaSourceLoader sourceLoader;
  private final JavaCompilerAnalyzer compilerAnalyzer;

  /**
   * Creates a frontend with an explicit source loader.
   *
   * @param sourceLoader
   *          deterministic source snapshot loader
   */
  public JavaFrontend(JavaSourceLoader sourceLoader) {
    this.sourceLoader = Objects.requireNonNull(sourceLoader, "sourceLoader");
    compilerAnalyzer = new JavaCompilerAnalyzer(new AnalysisEnvironmentCapture());
  }

  /**
   * Discovers and analyzes sources beneath a root directory.
   *
   * @param sourceRoot
   *          source directory
   * @param configuration
   *          compiler configuration
   * @return translated program and diagnostics
   * @throws IOException
   *           if input or compiler dependencies cannot be read
   */
  public FrontendResult analyze(Path sourceRoot, CompilerConfiguration configuration) throws IOException {
    Objects.requireNonNull(configuration, "configuration");
    return analyze(sourceLoader.discover(sourceRoot), configuration);
  }

  /**
   * Analyzes an existing immutable input snapshot.
   *
   * @param snapshot
   *          captured source input
   * @param configuration
   *          compiler configuration
   * @return translated program and diagnostics
   * @throws IOException
   *           if compiler dependencies cannot be captured
   */
  public FrontendResult analyze(InputSnapshot snapshot, CompilerConfiguration configuration) throws IOException {
    return compilerAnalyzer.analyze(snapshot, configuration);
  }
}
