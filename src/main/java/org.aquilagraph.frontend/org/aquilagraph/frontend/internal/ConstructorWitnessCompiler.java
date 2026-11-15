package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import org.aquilagraph.frontend.internal.ConstructorWitnessInputs.Input;
import org.aquilagraph.frontend.internal.ConstructorWitnessInputs.ReplacementRange;

/** Attributes a private witness with its own manager, keeping attribution live during correlation. */
final class ConstructorWitnessCompiler {
  private final JavaCompiler compiler;
  private final JavaCompilerInvocation invocation;

  ConstructorWitnessCompiler(JavaCompiler compiler, JavaCompilerInvocation invocation) {
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.invocation = Objects.requireNonNull(invocation, "invocation");
  }

  ConstructorCorrelation attribute(Input input, CorrelateAttribution correlate) throws IOException {
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (
        var manager = new CompilerFileManager(Objects.requireNonNull(
            compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8), "constructor witness file manager"));
        var files = new ConstructorWitnessFileManager(manager, input.binaries())) {
      invocation.configurePaths(manager.standardFileManager());
      var task = (JavacTask) compiler.getTask(new StringWriter(), files, diagnostics, invocation.compilerOptions(), null, input.sources());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      task.analyze();
      var errors = diagnostics.getDiagnostics().stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
      if (!errors.isEmpty()) {
        return !input.selection().expanded() && onlyInapplicabilityErrors(errors, input.replacementRange())
            ? ConstructorCorrelation.inapplicable()
            : ConstructorCorrelation.rejected();
      }
      return correlate.apply(new Attribution(units, Trees.instance(task), task.getTypes(), task.getElements()));
    }
  }

  @FunctionalInterface
  interface CorrelateAttribution {
    ConstructorCorrelation apply(Attribution attribution) throws IOException;
  }

  private static boolean onlyInapplicabilityErrors(List<Diagnostic<? extends JavaFileObject>> errors, ReplacementRange range) {
    return errors.stream().allMatch(error -> error.getSource() != null && range.contains(error.getSource().toUri(), error.getPosition())
        && ("compiler.err.cant.apply.symbol".equals(error.getCode()) || "compiler.err.cant.apply.symbols".equals(error.getCode())));
  }

  record Attribution(List<CompilationUnitTree> units, Trees trees, Types types, Elements elements) {
    Attribution {
      units = List.copyOf(units);
    }
  }
}
