package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.api.Test;

@Isolated
class JavaFrontendLifetimeTest {
  private static final String SOURCE = "class Sample { int read() { return 1; } }";
  private static final String INVALID_COMPILER_OPTION = "-invalid-aquila-compiler-option";
  private static final String PATH_ENVIRONMENT_FAILURE = "Java analysis environment could not be captured consistently: ";
  @TempDir Path temporaryDirectory;

  @Test
  void givenEmptySourcesAndTaskInvalidOptionWithARecaptureFault_whenSnapshotEntryAnalyzes_thenInitialEnvironmentIsCapturedAndTaskAndRecaptureAreSkipped()
      throws IOException {
    // Arrange
    var dependency = dependencyDirectory("dependency");
    try (var observation = new DependencyFileSystemObservation(dependency)) {
      observation.inputOpenFailureAttempt = 2;
      observation.inputOpenFailure = new IOException("Unexpected recapture");
      var configuration = configuration(observation.observe(dependency), List.of(INVALID_COMPILER_OPTION));
      var snapshot = InputSnapshot.fromSources(temporaryDirectory, List.of());
      var frontend = new JavaFrontend(new JavaSourceLoader());

      // Act
      var input = assertDoesNotThrow(() -> frontend.analyze(snapshot, configuration));

      // Assert
      assertEquals(snapshot, input.snapshot());
      assertEquals(List.of(), input.program().types());
      assertEquals(List.of(), input.diagnostics());
      assertEquals(List.of(INVALID_COMPILER_OPTION), input.environmentSnapshot().compilerConfiguration().options());
      assertEquals(1, observation.directoryOpenAttempts);
      assertEquals(1, observation.directoryCloseAttempts);
      assertEquals(1, observation.inputOpenAttempts);
      assertEquals(1, observation.inputCloseAttempts);
    }
  }

  @Test
  void givenNonemptySourcesAndTheSameTaskInvalidOption_whenSnapshotEntryAnalyzes_thenTaskRefusalOccursAfterInitialCaptureAndPreventsRecapture()
      throws IOException {
    // Arrange
    var dependency = dependencyDirectory("dependency");
    try (var observation = new DependencyFileSystemObservation(dependency)) {
      observation.inputOpenFailureAttempt = 2;
      observation.inputOpenFailure = new IOException("Unexpected recapture");
      var configuration = configuration(observation.observe(dependency), List.of(INVALID_COMPILER_OPTION));
      var snapshot = snapshot(temporaryDirectory);
      var frontend = new JavaFrontend(new JavaSourceLoader());

      // Act
      var failure = assertThrows(AnalysisInputException.class, () -> frontend.analyze(snapshot, configuration));

      // Assert
      assertEquals("The compiler rejected the analysis configuration", failure.getMessage());
      var compilerFailure = assertInstanceOf(IllegalArgumentException.class, failure.getCause());
      assertTrue(compilerFailure.getMessage().contains(INVALID_COMPILER_OPTION));
      assertEquals(0, failure.getSuppressed().length);
      assertEquals(1, observation.inputOpenAttempts);
      assertEquals(1, observation.inputCloseAttempts);
      assertEquals(1, observation.directoryCloseAttempts);
    }
  }

  @Test
  void givenRealCompilerWorkAndRecaptureReadFailure_whenEitherPublicEntryAnalyzes_thenNoInputReturnsAndEachIoFamilyRetainsTheOriginalCause()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var sourceRoot = Files.createDirectory(temporaryDirectory.resolve(pathEntry ? "path-sources" : "snapshot-sources"));
      Files.writeString(sourceRoot.resolve("Sample.java"), SOURCE);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      var readFailure = new IOException("Dependency read failed during recapture");
      try (var observation = new DependencyFileSystemObservation(dependency)) {
        observation.inputReadFailureAttempt = 2;
        observation.inputReadFailure = readFailure;
        var configuration = configuration(observation.observe(dependency), List.of());
        var snapshot = snapshot(sourceRoot);
        var frontend = new JavaFrontend(new JavaSourceLoader());

        // Act
        if (pathEntry) {
          var failure = assertThrows(AnalysisEnvironmentException.class, () -> frontend.analyze(sourceRoot, configuration));

          // Assert
          assertSame(readFailure, failure.getCause());
          assertEquals("Java analysis environment could not be captured consistently: " + readFailure.getMessage(), failure.getMessage());
        } else {
          var failure = assertThrows(IOException.class, () -> frontend.analyze(snapshot, configuration));

          // Assert
          assertSame(readFailure, failure);
        }
        assertEquals(2, observation.directoryOpenAttempts);
        assertEquals(2, observation.directoryCloseAttempts);
        assertEquals(2, observation.inputOpenAttempts);
        assertEquals(2, observation.inputCloseAttempts);
        assertEquals(0, readFailure.getSuppressed().length);
      }
    }
  }

  @Test
  void givenRealCompilerWorkAndRecaptureOpenFailure_whenSnapshotEntryAnalyzes_thenCheckedFailurePreventsReturnWithoutClosingAnUnacquiredStream()
      throws IOException {
    // Arrange
    var dependency = dependencyDirectory("dependency");
    var openFailure = new IOException("Dependency open failed during recapture");
    try (var observation = new DependencyFileSystemObservation(dependency)) {
      observation.inputOpenFailureAttempt = 2;
      observation.inputOpenFailure = openFailure;
      var configuration = configuration(observation.observe(dependency), List.of());
      var snapshot = snapshot(temporaryDirectory);
      var frontend = new JavaFrontend(new JavaSourceLoader());

      // Act
      var failure = assertThrows(IOException.class, () -> frontend.analyze(snapshot, configuration));

      // Assert
      assertSame(openFailure, failure);
      assertEquals(2, observation.inputOpenAttempts);
      assertEquals(1, observation.inputCloseAttempts);
      assertEquals(2, observation.directoryCloseAttempts);
      assertEquals(0, failure.getSuppressed().length);
    }
  }

  @Test
  void givenRealAttributedCompilerWork_whenEitherEntryReturns_thenOwnedCloseCompletesBeforeTheIndependentRecaptureRead()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", false);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        observation.inputOpenObserver = attempt -> compiler.timeline.add("dependency-read-" + attempt);
        var configuration = configuration(observation.observe(dependency), List.of());
        var frontend = observedFrontend(compiler);

        // Act
        var input = analyze(frontend, root, configuration, pathEntry, false);

        // Assert
        assertEquals("Sample", input.program().types().getFirst().qualifiedName());
        assertEquals(1, input.program().types().getFirst().methods().stream().filter(method -> method.name().equals("read")).count());
        assertEquals(1, compiler.selections);
        assertEquals(1, compiler.managerRequests);
        assertEquals(1, compiler.taskRequests);
        assertEquals(1, compiler.closeAttempts);
        assertTrue(compiler.delegateCloseCompleted);
        assertEquals(2, observation.inputOpenAttempts);
        assertBefore(compiler.timeline, "parse-complete", "manager-close-complete");
        assertBefore(compiler.timeline, "analysis-complete", "manager-close-complete");
        assertBefore(compiler.timeline, "manager-close-complete", "dependency-read-2");
      }
    }
  }

  @Test
  void givenRealAttributedWorkAndInjectedManagerCloseFailure_whenEitherEntryAnalyzes_thenCloseFailurePreventsRecaptureAndInput()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", false);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      var closeFailure = new IOException("Injected manager close failure");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        compiler.closeFailure = closeFailure;
        observation.inputOpenObserver = attempt -> compiler.timeline.add("dependency-read-" + attempt);
        var configuration = configuration(observation.observe(dependency), List.of());
        var frontend = observedFrontend(compiler);

        // Act
        if (pathEntry) {
          var failure = assertThrows(AnalysisEnvironmentException.class, () -> analyze(frontend, root, configuration, true, false));

          // Assert
          assertSame(closeFailure, failure.getCause());
          assertEquals(PATH_ENVIRONMENT_FAILURE + closeFailure.getMessage(), failure.getMessage());
        } else {
          var failure = assertThrows(IOException.class, () -> analyze(frontend, root, configuration, false, false));

          // Assert
          assertSame(closeFailure, failure);
        }
        assertEquals(0, closeFailure.getSuppressed().length);
        assertEquals(1, compiler.closeAttempts);
        assertTrue(compiler.delegateCloseCompleted);
        assertEquals(1, observation.inputOpenAttempts);
        assertBefore(compiler.timeline, "analysis-complete", "manager-close-injected-failure");
      }
    }
  }

  @Test
  void givenActualParsingThenInjectedWorkAndCloseFailures_whenEitherEntryAnalyzes_thenWorkStaysPrimaryAndRecaptureIsSkipped()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", false);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      var workFailure = new IOException("Injected work failure after actual parsing");
      var closeFailure = new IOException("Injected manager close failure");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        compiler.workFailure = workFailure;
        compiler.closeFailure = closeFailure;
        var configuration = configuration(observation.observe(dependency), List.of());
        var frontend = observedFrontend(compiler);

        // Act
        if (pathEntry) {
          var failure = assertThrows(AnalysisEnvironmentException.class, () -> analyze(frontend, root, configuration, true, false));

          // Assert
          assertSame(workFailure, failure.getCause());
          assertEquals(PATH_ENVIRONMENT_FAILURE + workFailure.getMessage(), failure.getMessage());
          assertEquals(0, failure.getSuppressed().length);
        } else {
          var failure = assertThrows(IOException.class, () -> analyze(frontend, root, configuration, false, false));

          // Assert
          assertSame(workFailure, failure);
        }
        assertEquals(List.of(closeFailure), List.of(workFailure.getSuppressed()));
        assertEquals(1, compiler.parsedUnits);
        assertEquals(1, compiler.closeAttempts);
        assertTrue(compiler.delegateCloseCompleted);
        assertEquals(1, observation.inputOpenAttempts);
      }
    }
  }

  @Test
  void givenActualParsingThenInjectedUncheckedWorkAndCloseFailures_whenEitherEntryAnalyzes_thenSuppressedIoDoesNotPromoteThePrimaryFamily()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      for (var error : List.of(false, true)) {
        // Arrange
        var name = (pathEntry ? "path" : "snapshot") + (error ? "-error" : "-runtime");
        var root = sourceRoot(name + "-root", false);
        var dependency = dependencyDirectory(name + "-dependency");
        var workFailure = error ? new AssertionError("Injected work error") : new IllegalStateException("Injected work runtime failure");
        var closeFailure = new IOException("Injected manager close failure");
        try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
          compiler.workFailure = workFailure;
          compiler.closeFailure = closeFailure;
          var configuration = configuration(observation.observe(dependency), List.of());
          var frontend = observedFrontend(compiler);

          // Act
          var failure = assertThrows(workFailure.getClass(), () -> analyze(frontend, root, configuration, pathEntry, false));

          // Assert
          assertSame(workFailure, failure);
          assertEquals(List.of(closeFailure), List.of(failure.getSuppressed()));
          assertEquals(1, compiler.parsedUnits);
          assertEquals(1, compiler.closeAttempts);
          assertTrue(compiler.delegateCloseCompleted);
          assertEquals(1, observation.inputOpenAttempts);
        }
      }
    }
  }

  @Test
  @ResourceLock(Resources.SYSTEM_PROPERTIES)
  void givenRuntimeProvenanceChangesDuringNormalManagerClose_whenEitherEntryRecaptures_thenFullRecordMismatchPreventsInput()
      throws IOException {
    var originalRuntime = Objects.requireNonNull(System.getProperty("java.runtime.version"));
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", false);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        compiler.afterDelegateClose = () -> System.setProperty("java.runtime.version", originalRuntime + "-changed-at-close");
        observation.inputOpenObserver = attempt -> compiler.timeline.add("dependency-read-" + attempt);
        var configuration = configuration(observation.observe(dependency), List.of());
        var frontend = observedFrontend(compiler);

        try {
          // Act
          if (pathEntry) {
            var failure = assertThrows(AnalysisEnvironmentException.class, () -> analyze(frontend, root, configuration, true, false));

            // Assert
            var mismatch = assertInstanceOf(IOException.class, failure.getCause());
            assertEquals("Compiler dependencies changed during analysis", mismatch.getMessage());
            assertEquals(PATH_ENVIRONMENT_FAILURE + mismatch.getMessage(), failure.getMessage());
          } else {
            var failure = assertThrows(IOException.class, () -> analyze(frontend, root, configuration, false, false));

            // Assert
            assertEquals("Compiler dependencies changed during analysis", failure.getMessage());
          }
          assertEquals(1, compiler.closeAttempts);
          assertTrue(compiler.delegateCloseCompleted);
          assertEquals(2, observation.inputOpenAttempts);
          assertBefore(compiler.timeline, "manager-close-complete", "dependency-read-2");
        } finally {
          System.setProperty("java.runtime.version", originalRuntime);
        }
      }
    }
  }

  @Test
  void givenRealCompilerWorkAndInjectedRecaptureReadFailure_whenEitherEntryAnalyzes_thenNormalClosePrecedesTheFailedReadAndNoInputReturns()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", false);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      var recaptureFailure = new IOException("Injected recapture read failure");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        observation.inputOpenObserver = attempt -> compiler.timeline.add("dependency-read-" + attempt);
        observation.inputReadFailureAttempt = 2;
        observation.inputReadFailure = recaptureFailure;
        var configuration = configuration(observation.observe(dependency), List.of());
        var frontend = observedFrontend(compiler);

        // Act
        if (pathEntry) {
          var failure = assertThrows(AnalysisEnvironmentException.class, () -> analyze(frontend, root, configuration, true, false));

          // Assert
          assertSame(recaptureFailure, failure.getCause());
          assertEquals(PATH_ENVIRONMENT_FAILURE + recaptureFailure.getMessage(), failure.getMessage());
        } else {
          var failure = assertThrows(IOException.class, () -> analyze(frontend, root, configuration, false, false));

          // Assert
          assertSame(recaptureFailure, failure);
        }
        assertEquals(0, recaptureFailure.getSuppressed().length);
        assertEquals(1, compiler.closeAttempts);
        assertTrue(compiler.delegateCloseCompleted);
        assertEquals(2, observation.inputOpenAttempts);
        assertBefore(compiler.timeline, "manager-close-complete", "dependency-read-2");
      }
    }
  }

  @Test
  void givenControlledCompilerWithoutTheSelectedCapability_whenEitherEmptyEntryAnalyzes_thenNamedRefusalPrecedesCaptureAndManagerAcquisition()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", true);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        compiler.unsupportedRelease = true;
        var configuration = configuration(observation.observe(dependency), List.of());
        var frontend = observedFrontend(compiler);

        // Act
        var failure = assertThrows(AnalysisEnvironmentException.class, () -> analyze(frontend, root, configuration, pathEntry, true));

        // Assert
        assertEquals("The system compiler cannot analyze Java release 25", failure.getMessage());
        var cause = assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("The system compiler does not support Java release 25", cause.getMessage());
        assertEquals(1, compiler.selections);
        assertEquals(0, compiler.managerRequests);
        assertEquals(0, compiler.taskRequests);
        assertEquals(0, compiler.closeAttempts);
        assertEquals(0, observation.inputOpenAttempts);
      }
    }
  }

  @Test
  void givenControlledMissingCompiler_whenEitherEmptyEntryAnalyzes_thenNamedEnvironmentFailurePrecedesCaptureWithoutAHandle()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", true);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        compiler.missingCompiler = true;
        var configuration = configuration(observation.observe(dependency), List.of());
        var frontend = observedFrontend(compiler);

        // Act
        var failure = assertThrows(AnalysisEnvironmentException.class, () -> analyze(frontend, root, configuration, pathEntry, true));

        // Assert
        assertEquals("A JDK system compiler is required for Java release 25", failure.getMessage());
        assertNull(failure.getCause());
        assertEquals(1, compiler.selections);
        assertEquals(0, compiler.managerRequests);
        assertEquals(0, compiler.taskRequests);
        assertEquals(0, compiler.closeAttempts);
        assertEquals(0, observation.inputOpenAttempts);
      }
    }
  }

  @Test
  void givenSupportedCompilerAndEmptySources_whenEitherEntryReturns_thenOneSelectionAndInitialCaptureAcquireNoManagerOrTask()
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(pathEntry ? "path-root" : "snapshot-root", true);
      var dependency = dependencyDirectory(pathEntry ? "path-dependency" : "snapshot-dependency");
      try (var compiler = new CompilerObservation(); var observation = new DependencyFileSystemObservation(dependency)) {
        var configuration = configuration(observation.observe(dependency), List.of(INVALID_COMPILER_OPTION));
        var frontend = observedFrontend(compiler);

        // Act
        var input = analyze(frontend, root, configuration, pathEntry, true);

        // Assert
        assertEquals(List.of(), input.program().types());
        assertEquals(1, compiler.selections);
        assertEquals(0, compiler.managerRequests);
        assertEquals(0, compiler.taskRequests);
        assertEquals(0, compiler.closeAttempts);
        assertEquals(1, observation.inputOpenAttempts);
      }
    }
  }

  private Path sourceRoot(String name, boolean empty) throws IOException {
    var root = Files.createDirectory(temporaryDirectory.resolve(name));
    if (!empty) {
      Files.writeString(root.resolve("Sample.java"), SOURCE);
    }
    return root;
  }

  private static JavaFrontend observedFrontend(CompilerObservation observation) {
    var pipeline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), observation::select);
    return new JavaFrontend(new JavaSourceLoader(), pipeline);
  }

  private static SyntaxAnalysisInput analyze(JavaFrontend frontend, Path root, CompilerConfiguration configuration, boolean pathEntry,
      boolean empty) throws IOException {
    var snapshot = empty ? InputSnapshot.fromSources(root, List.of()) : snapshot(root);
    return pathEntry ? frontend.analyze(root, configuration) : frontend.analyze(snapshot, configuration);
  }

  private static void assertBefore(List<String> timeline, String earlier, String later) {
    assertTrue(timeline.contains(earlier), timeline.toString());
    assertTrue(timeline.contains(later), timeline.toString());
    assertTrue(timeline.indexOf(earlier) < timeline.indexOf(later), timeline.toString());
  }

  private Path dependencyDirectory(String name) throws IOException {
    var dependency = Files.createDirectory(temporaryDirectory.resolve(name));
    Files.write(dependency.resolve("state.bin"), new byte[] {0, (byte) 0xff});
    return dependency;
  }

  private static CompilerConfiguration configuration(Path dependency, List<String> options) {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependency), List.of(), options);
  }

  private static InputSnapshot snapshot(Path sourceRoot) {
    return InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Sample.java"), SOURCE)));
  }

  private static final class CompilerObservation implements AutoCloseable {
    private final JavaCompiler delegate = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler());
    private final List<String> timeline = new ArrayList<>();
    private final JavaCompiler observedCompiler;
    private @Nullable StandardJavaFileManager manager;
    private @Nullable Throwable workFailure;
    private @Nullable IOException closeFailure;
    private Runnable afterDelegateClose = () -> {};
    private int selections;
    private int managerRequests;
    private int taskRequests;
    private int closeAttempts;
    private int parsedUnits;
    private boolean delegateCloseCompleted;
    private boolean unsupportedRelease;
    private boolean missingCompiler;

    private CompilerObservation() {
      observedCompiler = (JavaCompiler) Proxy.newProxyInstance(JavaCompiler.class.getClassLoader(), new Class<?>[] {JavaCompiler.class},
          (_, method, arguments) -> {
            if (method.getName().equals("getSourceVersions") && unsupportedRelease) {
              return Set.of(SourceVersion.RELEASE_24);
            }
            if (method.getName().equals("getStandardFileManager")) {
              managerRequests++;
              var acquired = (StandardJavaFileManager) invoke(method, delegate, arguments);
              manager = acquired;
              return observedManager(acquired);
            }
            if (method.getName().equals("getTask")) {
              taskRequests++;
              var task = (JavacTask) invoke(method, delegate, arguments);
              task.addTaskListener(new TaskListener() {
                @Override
                public void finished(TaskEvent event) {
                  if (event.getKind() == TaskEvent.Kind.PARSE) {
                    timeline.add("parse-complete");
                  } else if (event.getKind() == TaskEvent.Kind.ANALYZE) {
                    timeline.add("analysis-complete");
                  }
                }
              });
              var selectedFailure = workFailure;
              return selectedFailure == null ? task : new ParseFailureTask(task, selectedFailure, this);
            }
            return invoke(method, delegate, arguments);
          });
    }

    private @Nullable JavaCompiler select() {
      selections++;
      return missingCompiler ? null : observedCompiler;
    }

    private StandardJavaFileManager observedManager(StandardJavaFileManager acquired) {
      return (StandardJavaFileManager) Proxy.newProxyInstance(StandardJavaFileManager.class.getClassLoader(),
          new Class<?>[] {StandardJavaFileManager.class}, (_, method, arguments) -> {
            if (method.getName().equals("close")) {
              closeAttempts++;
              var result = invoke(method, acquired, arguments);
              delegateCloseCompleted = true;
              afterDelegateClose.run();
              var selectedFailure = closeFailure;
              if (selectedFailure != null) {
                timeline.add("manager-close-injected-failure");
                throw selectedFailure;
              }
              timeline.add("manager-close-complete");
              return result;
            }
            return invoke(method, acquired, arguments);
          });
    }

    private static @Nullable Object invoke(Method method, Object receiver, @Nullable Object @Nullable [] arguments) throws Throwable {
      try {
        return method.invoke(receiver, arguments);
      } catch (InvocationTargetException exception) {
        throw exception.getCause();
      }
    }

    @Override
    public void close() throws IOException {
      if (manager != null && !delegateCloseCompleted) {
        manager.close();
      }
    }
  }

  private static final class ParseFailureTask extends JavacTask {
    private final JavacTask delegate;
    private final Throwable failure;
    private final CompilerObservation observation;

    private ParseFailureTask(JavacTask delegate, Throwable failure, CompilerObservation observation) {
      this.delegate = delegate;
      this.failure = failure;
      this.observation = observation;
    }

    @Override
    public Iterable<? extends CompilationUnitTree> parse() throws IOException {
      var units = new ArrayList<CompilationUnitTree>();
      delegate.parse().forEach(units::add);
      observation.parsedUnits = units.size();
      switch (failure) {
        case IOException io -> throw io;
        case RuntimeException runtime -> throw runtime;
        case Error error -> throw error;
        default -> throw new IllegalArgumentException("An injected parse failure requires an I/O, runtime or error family");
      }
    }

    @Override public Iterable<? extends Element> analyze() throws IOException { return delegate.analyze(); }
    @Override public Iterable<? extends JavaFileObject> generate() throws IOException { return delegate.generate(); }
    @Override public void setTaskListener(TaskListener listener) { delegate.setTaskListener(listener); }
    @Override public void addTaskListener(TaskListener listener) { delegate.addTaskListener(listener); }
    @Override public void removeTaskListener(TaskListener listener) { delegate.removeTaskListener(listener); }
    @Override public TypeMirror getTypeMirror(Iterable<? extends Tree> path) { return delegate.getTypeMirror(path); }
    @Override public Elements getElements() { return delegate.getElements(); }
    @Override public Types getTypes() { return delegate.getTypes(); }
    @Override public void setProcessors(Iterable<? extends Processor> processors) { delegate.setProcessors(processors); }
    @Override public void setLocale(Locale locale) { delegate.setLocale(locale); }
    @Override public void addModules(Iterable<String> moduleNames) { delegate.addModules(moduleNames); }
    @Override public Boolean call() { return delegate.call(); }
  }
}
