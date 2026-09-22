package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.util.List;
import java.util.Objects;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

record JavaCompilation(List<CompilationUnitTree> units, SemanticResolver semantics, JavaSourceEvidence sourceEvidence,
    List<Diagnostic<? extends JavaFileObject>> diagnostics) {
  JavaCompilation {
    units = List.copyOf(Objects.requireNonNull(units, "units"));
    Objects.requireNonNull(semantics, "semantics");
    Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }
}
