package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.MethodSyntax;

final class JavaProgramTranslator {
  private final SnapshotSourceCatalog sources;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaTypeTranslator typeTranslator;

  JavaProgramTranslator(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence) {
    this.sources = Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
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
    diagnoseCompilationUnitProperties(unit, sourceFile, diagnostics);
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = typeTranslator.translate(unit, unitPath, sourceFile, classTree);
        types.addAll(translation.value().types());
        methodSyntax.addAll(translation.value().methodSyntax());
        diagnostics.addAll(translation.diagnostics());
      }
    }
  }

  private void diagnoseCompilationUnitProperties(CompilationUnitTree unit, SourceFile sourceFile, List<AnalysisDiagnostic> diagnostics) {
    for (var importTree : unit.getImports()) {
      diagnostics.add(diagnostic(sourceFile.sourceFileKey(), JavaDeclarationPropertyCoverage.IMPORT_DECLARATION_DIAGNOSTIC,
          "Import declarations are outside the supported semantic graph profile", sourceEvidence.origin(unit, sourceFile, importTree)));
    }
    if (unit.getPackage() != null) {
      diagnostics.add(diagnostic(sourceFile.sourceFileKey(), JavaDeclarationPropertyCoverage.PACKAGE_DECLARATION_DIAGNOSTIC,
          "Package declarations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, unit.getPackage())));
    }
    for (var annotation : unit.getPackageAnnotations()) {
      diagnostics.add(diagnostic(sourceFile.sourceFileKey(), JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC,
          "Package declaration annotations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, annotation)));
    }
    if (unit.getModule() != null) {
      diagnostics.add(diagnostic(sourceFile.sourceFileKey(), JavaDeclarationPropertyCoverage.MODULE_DECLARATION_DIAGNOSTIC,
          "Module declarations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, unit.getModule())));
    }
  }

  private static AnalysisDiagnostic diagnostic(String context, String code, String message, Optional<SourceSpan> origin) {
    return FrontendDiagnosticFactory.error(context, code, message, origin);
  }
}
