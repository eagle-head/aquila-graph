package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaType;

final class JavaProgramTranslator {
  private final SnapshotSourceCatalog sources;
  private final JavaTypeTranslator typeTranslator;

  JavaProgramTranslator(SnapshotSourceCatalog sources, JavaSemanticResolver semantics, JavaSourceEvidence sourceEvidence) {
    this.sources = Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(semantics, "semantics");
    Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    var bodyTranslator = new JavaMethodBodyTranslator(semantics, sourceEvidence);
    var methodTranslator = new JavaMethodTranslator(semantics, sourceEvidence, bodyTranslator);
    typeTranslator = new JavaTypeTranslator(semantics, sourceEvidence, methodTranslator);
  }

  TranslationResult<JavaProgram> translate(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var types = new ArrayList<JavaType>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    for (var unit : units) {
      translateUnit(unit, types, diagnostics);
    }
    return new TranslationResult<>(new JavaProgram(types), diagnostics);
  }

  private void translateUnit(CompilationUnitTree unit, List<JavaType> types, List<AnalysisDiagnostic> diagnostics) {
    var sourceFile = sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
    var unitPath = new TreePath(unit);
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = typeTranslator.translate(unit, unitPath, sourceFile, classTree);
        types.add(translation.value());
        diagnostics.addAll(translation.diagnostics());
      }
    }
  }
}
