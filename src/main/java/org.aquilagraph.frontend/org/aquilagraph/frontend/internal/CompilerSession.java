package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.JavaCompiler;

final class CompilerSession implements AutoCloseable {
  private final CompilerFileManager fileManager;
  private final DiagnosticCollector<JavaFileObject> diagnostics;
  private final JavacTask task;
  private final SnapshotSourceCatalog sources;
  private final JavaCompiler compiler;
  private final JavaCompilerInvocation invocation;

  private CompilerSession(CompilerFileManager fileManager, DiagnosticCollector<JavaFileObject> diagnostics, JavacTask task,
      SnapshotSourceCatalog sources, JavaCompiler compiler, JavaCompilerInvocation invocation) {
    this.fileManager = Objects.requireNonNull(fileManager, "fileManager");
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    this.task = Objects.requireNonNull(task, "task");
    this.sources = Objects.requireNonNull(sources, "sources");
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.invocation = Objects.requireNonNull(invocation, "invocation");
  }

  static CompilerSession fromAcquiredTask(CompilerFileManager fileManager, DiagnosticCollector<JavaFileObject> diagnostics, JavacTask task,
      SnapshotSourceCatalog sources, JavaCompiler compiler, JavaCompilerInvocation invocation) {
    return new CompilerSession(fileManager, diagnostics, task, sources, compiler, invocation);
  }

  ParsedJavaSource parse() throws IOException {
    var units = new ArrayList<CompilationUnitTree>();
    task.parse().forEach(units::add);
    var trees = Trees.instance(task);
    var evidence = new JavaSourceEvidence(trees.getSourcePositions());
    return new ParsedJavaSource(units, evidence, ParsedTreeInventory.capture(units, sources::find, evidence));
  }

  JavaCompilation analyze(ParsedJavaSource parsedSource, JavaTranslationPolicy policy) throws IOException {
    Objects.requireNonNull(parsedSource, "parsedSource");
    task.analyze();
    var trees = Trees.instance(task);
    var types = task.getTypes();
    var identities = new DeclarationIdentityRegistry(trees, types, task.getElements(), policy);
    var selection = new JavaConstructorSelection(compiler, invocation, sources, fileManager.standardFileManager(), trees, types,
        task.getElements(), policy, parsedSource.treeInventory()::tokens);
    selection.prepare(parsedSource.units());
    return new JavaCompilation(parsedSource.units(),
        new SemanticResolver(trees, types, task.getElements(), identities, Optional.of(selection)), parsedSource.sourceEvidence(),
        parsedSource.treeInventory(), diagnostics.getDiagnostics());
  }

  @Override
  public void close() throws IOException {
    fileManager.close();
  }
}
