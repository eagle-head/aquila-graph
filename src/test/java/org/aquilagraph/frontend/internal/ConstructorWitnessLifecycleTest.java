package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ConstructorWitnessLifecycleTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenUnavailableOrRejectedConstructorWitnesses_whenAllExecutionOwnersAreProduced_thenCorrelationFailuresAreDiagnosed() {
    // Arrange
    var cases = List.of(new WitnessCase("Unavailable", "class PrivateProof {}", "frontend.missing-constructor-correlation"),
        new WitnessCase("Rejected", "class PrivateProof { MissingType value; }", "frontend.constructor-witness-rejected"));
    var source = "class Box { <T> Box(T x) {} } class Probe { Object field = new Box(1); "
        + "{ Object local = new Box(2); } Object make() { return new Box(3); } } "
        + "enum Token { ONE(4); <T> Token(T value) {} }";

    // Act
    var tests = cases.stream().map(example -> DynamicTest.dynamicTest(
        "given" + example.name() + "PrivateProof_whenProducedAndDecoded_thenRequiredCorrelationHasSourceDiagnostic", () -> {
          // Arrange
          var observation = new CompilerObservation();
          observation.witnessSource = example.source();
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Probe.java"), source)));
          var frontend = new JavaFrontend(new JavaSourceLoader(), new JavaAnalysisPipeline(
              new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), () -> observation.compiler));
          // Act
          var outcome = frontend.produce(new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_MODULE_SERVICES));
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, outcome.completion(), outcome.diagnostics().toString());
          var original = outcome.result().orElseThrow();
          for (var ast : List.of(original, AstCodec.decode(AstCodec.encode(original), original.identity()))) {
            assertEquals(List.of(example.code(), example.code(), example.code(), example.code()),
                ast.diagnostics().stream().map(value -> value.code()).toList());
            assertTrue(ast.diagnostics().stream().allMatch(value -> value.stage() == DiagnosticStage.FRONTEND && value.isError()
                && value.origins().size() == 1 && value.origins().getFirst().sourceFileKey().equals(snapshot.sourceFiles().getFirst().sourceFileKey())));
            var ranges = ast.diagnostics().stream().map(value -> value.origins().getFirst())
                .map(span -> source.substring((int) span.startOffset(), (int) span.endOffsetExclusive())).sorted().toList();
            assertEquals(List.of("ONE(4)", "new Box(1)", "new Box(2)", "new Box(3)"), ranges);
            assertEquals(original.identity(), ast.identity());
          }
          assertEquals(List.of(1, 1, 1, 1, 1), observation.closes);
        }));
    // Assert
    return tests;
  }

  private record WitnessCase(String name, String source, String code) {
  }

  @Test
  void givenEscapedLocalConstructorOwner_whenSessionAnalyzes_thenWitnessSelectionAndBothManagersClose() throws IOException {
    // Arrange
    var source = "class Probe { Object scope() { class Box { <T> Box(T x) {} " + "\\" + "u007d"
        + " class Local { Object make() { return new Box(1); } } return new Local().make(); } }";
    var observation = new CompilerObservation();
    var sources = sources(source);

    // Act
    try (var session = CompilerSessionAcquisition.open(observation.compiler, invocation(), sources)) {
      session.analyze(session.parse(), JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    }

    // Assert
    assertEquals(List.of(1, 1), observation.closes);
    assertEquals(2, observation.diagnostics.size());
    assertTrue(observation.diagnostics.stream().flatMap(value -> value.getDiagnostics().stream())
        .noneMatch(value -> value.getKind() == Diagnostic.Kind.ERROR),
        observation.diagnostics.stream().flatMap(value -> value.getDiagnostics().stream()).toList().toString());
  }

  @Test
  void givenWitnessCreationAndCloseFailures_whenEscapedConstructorIsAnalyzed_thenPrimaryAndCleanupFailuresSurvive() throws IOException {
    // Arrange
    var source = "class Box { <T> Box(T x) {} " + "\\" + "u007d class Probe { Object make() { return new Box(1); } }";
    var observation = new CompilerObservation();
    observation.witnessFailure = new IllegalStateException("Controlled witness task creation failure");
    observation.closeFailure = new IOException("Controlled witness manager close failure");
    var sources = sources(source);

    // Act
    var actual = assertThrows(IllegalStateException.class, () -> {
      try (var session = CompilerSessionAcquisition.open(observation.compiler, invocation(), sources)) {
        session.analyze(session.parse(), JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_MODULE_SERVICES));
      }
    });

    // Assert
    assertSame(observation.witnessFailure, actual);
    assertArrayEquals(new Throwable[] {observation.closeFailure}, actual.getSuppressed());
    assertEquals(List.of(1, 1), observation.closes);
  }

  private SnapshotSourceCatalog sources(String source) {
    return new SnapshotSourceCatalog(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Probe.java"), source))));
  }

  private static JavaCompilerInvocation invocation() {
    return JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  private static final class CompilerObservation {
    private final JavaCompiler delegate = ToolProvider.getSystemJavaCompiler();
    private final List<Integer> closes = new ArrayList<>();
    private final List<DiagnosticCollector<?>> diagnostics = new ArrayList<>();
    private final JavaCompiler compiler;
    private RuntimeException witnessFailure;
    private IOException closeFailure;
    private String witnessSource;
    private int taskCount;

    private CompilerObservation() {
      compiler = (JavaCompiler) Proxy.newProxyInstance(JavaCompiler.class.getClassLoader(), new Class<?>[] {JavaCompiler.class},
          (_, method, arguments) -> {
            if (method.getName().equals("getStandardFileManager")) {
              var manager = (StandardJavaFileManager) invoke(method, delegate, arguments);
              var index = closes.size();
              closes.add(0);
              return Proxy.newProxyInstance(StandardJavaFileManager.class.getClassLoader(), new Class<?>[] {StandardJavaFileManager.class},
                  (_, operation, values) -> {
                    if (operation.getName().equals("close")) {
                      closes.set(index, closes.get(index) + 1);
                      invoke(operation, manager, values);
                      if (index > 0 && closeFailure != null) {
                        throw closeFailure;
                      }
                      return null;
                    }
                    return invoke(operation, manager, values);
                  });
            }
            if (method.getName().equals("getTask")) {
              taskCount++;
              if (arguments[2] instanceof DiagnosticCollector<?> collector) {
                diagnostics.add(collector);
              }
              if (taskCount > 1 && witnessFailure != null) {
                throw witnessFailure;
              }
              if (taskCount > 1 && witnessSource != null) {
                arguments[5] = List.of(new SimpleJavaFileObject(URI.create("string:///PrivateProof.java"), JavaFileObject.Kind.SOURCE) {
                  @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return witnessSource; }
                });
              }
            }
            return invoke(method, delegate, arguments);
          });
    }

    private static Object invoke(Method method, Object receiver, Object[] arguments) throws Throwable {
      try {
        return method.invoke(receiver, arguments);
      } catch (InvocationTargetException exception) {
        throw exception.getCause();
      }
    }

  }
}
