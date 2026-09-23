package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.syntax.JavaAstAggregate;

final class JavaProgramTranslator {
  private final SnapshotSourceCatalog sources;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaDeclarationPropertyObserver propertyObserver;
  private final JavaTypeTranslator typeTranslator;
  private final JavaAstContributionTranslator astTranslator;

  JavaProgramTranslator(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence,
      ParsedTreeInventory treeInventory, LanguageFeatureCoverage coverage) {
    this.sources = Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    propertyObserver = new JavaDeclarationPropertyObserver(coverage);
    var bodyTranslator = new JavaMethodBodyTranslator(semantics, sourceEvidence, treeInventory, propertyObserver);
    var methodTranslator = new JavaMethodTranslator(semantics, sourceEvidence, propertyObserver, bodyTranslator);
    typeTranslator = new JavaTypeTranslator(semantics, sourceEvidence, propertyObserver, methodTranslator);
    astTranslator = new JavaAstContributionTranslator(semantics, sourceEvidence, treeInventory);
  }

  TranslationResult<TranslatedProgram> translate(InputSnapshot snapshot, List<CompilationUnitTree> units) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(units, "units");
    var types = new ArrayList<JavaType>();
    var methodBodies = new ArrayList<TranslatedMethodBody>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    var unitsBySource = new HashMap<String, CompilationUnitTree>();
    for (var unit : units) {
      var source = sourceFor(unit);
      if (unitsBySource.putIfAbsent(source.sourceFileKey(), unit) != null) {
        throw new IllegalArgumentException("Duplicate parsed compilation unit for source " + source.sourceFileKey());
      }
    }
    var builder = JavaAstAggregate.builder(snapshot);
    var incompleteRoot = false;
    for (var source : snapshot.sourceFiles()) {
      var unit = Objects.requireNonNull(unitsBySource.get(source.sourceFileKey()), "parsed compilation unit");
      var firstBody = methodBodies.size();
      translateUnit(unit, types, methodBodies, diagnostics, occurrences, observations);
      var ast = astTranslator.translate(unit, source, methodBodies.subList(firstBody, methodBodies.size()));
      diagnostics.addAll(ast.diagnostics());
      if (ast.contribution().isPresent()) {
        builder.add(ast.contribution().orElseThrow());
        ast.bodies().forEach(body -> builder.addMethodBody(body.methodKey(), body.root(), body.origin()));
      } else {
        incompleteRoot = true;
      }
    }
    var program = incompleteRoot ? new JavaProgram(List.of()) : new JavaProgram(types);
    var aggregate = incompleteRoot ? Optional.<JavaAstAggregate>empty() : Optional.of(builder.seal());
    return new TranslationResult<>(new TranslatedProgram(program, aggregate), diagnostics, occurrences, observations);
  }

  private void translateUnit(CompilationUnitTree unit, List<JavaType> types, List<TranslatedMethodBody> methodBodies,
      List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    var sourceFile = sourceFor(unit);
    var unitPath = new TreePath(unit);
    diagnoseCompilationUnitProperties(unit, sourceFile, diagnostics, occurrences, observations);
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = typeTranslator.translate(unit, unitPath, sourceFile, classTree);
        types.addAll(translation.value().types());
        methodBodies.addAll(translation.value().methodBodies());
        diagnostics.addAll(translation.diagnostics());
        occurrences.addAll(translation.featureOccurrences());
        observations.addAll(translation.featureObservations());
      }
    }
  }

  private SourceFile sourceFor(CompilationUnitTree unit) {
    return sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
  }

  private void diagnoseCompilationUnitProperties(CompilationUnitTree unit, SourceFile sourceFile, List<AnalysisDiagnostic> diagnostics,
      List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    if (unit.getModule() != null) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, sourceFile.sourceFileKey(),
          JavaDeclarationPropertyCoverage.MODULE_DECLARATION_DIAGNOSTIC,
          "Module declarations are outside the supported semantic graph profile", sourceEvidence.origin(unit, sourceFile, unit.getModule()),
          JavaDeclarationPropertyCoverage.COMPILATION_UNIT_MODULE);
    }
  }
}
