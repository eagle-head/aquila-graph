package org.aquilagraph.frontend;

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import org.jspecify.annotations.Nullable;

final class CompilerSessionAcquisition implements AutoCloseable {
  private @Nullable StandardJavaFileManager fileManager;
  private @Nullable CompilerFileManager managedFileManager;
  private boolean transferred;

  private CompilerSessionAcquisition() {
  }

  static CompilerSession open(JavaCompiler compiler, JavaCompilerInvocation invocation, SnapshotSourceCatalog sources) throws IOException {
    Objects.requireNonNull(compiler, "compiler");
    Objects.requireNonNull(invocation, "invocation");
    Objects.requireNonNull(sources, "sources");
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var acquisition = new CompilerSessionAcquisition()) {
      acquisition.fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
      acquisition.managedFileManager = new CompilerFileManager(Objects.requireNonNull(acquisition.fileManager, "fileManager"));
      var task = (JavacTask) compiler.getTask(null, Objects.requireNonNull(acquisition.managedFileManager, "managedFileManager"),
          diagnostics, invocation.compilerOptions(), null, sources.compilationUnits());
      return acquisition.transfer(CompilerSession.fromAcquiredTask(
          Objects.requireNonNull(acquisition.managedFileManager, "managedFileManager"), diagnostics, task, sources, compiler, invocation));
    }
  }

  private CompilerSession transfer(CompilerSession completedSession) {
    transferred = true;
    return completedSession;
  }

  @Override
  public void close() throws IOException {
    if (!transferred) {
      if (managedFileManager != null) {
        managedFileManager.close();
      } else if (fileManager != null) {
        fileManager.close();
      }
    }
  }
}
