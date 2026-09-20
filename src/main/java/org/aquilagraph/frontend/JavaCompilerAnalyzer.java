package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.FrontendResult;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaProgram;

final class JavaCompilerAnalyzer {
  private final AnalysisEnvironmentCapture environmentCapture;

  JavaCompilerAnalyzer(AnalysisEnvironmentCapture environmentCapture) {
    this.environmentCapture =
        Objects.requireNonNull(environmentCapture, "environmentCapture");
  }

  FrontendResult analyze(InputSnapshot snapshot, CompilerConfiguration configuration)
      throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(configuration, "configuration");
    var compiler = systemCompiler();
    var invocation = JavaCompilerInvocation.from(configuration);
    var environmentSnapshot =
        environmentCapture.capture(compiler, configuration, invocation.analysisOptions());
    if (snapshot.sourceFiles().isEmpty()) {
      return new FrontendResult(
          snapshot, environmentSnapshot, new JavaProgram(List.of()), List.of());
    }

    var sources = new SnapshotSourceCatalog(snapshot);
    var collector = new DiagnosticCollector<JavaFileObject>();
    FrontendResult result;
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8)) {
      var task = compilerTask(compiler, fileManager, collector, invocation, sources);
      var units = parseAndAnalyze(task);
      var semantics = new JavaTreeSemantics(Trees.instance(task));
      var translation = new JavaProgramTranslator(sources, semantics).translate(units);
      var diagnostics = new ArrayList<AnalysisDiagnostic>();
      diagnostics.addAll(
          new CompilerDiagnosticTranslator(sources).translate(collector.getDiagnostics()));
      diagnostics.addAll(translation.diagnostics());
      result =
          new FrontendResult(snapshot, environmentSnapshot, translation.program(), diagnostics);
    }

    verifyEnvironment(compiler, configuration, invocation, environmentSnapshot);
    return result;
  }

  private void verifyEnvironment(
      JavaCompiler compiler,
      CompilerConfiguration configuration,
      JavaCompilerInvocation invocation,
      AnalysisEnvironmentSnapshot expected)
      throws IOException {
    var actual =
        environmentCapture.capture(compiler, configuration, invocation.analysisOptions());
    if (!expected.equals(actual)) {
      throw new IOException("Compiler dependencies changed during analysis");
    }
  }

  private static JavaCompiler systemCompiler() {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("A JDK 25 system compiler is required");
    }
    return compiler;
  }

  private static JavacTask compilerTask(
      JavaCompiler compiler,
      StandardJavaFileManager fileManager,
      DiagnosticCollector<JavaFileObject> collector,
      JavaCompilerInvocation invocation,
      SnapshotSourceCatalog sources) {
    return (JavacTask)
        compiler.getTask(
            null,
            fileManager,
            collector,
            invocation.compilerOptions(),
            null,
            sources.compilationUnits());
  }

  private static List<CompilationUnitTree> parseAndAnalyze(JavacTask task) throws IOException {
    var units = new ArrayList<CompilationUnitTree>();
    task.parse().forEach(units::add);
    task.analyze();
    return List.copyOf(units);
  }
}
