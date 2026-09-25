package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
  private final JavaDeclarationPropertyObserver propertyObserver;
  private final JavaTypeTranslator typeTranslator;
  private final JavaAstContributionTranslator astTranslator;

  JavaProgramTranslator(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence,
      ParsedTreeInventory treeInventory, LanguageFeatureCoverage coverage) {
    this.sources = Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(semantics, "semantics");
    Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    propertyObserver = new JavaDeclarationPropertyObserver(coverage);
    var bodyTranslator = new JavaMethodBodyTranslator(semantics, sourceEvidence, treeInventory);
    var methodTranslator = new JavaMethodTranslator(semantics, sourceEvidence, treeInventory, bodyTranslator);
    typeTranslator = new JavaTypeTranslator(semantics, sourceEvidence, treeInventory, propertyObserver, methodTranslator);
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
    var translatedTypeKeys = new HashSet<String>();
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
      var firstType = types.size();
      var firstDiagnostic = diagnostics.size();
      var completeUnit = translateUnit(unit, types, methodBodies, diagnostics, occurrences, observations);
      var unitTypes = types.subList(firstType, types.size());
      var duplicateType = false;
      for (var type : unitTypes) {
        if (!translatedTypeKeys.add(type.typeKey())) {
          diagnostics.add(FrontendDiagnosticFactory.otherError(type.typeKey(), "frontend.duplicate-type-declaration",
              "Translated type declarations share a canonical key", type.origins().stream().findFirst()));
          duplicateType = true;
        }
      }
      var duplicateMember = diagnostics.subList(firstDiagnostic, diagnostics.size()).stream()
          .anyMatch(diagnostic -> "frontend.duplicate-member-declaration".equals(diagnostic.code()));
      if (!completeUnit || duplicateType || duplicateMember) {
        incompleteRoot = true;
        continue;
      }
      var ast = astTranslator.translate(unit, source, methodBodies.subList(firstBody, methodBodies.size()), unitTypes);
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

  private boolean translateUnit(CompilationUnitTree unit, List<JavaType> types, List<TranslatedMethodBody> methodBodies,
      List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    var sourceFile = sourceFor(unit);
    var unitPath = new TreePath(unit);
    var complete = true;
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = typeTranslator.translate(unit, unitPath, sourceFile, classTree);
        if (translation.value().isPresent()) {
          types.addAll(translation.value().orElseThrow().types());
          methodBodies.addAll(translation.value().orElseThrow().methodBodies());
        } else {
          complete = false;
        }
        diagnostics.addAll(translation.diagnostics());
        occurrences.addAll(translation.featureOccurrences());
        observations.addAll(translation.featureObservations());
      }
    }
    return complete;
  }

  private SourceFile sourceFor(CompilationUnitTree unit) {
    return sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
  }

}
