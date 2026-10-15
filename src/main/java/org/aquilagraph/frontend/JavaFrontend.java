package org.aquilagraph.frontend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.SourceAnalyzer;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstProducer;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.SourceInput;

/** Public facade for source discovery and Java analysis. */
public final class JavaFrontend implements AstProducer, SourceAnalyzer {
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

  JavaFrontend(JavaSourceLoader sourceLoader, JavaAnalysisPipeline analysisPipeline) {
    this.sourceLoader = Objects.requireNonNull(sourceLoader, "sourceLoader");
    this.analysisPipeline = Objects.requireNonNull(analysisPipeline, "analysisPipeline");
  }

  /**
   * Produces a source-qualified result without graph derivation.
   *
   * @param request
   *          source capture and representation request
   * @return admitted source outcome after normal compiler cleanup and environment verification
   */
  @Override
  public AstOutcome produce(AstProductionRequest request) {
    Objects.requireNonNull(request, "request");
    var policy = JavaTranslationPolicy.forProfile(request.profile());
    final InputSnapshot snapshot;
    try {
      snapshot = switch (request.input()) {
        case SourceInput.Root root -> sourceLoader.discover(root.path());
        case SourceInput.Snapshot captured -> captured.value();
      };
    } catch (IOException | IllegalArgumentException exception) {
      throw new AnalysisInputException("Java source input could not be captured: " + exception.getMessage(), exception);
    }
    try {
      return analysisPipeline.produce(snapshot, request.configuration(), policy);
    } catch (IOException exception) {
      throw new AnalysisEnvironmentException("Java analysis environment could not be captured consistently: " + exception.getMessage(),
          exception);
    }
  }

  /**
   * Produces source facts from an existing immutable capture.
   *
   * @param snapshot
   *          captured source values
   * @param configuration
   *          compiler configuration
   * @param profile
   *          source representation profile
   * @return independently source-qualified outcome
   * @throws IOException
   *           if compiler dependencies cannot be captured consistently
   */
  public AstOutcome produce(InputSnapshot snapshot, CompilerConfiguration configuration, AstProfile profile) throws IOException {
    return analysisPipeline.produce(snapshot, configuration, JavaTranslationPolicy.forProfile(profile));
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
    var policy = JavaTranslationPolicy.legacyAnalysis();
    final InputSnapshot snapshot;
    try {
      snapshot = sourceLoader.discover(sourceRoot);
    } catch (IOException | IllegalArgumentException exception) {
      throw new AnalysisInputException("Java source input could not be captured: " + exception.getMessage(), exception);
    }

    try {
      return analysisPipeline.analyze(snapshot, configuration, policy);
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
    return analysisPipeline.analyze(snapshot, configuration, JavaTranslationPolicy.legacyAnalysis());
  }
}
