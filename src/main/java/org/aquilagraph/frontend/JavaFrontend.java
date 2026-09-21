package org.aquilagraph.frontend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.application.port.AnalysisEnvironmentException;
import org.aquilagraph.application.port.AnalysisInputException;
import org.aquilagraph.application.port.SourceAnalyzer;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.syntax.SyntaxAnalysisInput;

/** Public facade for source discovery and Java analysis. */
public final class JavaFrontend implements SourceAnalyzer {
  private final JavaSourceLoader sourceLoader;
  private final JavaAnalysisPipeline analysisPipeline;

  /**
   * Creates a frontend with an explicit source loader.
   *
   * @param sourceLoader
   *          deterministic source snapshot loader
   */
  public JavaFrontend(JavaSourceLoader sourceLoader) {
    this.sourceLoader = Objects.requireNonNull(sourceLoader, "sourceLoader");
    analysisPipeline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()));
  }

  /**
   * Discovers and analyzes sources beneath a root directory.
   *
   * @param sourceRoot
   *          source directory
   * @param configuration
   *          compiler configuration
   * @return declarations, transient syntax evidence, environment identity, and diagnostics
   * @throws AnalysisInputException
   *           if the source input cannot be captured
   * @throws AnalysisEnvironmentException
   *           if compiler dependencies or the active compiler environment cannot be read
   */
  @Override
  public SyntaxAnalysisInput analyze(Path sourceRoot, CompilerConfiguration configuration) {
    Objects.requireNonNull(sourceRoot, "sourceRoot");
    Objects.requireNonNull(configuration, "configuration");
    final InputSnapshot snapshot;
    try {
      snapshot = sourceLoader.discover(sourceRoot);
    } catch (IOException | IllegalArgumentException exception) {
      throw new AnalysisInputException("Java source input could not be captured: " + exception.getMessage(), exception);
    }

    try {
      return analyze(snapshot, configuration);
    } catch (IOException exception) {
      throw new AnalysisEnvironmentException("Java analysis environment could not be captured consistently: " + exception.getMessage(),
          exception);
    }
  }

  /**
   * Analyzes an existing immutable input snapshot.
   *
   * @param snapshot
   *          captured source input
   * @param configuration
   *          compiler configuration
   * @return declarations, transient syntax evidence, environment identity, and diagnostics
   * @throws IOException
   *           if compiler dependencies cannot be captured
   */
  public SyntaxAnalysisInput analyze(InputSnapshot snapshot, CompilerConfiguration configuration) throws IOException {
    return analysisPipeline.analyze(snapshot, configuration);
  }
}
