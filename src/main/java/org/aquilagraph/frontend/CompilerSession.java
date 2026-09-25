package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

final class CompilerSession implements AutoCloseable {
  private final StandardJavaFileManager fileManager;
  private final DiagnosticCollector<JavaFileObject> diagnostics;
  private final JavacTask task;
  private final SnapshotSourceCatalog sources;

  private CompilerSession(StandardJavaFileManager fileManager, DiagnosticCollector<JavaFileObject> diagnostics, JavacTask task,
      SnapshotSourceCatalog sources) {
    this.fileManager = Objects.requireNonNull(fileManager, "fileManager");
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    this.task = Objects.requireNonNull(task, "task");
    this.sources = Objects.requireNonNull(sources, "sources");
  }

  static CompilerSession open(JavaCompiler compiler, JavaCompilerInvocation invocation, SnapshotSourceCatalog sources) {
    Objects.requireNonNull(compiler, "compiler");
    Objects.requireNonNull(invocation, "invocation");
    Objects.requireNonNull(sources, "sources");
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
    try {
      var task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, invocation.compilerOptions(), null,
          sources.compilationUnits());
      return new CompilerSession(fileManager, diagnostics, task, sources);
    } catch (IllegalArgumentException exception) {
      closeAfterFailure(fileManager, exception);
      throw exception;
    }
  }

  ParsedJavaSource parse() throws IOException {
    var units = new ArrayList<CompilationUnitTree>();
    task.parse().forEach(units::add);
    var trees = Trees.instance(task);
    var evidence = new JavaSourceEvidence(trees.getSourcePositions());
    return new ParsedJavaSource(units, evidence, ParsedTreeInventory.capture(units, sources::find, evidence));
  }

  JavaCompilation analyze(ParsedJavaSource parsedSource) throws IOException {
    Objects.requireNonNull(parsedSource, "parsedSource");
    task.analyze();
    var trees = Trees.instance(task);
    var types = task.getTypes();
    var identities = new DeclarationIdentityRegistry(trees, types);
    return new JavaCompilation(parsedSource.units(), new SemanticResolver(trees, types, task.getElements(), identities),
        parsedSource.sourceEvidence(), parsedSource.treeInventory(), diagnostics.getDiagnostics());
  }

  @Override
  public void close() throws IOException {
    fileManager.close();
  }

  private static void closeAfterFailure(StandardJavaFileManager fileManager, IllegalArgumentException failure) {
    try {
      fileManager.close();
    } catch (IOException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }
}
