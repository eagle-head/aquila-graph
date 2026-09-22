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
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.syntax.MethodSyntax;

final class JavaProgramTranslator {
  private final SnapshotSourceCatalog sources;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaDeclarationPropertyObserver propertyObserver;
  private final JavaTypeTranslator typeTranslator;

  JavaProgramTranslator(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence,
      LanguageFeatureCoverage coverage) {
    this.sources = Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    propertyObserver = new JavaDeclarationPropertyObserver(coverage);
    var bodyTranslator = new JavaMethodBodyTranslator(semantics, sourceEvidence, propertyObserver);
    var methodTranslator = new JavaMethodTranslator(semantics, sourceEvidence, propertyObserver, bodyTranslator);
    typeTranslator = new JavaTypeTranslator(semantics, sourceEvidence, propertyObserver, methodTranslator);
  }

  TranslationResult<TranslatedProgram> translate(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var types = new ArrayList<JavaType>();
    var methodSyntax = new ArrayList<MethodSyntax>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    for (var unit : units) {
      translateUnit(unit, types, methodSyntax, diagnostics, occurrences, observations);
    }
    return new TranslationResult<>(new TranslatedProgram(new JavaProgram(types), methodSyntax), diagnostics, occurrences, observations);
  }

  private void translateUnit(CompilationUnitTree unit, List<JavaType> types, List<MethodSyntax> methodSyntax,
      List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    var sourceFile = sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
    var unitPath = new TreePath(unit);
    diagnoseCompilationUnitProperties(unit, sourceFile, diagnostics, occurrences, observations);
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = typeTranslator.translate(unit, unitPath, sourceFile, classTree);
        types.addAll(translation.value().types());
        methodSyntax.addAll(translation.value().methodSyntax());
        diagnostics.addAll(translation.diagnostics());
        occurrences.addAll(translation.featureOccurrences());
        observations.addAll(translation.featureObservations());
      }
    }
  }

  private void diagnoseCompilationUnitProperties(CompilationUnitTree unit, SourceFile sourceFile, List<AnalysisDiagnostic> diagnostics,
      List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    for (var importTree : unit.getImports()) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, sourceFile.sourceFileKey(),
          JavaDeclarationPropertyCoverage.IMPORT_DECLARATION_DIAGNOSTIC,
          "Import declarations are outside the supported semantic graph profile", sourceEvidence.origin(unit, sourceFile, importTree),
          JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS);
    }
    if (unit.getPackage() != null) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, sourceFile.sourceFileKey(),
          JavaDeclarationPropertyCoverage.PACKAGE_DECLARATION_DIAGNOSTIC,
          "Package declarations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, unit.getPackage()), JavaDeclarationPropertyCoverage.COMPILATION_UNIT_PACKAGE,
          JavaDeclarationPropertyCoverage.COMPILATION_UNIT_PACKAGE_NAME);
    }
    for (var annotation : unit.getPackageAnnotations()) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, sourceFile.sourceFileKey(),
          JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC,
          "Package declaration annotations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, annotation), JavaDeclarationPropertyCoverage.COMPILATION_UNIT_PACKAGE_ANNOTATIONS);
    }
    if (unit.getModule() != null) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, sourceFile.sourceFileKey(),
          JavaDeclarationPropertyCoverage.MODULE_DECLARATION_DIAGNOSTIC,
          "Module declarations are outside the supported semantic graph profile", sourceEvidence.origin(unit, sourceFile, unit.getModule()),
          JavaDeclarationPropertyCoverage.COMPILATION_UNIT_MODULE);
    }
  }
}
