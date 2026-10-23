package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.EnvironmentIdentityScheme;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilerModuleLifecycleTest {
  @TempDir Path directory;

  @Test
  void givenModuleObservationAndCloseFailures_whenPublicProductionRuns_thenOriginalIoCauseAndSuppressedCleanupSurvive() throws IOException {
    // Arrange
    var observation = new IOException("Module observation read failed");
    var close = new IOException("Observation manager close failed");
    var closed = new AtomicInteger();
    var compiler = compiler("listLocationsForModules", observation, close, closed);
    var request = request();
    var frontend = frontend(compiler);

    // Act
    var failure = assertThrows(AnalysisEnvironmentException.class, () -> frontend.produce(request));

    // Assert
    assertSame(observation, failure.getCause());
    assertArrayEquals(new Throwable[] {close}, observation.getSuppressed());
    assertEquals(1, closed.get());
  }

  @Test
  void givenUnavailableModuleObservationCapability_whenPublicProductionRuns_thenCheckedEnvironmentFailureRetainsItsCause() throws IOException {
    // Arrange
    for (var operation : List.of("isSupportedOption", "handleOption", "listLocationsForModules")) {
      var unsupported = new UnsupportedOperationException("Module discovery is unavailable");
      var closed = new AtomicInteger();
      var compiler = compiler(operation, unsupported, null, closed);
      var request = request();

      // Act
      var failure = assertThrows(AnalysisEnvironmentException.class, () -> frontend(compiler).produce(request));

      // Assert
      assertEquals(IOException.class, failure.getCause().getClass());
      assertSame(unsupported, failure.getCause().getCause());
      assertEquals(1, closed.get());
    }
  }

  @Test
  void givenObservationViewRejectsConfiguration_whenPublicProductionRuns_thenInputFailureClosesTheAcquiredView() throws IOException {
    // Arrange
    var rejection = new IllegalArgumentException("Configuration is not accepted");
    var closed = new AtomicInteger();
    var compiler = compiler("setLocationFromPaths", rejection, null, closed);
    var request = request();

    // Act
    var failure = assertThrows(AnalysisInputException.class, () -> frontend(compiler).produce(request));

    // Assert
    assertSame(rejection, failure.getCause());
    assertEquals(1, closed.get());
  }

  @Test
  void givenObservationAcquisitionAbruptlyFails_whenPublicProductionRuns_thenTheOriginalFailureRemainsAbrupt() throws IOException {
    // Arrange
    var abrupt = new AssertionError("Observation acquisition aborted");
    var closed = new AtomicInteger();
    var compiler = compiler("getStandardFileManager", abrupt, null, closed);
    var request = request();

    // Act
    var failure = assertThrows(AssertionError.class, () -> frontend(compiler).produce(request));

    // Assert
    assertSame(abrupt, failure);
    assertEquals(0, closed.get());
  }

  private AstProductionRequest request() throws IOException {
    var dependency = Files.createDirectories(directory.resolve("modules"));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(dependency), List.of());
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example {}")));
    return new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  @Test
  void givenUnsupportedTargetViewOptions_whenPublicProductionRuns_thenCaptureFailsAndClosesTheView() throws IOException {
    // Arrange
    var responses = Map.<String, Object>of("isSupportedOption", -1, "handleOption", false);
    for (var response : responses.entrySet()) {
      var closed = new AtomicInteger();
      var request = request();
      var compiler = compiler(response.getKey(), response.getValue(), null, closed);

      // Act
      var failure = assertThrows(AnalysisEnvironmentException.class, () -> frontend(compiler).produce(request));

      // Assert
      assertEquals(IOException.class, failure.getCause().getClass());
      assertEquals("The compiler file manager cannot configure target-selected multi-release module observation", failure.getCause().getMessage());
      assertEquals(1, closed.get());
    }
  }

  @Test
  void givenAReadOnlyModuleView_whenDependenciesAreCaptured_thenNoCompilationTaskIsAcquiredAndTheViewCloses() throws IOException {
    // Arrange
    var closed = new AtomicInteger();
    var compiler = compiler("getTask", new AssertionError("Dependency observation cannot acquire a compilation task"), null, closed);
    var configuration = request().configuration();
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());

    // Act
    var environment = capture.capture(compiler, configuration, JavaCompilerInvocation.from(configuration).analysisOptions());

    // Assert
    assertEquals(EnvironmentIdentityScheme.MODULE_SEMANTICS, environment.identityScheme());
    assertEquals(DependencySemantics.State.MODULES, environment.modulePath().getFirst().semantics().state());
    assertEquals(1, closed.get());
  }

  private static JavaFrontend frontend(JavaCompiler compiler) {
    return new JavaFrontend(new JavaSourceLoader(),
        new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), () -> compiler));
  }

  private static JavaCompiler compiler(String operation, Object response, IOException closeFailure, AtomicInteger closed) {
    var delegate = ToolProvider.getSystemJavaCompiler();
    return (JavaCompiler) Proxy.newProxyInstance(JavaCompiler.class.getClassLoader(), new Class<?>[] {JavaCompiler.class},
        (_, method, arguments) -> {
          if (method.getName().equals(operation)) {
            if (response instanceof Throwable failure) { throw failure; }
            return response;
          }
          if (method.getName().equals("getStandardFileManager")) {
            var manager = (StandardJavaFileManager) method.invoke(delegate, arguments);
            return Proxy.newProxyInstance(StandardJavaFileManager.class.getClassLoader(), new Class<?>[] {StandardJavaFileManager.class},
                (_, managerMethod, managerArguments) -> {
                  if (managerMethod.getName().equals("close")) {
                    manager.close();
                    closed.incrementAndGet();
                    if (closeFailure != null) { throw closeFailure; }
                    return null;
                  }
                  if (managerMethod.getName().equals(operation)) {
                    if (response instanceof Throwable failure) { throw failure; }
                    return response;
                  }
                  try { return managerMethod.invoke(manager, managerArguments); }
                  catch (InvocationTargetException exception) { throw exception.getCause(); }
                });
          }
          try { return method.invoke(delegate, arguments); }
          catch (InvocationTargetException exception) { throw exception.getCause(); }
        });
  }
}
