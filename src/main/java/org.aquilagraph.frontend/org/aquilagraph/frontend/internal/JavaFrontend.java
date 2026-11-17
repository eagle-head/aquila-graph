package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.util.Objects;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.AnalysisObserver;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.AstProducer;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.SourceInput;

/** Compiler-backed producer for source discovery and admitted Java analysis. */
public final class JavaFrontend implements AstProducer {
  private final JavaSourceLoader sourceLoader;
  private final JavaAnalysisPipeline analysisPipeline;
  private final AnalysisObserver observer;

  /**
   * Creates a frontend with an explicit source loader.
   *
   * @param sourceLoader
   *          deterministic source snapshot loader
   */
  public JavaFrontend(JavaSourceLoader sourceLoader) {
    this(sourceLoader, AnalysisObserver.none());
  }

  /**
   * Creates a frontend with host-owned observations.
   *
   * @param sourceLoader
   *          deterministic snapshot loader
   * @param observer
   *          observation backend
   */
  public JavaFrontend(JavaSourceLoader sourceLoader, AnalysisObserver observer) {
    this.sourceLoader = Objects.requireNonNull(sourceLoader, "sourceLoader");
    this.observer = Objects.requireNonNull(observer, "observer");
    analysisPipeline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()),
        ToolProvider::getSystemJavaCompiler, observer);
  }

  JavaFrontend(JavaSourceLoader sourceLoader, JavaAnalysisPipeline analysisPipeline) {
    this.sourceLoader = Objects.requireNonNull(sourceLoader, "sourceLoader");
    this.analysisPipeline = Objects.requireNonNull(analysisPipeline, "analysisPipeline");
    observer = AnalysisObserver.none();
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
      return JavaAnalysisStage.measure(observer, "production", snapshot, request.profile(),
          () -> analysisPipeline.produce(snapshot, request.configuration(), policy));
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
    return JavaAnalysisStage.measure(observer, "production", snapshot, profile,
        () -> analysisPipeline.produce(snapshot, configuration, JavaTranslationPolicy.forProfile(profile)));
  }

}
