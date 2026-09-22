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
import org.aquilagraph.syntax.MethodSyntax;

final class JavaProgramTranslator {
  private final SnapshotSourceCatalog sources;
  private final JavaTypeTranslator typeTranslator;

  JavaProgramTranslator(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence) {
    this.sources = Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(semantics, "semantics");
    Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    var bodyTranslator = new JavaMethodBodyTranslator(semantics, sourceEvidence);
    var methodTranslator = new JavaMethodTranslator(semantics, sourceEvidence, bodyTranslator);
    typeTranslator = new JavaTypeTranslator(semantics, sourceEvidence, methodTranslator);
  }

  TranslationResult<TranslatedProgram> translate(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var types = new ArrayList<JavaType>();
    var methodSyntax = new ArrayList<MethodSyntax>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    for (var unit : units) {
      translateUnit(unit, types, methodSyntax, diagnostics);
    }
    return new TranslationResult<>(new TranslatedProgram(new JavaProgram(types), methodSyntax), diagnostics);
  }

  private void translateUnit(CompilationUnitTree unit, List<JavaType> types, List<MethodSyntax> methodSyntax,
      List<AnalysisDiagnostic> diagnostics) {
    var sourceFile = sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
    var unitPath = new TreePath(unit);
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = typeTranslator.translate(unit, unitPath, sourceFile, classTree);
        types.addAll(translation.value().types());
        methodSyntax.addAll(translation.value().methodSyntax());
        diagnostics.addAll(translation.diagnostics());
      }
    }
  }
}
