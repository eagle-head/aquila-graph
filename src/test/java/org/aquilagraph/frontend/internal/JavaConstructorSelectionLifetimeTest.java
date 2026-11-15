package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaConstructorSelectionLifetimeTest {
  private static final String SOURCE = """
      class Selection {
        <T> Selection(T value) {}
        static Selection create() { return new Selection("text"); }
      }
      """;

  @Test
  void givenActualConstructorWitness_whenEitherEntryReturns_thenBothOwnedManagersHaveClosed(@TempDir Path directory) throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(directory, pathEntry);
      try (var observation = new CompilerObservation()) {
        var frontend = frontend(observation);

        // Act
        var input = analyze(frontend, root, pathEntry);

        // Assert
        assertTrue(input.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()), input.diagnostics().toString());
        var body = input.aggregate().methodSyntax("type:Selection#create()").orElseThrow();
        var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
            .findFirst().orElseThrow();
        assertEquals("java.lang.String", body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
        assertEquals(2, observation.managerRequests);
        assertEquals(2, observation.taskRequests);
        assertEquals(List.of(true, false), observation.closedRoles);
        assertTrue(observation.managers.stream().allMatch(manager -> manager.delegateClosed));
      }
    }
  }

  @Test
  void givenBorrowedOriginalManager_whenSessionPreparesWitness_thenOnlyTheWitnessManagerClosesBeforeTheSession(@TempDir Path directory)
      throws IOException {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var sources = new SnapshotSourceCatalog(
        InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Selection.java")), SOURCE))));
    var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
    try (var observation = new CompilerObservation()) {
      List<Boolean> closedBeforeSessionClose;

      // Act
      try (var session = CompilerSessionAcquisition.open(observation.compiler, JavaCompilerInvocation.from(configuration), sources)) {
        session.analyze(session.parse(), policy);
        closedBeforeSessionClose = observation.managers.stream().map(manager -> manager.delegateClosed).toList();
      }

      // Assert
      assertEquals(List.of(false, true), closedBeforeSessionClose);
      assertEquals(List.of(true, false), observation.closedRoles);
      assertTrue(observation.managers.stream().allMatch(manager -> manager.delegateClosed));
    }
  }

  @Test
  void givenActualWitnessAndInjectedWitnessCloseFailure_whenEitherEntryAnalyzes_thenCheckedFailureKeepsItsPublicFamily(
      @TempDir Path directory) throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(directory, pathEntry);
      var closeFailure = new IOException("Injected constructor witness close failure");
      try (var observation = new CompilerObservation()) {
        observation.witnessCloseFailure = closeFailure;
        var frontend = frontend(observation);

        // Act
        if (pathEntry) {
          var failure = assertThrows(AnalysisEnvironmentException.class, () -> analyze(frontend, root, true));

          // Assert
          assertSame(closeFailure, failure.getCause());
          assertEquals(0, failure.getSuppressed().length);
        } else {
          var failure = assertThrows(IOException.class, () -> analyze(frontend, root, false));

          // Assert
          assertSame(closeFailure, failure);
        }
        assertEquals(0, closeFailure.getSuppressed().length);
        assertEquals(2, observation.taskRequests);
        assertEquals(List.of(true, false), observation.closedRoles);
        assertTrue(observation.managers.stream().allMatch(manager -> manager.delegateClosed));
      }
    }
  }

  @Test
  void givenWitnessTaskAndBothManagerClosesFail_whenEitherEntryAnalyzes_thenUncheckedWorkRemainsPrimaryWithOrderedSuppression(
      @TempDir Path directory) throws IOException {
    for (var pathEntry : List.of(false, true)) {
      for (var error : List.of(false, true)) {
        // Arrange
        var root = sourceRoot(directory, pathEntry);
        var workFailure = error ? new AssertionError("Injected constructor witness work error")
            : new IllegalStateException("Injected constructor witness work failure");
        var witnessCloseFailure = new IOException("Injected constructor witness close failure");
        var originalCloseFailure = new IOException("Injected original compiler close failure");
        try (var observation = new CompilerObservation()) {
          observation.workFailure = workFailure;
          observation.witnessCloseFailure = witnessCloseFailure;
          observation.originalCloseFailure = originalCloseFailure;
          var frontend = frontend(observation);

          // Act
          var failure = assertThrows(workFailure.getClass(), () -> analyze(frontend, root, pathEntry));

          // Assert
          assertSame(workFailure, failure);
          assertEquals(List.of(witnessCloseFailure, originalCloseFailure), List.of(failure.getSuppressed()));
          assertEquals(2, observation.managerRequests);
          assertEquals(2, observation.taskRequests);
          assertEquals(List.of(true, false), observation.closedRoles);
          assertTrue(observation.managers.stream().allMatch(manager -> manager.delegateClosed));
        }
      }
    }
  }

  @Test
  void givenWitnessManagerProviderReturnsNull_whenEitherEntryAnalyzes_thenNoImplicitManagerOrWitnessTaskIsCreated(@TempDir Path directory)
      throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(directory, pathEntry);
      try (var observation = new CompilerObservation()) {
        observation.nullWitnessManager = true;
        var frontend = frontend(observation);

        // Act
        var failure = assertThrows(NullPointerException.class, () -> analyze(frontend, root, pathEntry));

        // Assert
        assertEquals("constructor witness file manager", failure.getMessage());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(2, observation.managerRequests);
        assertEquals(1, observation.taskRequests);
        assertEquals(List.of(false), observation.closedRoles);
        assertEquals(1, observation.managers.size());
        assertTrue(observation.managers.getFirst().delegateClosed);
      }
    }
  }

  @Test
  void givenWitnessManagerAcquisitionFails_whenEitherEntryAnalyzes_thenOriginalFailurePropagatesAndOriginalManagerCloses(
      @TempDir Path directory) throws IOException {
    for (var pathEntry : List.of(false, true)) {
      // Arrange
      var root = sourceRoot(directory, pathEntry);
      var acquisitionFailure = new IllegalStateException("Injected constructor witness acquisition failure");
      try (var observation = new CompilerObservation()) {
        observation.acquisitionFailure = acquisitionFailure;
        var frontend = frontend(observation);

        // Act
        var failure = assertThrows(IllegalStateException.class, () -> analyze(frontend, root, pathEntry));

        // Assert
        assertSame(acquisitionFailure, failure);
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(2, observation.managerRequests);
        assertEquals(1, observation.taskRequests);
        assertEquals(List.of(false), observation.closedRoles);
        assertEquals(1, observation.managers.size());
        assertTrue(observation.managers.getFirst().delegateClosed);
      }
    }
  }

  @Test
  void givenAbruptWitnessAttribution_whenTheFrontendIsRetried_thenPrimaryFailureSurvivesAndTheNextResultIsUsable(@TempDir Path directory)
      throws IOException {
    // Arrange
    var root = sourceRoot(directory, false);
    var attributionFailure = new IllegalStateException("Injected witness attribution failure");
    var witnessCloseFailure = new IOException("Injected witness manager close failure");
    var originalCloseFailure = new IOException("Injected original manager close failure");
    try (var observation = new CompilerObservation()) {
      observation.witnessAttributionFailure = attributionFailure;
      observation.witnessCloseFailure = witnessCloseFailure;
      observation.originalCloseFailure = originalCloseFailure;
      var frontend = frontend(observation);

      // Act
      var failure = assertThrows(RuntimeException.class, () -> analyze(frontend, root, false));
      var closedAfterFailure = List.copyOf(observation.closedRoles);
      var delegatesClosedAfterFailure = observation.managers.stream().allMatch(manager -> manager.delegateClosed);
      observation.beginHealthyRequest();
      var result = analyze(frontend, root, false);

      // Assert
      assertSame(attributionFailure, failure.getCause());
      assertEquals(List.of(witnessCloseFailure, originalCloseFailure), List.of(failure.getSuppressed()));
      assertEquals(List.of(true, false), closedAfterFailure);
      assertTrue(delegatesClosedAfterFailure);
      assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()), result.diagnostics().toString());
      var body = result.aggregate().methodSyntax("type:Selection#create()").orElseThrow();
      var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
          .findFirst().orElseThrow();
      assertEquals("java.lang.String", body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
      assertEquals(List.of(true, false, true, false), observation.closedRoles);
      assertTrue(observation.managers.stream().allMatch(manager -> manager.delegateClosed));
    }
  }

  private static Path sourceRoot(Path directory, boolean pathEntry) throws IOException {
    var root = directory.resolve(pathEntry ? "path-input" : "snapshot-input");
    Files.createDirectories(root);
    Files.writeString(root.resolve("Selection.java"), SOURCE, StandardCharsets.UTF_8);
    return root;
  }

  private static JavaFrontend frontend(CompilerObservation observation) {
    return new JavaFrontend(new JavaSourceLoader(), new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()),
        () -> observation.compiler));
  }

  private static AstResult analyze(JavaFrontend frontend, Path root, boolean pathEntry) throws IOException {
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    return pathEntry ? frontend.produce(new AstProductionRequest(root, configuration, AstProfile.JAVA_SOURCE_UNITS)).result().orElseThrow()
        : frontend.produce(InputSnapshot.fromSources(capturedAbsolute(root), List.of(SourceFile.fromContent(capturedPath(Path.of("Selection.java")), SOURCE))), configuration,
            AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
  }

  private static final class CompilerObservation implements AutoCloseable {
    private final JavaCompiler delegate = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler());
    private final JavaCompiler compiler;
    private final List<ManagerObservation> managers = new ArrayList<>();
    private final List<Boolean> closedRoles = new ArrayList<>();
    private @Nullable Throwable workFailure;
    private @Nullable IllegalStateException witnessAttributionFailure;
    private @Nullable IOException witnessCloseFailure;
    private @Nullable IOException originalCloseFailure;
    private @Nullable IllegalStateException acquisitionFailure;
    private boolean nullWitnessManager;
    private int managerRequests;
    private int taskRequests;

    private CompilerObservation() {
      compiler = (JavaCompiler) Proxy.newProxyInstance(JavaCompiler.class.getClassLoader(), new Class<?>[] {JavaCompiler.class},
          (_, method, arguments) -> {
            if (method.getName().equals("getStandardFileManager")) {
              managerRequests++;
              var witness = managerRequests > 1;
              if (witness && acquisitionFailure != null) {
                throw acquisitionFailure;
              }
              if (witness && nullWitnessManager) {
                return null;
              }
              var acquired = (StandardJavaFileManager) invoke(method, delegate, arguments);
              var manager = new ManagerObservation(acquired, witness, this);
              managers.add(manager);
              return manager.observedManager;
            }
            if (method.getName().equals("getTask")) {
              taskRequests++;
              if (taskRequests > 1 && workFailure != null) {
                throw workFailure;
              }
              var task = (JavacTask) invoke(method, delegate, arguments);
              var attributionFailure = witnessAttributionFailure;
              if (taskRequests > 1 && attributionFailure != null) {
                task.addTaskListener(new TaskListener() {
                  @Override
                  public void started(TaskEvent event) {
                    if (event.getKind() == TaskEvent.Kind.ANALYZE) {
                      throw attributionFailure;
                    }
                  }
                });
              }
              return task;
            }
            return invoke(method, delegate, arguments);
          });
    }

    private void beginHealthyRequest() {
      managerRequests = 0;
      taskRequests = 0;
      witnessAttributionFailure = null;
      witnessCloseFailure = null;
      originalCloseFailure = null;
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
      for (var manager : managers) {
        if (!manager.delegateClosed) {
          manager.delegate.close();
          manager.delegateClosed = true;
        }
      }
    }
  }

  private static final class ManagerObservation {
    private final StandardJavaFileManager delegate;
    private final StandardJavaFileManager observedManager;
    private boolean delegateClosed;

    private ManagerObservation(StandardJavaFileManager delegate, boolean witness, CompilerObservation observation) {
      this.delegate = Objects.requireNonNull(delegate);
      observedManager = (StandardJavaFileManager) Proxy.newProxyInstance(StandardJavaFileManager.class.getClassLoader(),
          new Class<?>[] {StandardJavaFileManager.class}, (_, method, arguments) -> {
            if (method.getName().equals("close")) {
              var result = CompilerObservation.invoke(method, delegate, arguments);
              delegateClosed = true;
              observation.closedRoles.add(witness);
              var failure = witness ? observation.witnessCloseFailure : observation.originalCloseFailure;
              if (failure != null) {
                throw failure;
              }
              return result;
            }
            return CompilerObservation.invoke(method, delegate, arguments);
          });
    }
  }
}
