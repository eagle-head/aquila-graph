package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilerSessionAcquisitionTest {
  private static final JavaCompilerInvocation INVOCATION = JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

  @TempDir Path temporaryDirectory;

  @Test
  void givenTaskCreationFailsAfterManagerAcquisition_whenSessionIsOpened_thenOriginalFailureRetainsCleanupFailure() throws IOException {
    for (var failure : taskFailures()) {
      // Arrange
      var closeFailure = new IOException("Manager close failed");
      try (var fixture = new CompilerFixture()) {
        fixture.taskFailure = failure;
        fixture.closeFailure = closeFailure;
        var sources = sources();

        // Act
        var actual = assertThrows(failure.getClass(), () -> CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources));

        // Assert
        assertSame(failure, actual);
        assertArrayEquals(new Throwable[] {closeFailure}, actual.getSuppressed());
        assertEquals(1, fixture.closeAttempts.get());
        assertSame(fixture.managerListener, fixture.taskListener);
      }
    }
  }

  @Test
  void givenManagerAcquisitionFailsWithoutReturningHandle_whenSessionIsOpened_thenOriginalFailurePropagatesWithoutClose()
      throws IOException {
    // Arrange
    var failure = new IllegalStateException("Manager acquisition failed");
    try (var fixture = new CompilerFixture()) {
      fixture.managerFailure = failure;
      var sources = sources();

      // Act
      var actual = assertThrows(IllegalStateException.class, () -> CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources));

      // Assert
      assertSame(failure, actual);
      assertEquals(0, fixture.closeAttempts.get());
      assertEquals(0, fixture.taskRequests.get());
      assertEquals(0, actual.getSuppressed().length);
    }
  }

  @Test
  void givenManagerProviderReturnsNull_whenSessionIsOpened_thenNoTaskIsCreatedAndNoHandleIsClosed() throws IOException {
    // Arrange
    try (var fixture = new CompilerFixture()) {
      fixture.nullManager = true;
      var sources = sources();

      // Act
      var failure = assertThrows(NullPointerException.class, () -> CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources));

      // Assert
      assertEquals("fileManager", failure.getMessage());
      assertEquals(0, fixture.closeAttempts.get());
      assertEquals(0, fixture.taskRequests.get());
    }
  }

  @Test
  void givenTaskHasWrongRuntimeType_whenSessionIsOpened_thenCastFailureClosesAcquiredManager() throws IOException {
    // Arrange
    try (var fixture = new CompilerFixture()) {
      fixture.suppliedTask = (CompilationTask) Proxy.newProxyInstance(CompilationTask.class.getClassLoader(),
          new Class<?>[] {CompilationTask.class}, (_, _, _) -> null);
      var sources = sources();

      // Act
      var failure = assertThrows(ClassCastException.class, () -> CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources));

      // Assert
      assertEquals(1, fixture.closeAttempts.get());
      assertEquals(0, failure.getSuppressed().length);
    }
  }

  @Test
  void givenTaskProviderReturnsNull_whenSessionIsOpened_thenSessionValidationClosesAcquiredManager() throws IOException {
    // Arrange
    try (var fixture = new CompilerFixture()) {
      fixture.nullTask = true;
      var sources = sources();

      // Act
      var failure = assertThrows(NullPointerException.class, () -> CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources));

      // Assert
      assertEquals("task", failure.getMessage());
      assertEquals(1, fixture.closeAttempts.get());
    }
  }

  @Test
  void givenSuccessfulSessionTransfer_whenCompilerWorkAndEnclosingScopeFinish_thenManagerIsClosedOnceAndDiagnosticsAreRetained()
      throws IOException {
    // Arrange
    try (var fixture = new CompilerFixture()) {
      var source = SourceFile.fromContent(Path.of("Sample.java"), "class Sample { Missing value; }");
      var sources = new SnapshotSourceCatalog(InputSnapshot.fromSources(temporaryDirectory, List.of(source)));
      List<String> diagnosticCodes;
      int unitCount;

      // Act
      try (var session = CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources)) {
        assertEquals(0, fixture.closeAttempts.get());
        var compilation = session.analyze(session.parse());
        unitCount = compilation.units().size();
        diagnosticCodes = compilation.diagnostics().stream().map(diagnostic -> diagnostic.getCode()).toList();
      }

      // Assert
      assertEquals(1, fixture.closeAttempts.get());
      assertEquals(1, unitCount);
      assertEquals(List.of("compiler.err.cant.resolve.location"), diagnosticCodes);
      assertSame(fixture.managerListener, fixture.taskListener);
    }
  }

  @Test
  void givenTransferredSessionWorkAndCloseFail_whenEnclosingScopeFinishes_thenWorkFailureRemainsPrimaryWithSuppressedClose()
      throws IOException {
    // Arrange
    var workFailure = new IOException("Parsing failed");
    var closeFailure = new IOException("Manager close failed");
    try (var fixture = new CompilerFixture()) {
      fixture.suppliedTask = new FailingJavacTask(workFailure);
      fixture.closeFailure = closeFailure;
      var sources = sources();

      // Act
      var actual = assertThrows(IOException.class, () -> {
        try (var session = CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources)) {
          session.parse();
        }
      });

      // Assert
      assertSame(workFailure, actual);
      assertArrayEquals(new Throwable[] {closeFailure}, actual.getSuppressed());
      assertEquals(1, fixture.closeAttempts.get());
    }
  }

  @Test
  void givenTransferredSessionWorkSucceedsAndCloseFails_whenEnclosingScopeFinishes_thenCloseFailurePreventsNormalReturn()
      throws IOException {
    // Arrange
    var closeFailure = new IOException("Manager close failed");
    try (var fixture = new CompilerFixture()) {
      fixture.closeFailure = closeFailure;
      var sources = sources();

      // Act
      var actual = assertThrows(IOException.class, () -> {
        try (var session = CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources)) {
          session.analyze(session.parse());
        }
      });

      // Assert
      assertSame(closeFailure, actual);
      assertEquals(0, actual.getSuppressed().length);
      assertEquals(1, fixture.closeAttempts.get());
    }
  }

  @Test
  void givenManagerHoldsAnOpenedDependencyArchive_whenTaskCreationFails_thenCleanupAllowsArchiveRemoval() throws IOException {
    // Arrange
    var archive = temporaryDirectory.resolve("dependency.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new JarEntry("example/Dependency.class"));
      output.closeEntry();
    }
    try (var fixture = new CompilerFixture()) {
      fixture.delegateManager.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(archive));
      var entry = fixture.delegateManager.getJavaFileForInput(StandardLocation.CLASS_PATH, "example.Dependency", Kind.CLASS);
      try (var input = entry.openInputStream()) {
        assertEquals(0, input.readAllBytes().length);
      }
      fixture.taskFailure = new IllegalStateException("Task creation failed");
      var sources = sources();

      // Act
      var failure = assertThrows(IllegalStateException.class,
          () -> CompilerSessionAcquisition.open(fixture.compiler, INVOCATION, sources));
      Files.delete(archive);

      // Assert
      assertSame(fixture.taskFailure, failure);
      assertFalse(Files.exists(archive));
      assertEquals(1, fixture.closeAttempts.get());
    }
  }

  private SnapshotSourceCatalog sources() {
    return new SnapshotSourceCatalog(InputSnapshot.fromSources(temporaryDirectory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}"))));
  }

  private static List<Throwable> taskFailures() {
    return List.of(new IllegalArgumentException("Configuration rejected"), new IllegalStateException("Task creation failed"),
        new AssertionError("Task creation aborted"));
  }

  private static final class CompilerFixture implements AutoCloseable {
    private final JavaCompiler delegate = ToolProvider.getSystemJavaCompiler();
    private final StandardJavaFileManager delegateManager = delegate.getStandardFileManager(null, Locale.ROOT, null);
    private final AtomicInteger closeAttempts = new AtomicInteger();
    private final AtomicInteger taskRequests = new AtomicInteger();
    private final JavaCompiler compiler;
    private Throwable managerFailure;
    private Throwable taskFailure;
    private IOException closeFailure;
    private CompilationTask suppliedTask;
    private Object managerListener;
    private Object taskListener;
    private boolean nullManager;
    private boolean nullTask;

    private CompilerFixture() {
      var manager = (StandardJavaFileManager) Proxy.newProxyInstance(StandardJavaFileManager.class.getClassLoader(),
          new Class<?>[] {StandardJavaFileManager.class}, (_, method, arguments) -> {
            if (method.getName().equals("close")) {
              closeAttempts.incrementAndGet();
              if (closeFailure != null) {
                throw closeFailure;
              }
            }
            try {
              return method.invoke(delegateManager, arguments);
            } catch (InvocationTargetException exception) {
              throw exception.getCause();
            }
          });
      compiler = (JavaCompiler) Proxy.newProxyInstance(JavaCompiler.class.getClassLoader(), new Class<?>[] {JavaCompiler.class},
          (_, method, arguments) -> {
            if (method.getName().equals("getStandardFileManager")) {
              managerListener = arguments[0];
              if (managerFailure != null) {
                throw managerFailure;
              }
              return nullManager ? null : manager;
            }
            if (method.getName().equals("getTask")) {
              taskRequests.incrementAndGet();
              taskListener = arguments[2];
              if (taskFailure != null) {
                throw taskFailure;
              }
              if (nullTask || suppliedTask != null) {
                return suppliedTask;
              }
            }
            try {
              return method.invoke(delegate, arguments);
            } catch (InvocationTargetException exception) {
              throw exception.getCause();
            }
          });
    }

    @Override
    public void close() throws IOException {
      delegateManager.close();
    }
  }

  private static final class FailingJavacTask extends JavacTask {
    private final IOException failure;

    private FailingJavacTask(IOException failure) {
      this.failure = failure;
    }

    @Override public Iterable<? extends CompilationUnitTree> parse() throws IOException { throw failure; }
    @Override public Iterable<? extends Element> analyze() throws IOException { throw failure; }
    @Override public Iterable<? extends JavaFileObject> generate() throws IOException { throw failure; }
    @Override public void setTaskListener(TaskListener listener) { throw new UnsupportedOperationException(); }
    @Override public void addTaskListener(TaskListener listener) { throw new UnsupportedOperationException(); }
    @Override public void removeTaskListener(TaskListener listener) { throw new UnsupportedOperationException(); }
    @Override public TypeMirror getTypeMirror(Iterable<? extends Tree> path) { throw new UnsupportedOperationException(); }
    @Override public Elements getElements() { throw new UnsupportedOperationException(); }
    @Override public Types getTypes() { throw new UnsupportedOperationException(); }
    @Override public void setProcessors(Iterable<? extends Processor> processors) { throw new UnsupportedOperationException(); }
    @Override public void setLocale(Locale locale) { throw new UnsupportedOperationException(); }
    @Override public void addModules(Iterable<String> moduleNames) { throw new UnsupportedOperationException(); }
    @Override public Boolean call() { throw new UnsupportedOperationException(); }
  }
}
