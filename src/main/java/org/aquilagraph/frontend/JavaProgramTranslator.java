package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.WrittenSourceAssociation;

final class JavaProgramTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaProgramTranslator";
  private final SnapshotSourceCatalog sources;
  private final JavaUnitTranslationFactory unitTranslations;

  JavaProgramTranslator(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence,
      ParsedTreeInventory treeInventory, JavaTranslationPolicy policy) {
    this.sources = Objects.requireNonNull(sources, "sources");
    unitTranslations = new JavaUnitTranslationFactory(semantics, sourceEvidence, treeInventory, policy);
  }

  JavaProgramTranslator(SnapshotSourceCatalog sources, JavaUnitTranslationFactory unitTranslations) {
    this.sources = Objects.requireNonNull(sources, "sources");
    this.unitTranslations = Objects.requireNonNull(unitTranslations, "unitTranslations");
  }

  TranslationResult<TranslatedProgram> translate(InputSnapshot snapshot, List<CompilationUnitTree> units) {
    return translate(snapshot, units, false);
  }

  TranslationResult<TranslatedProgram> translate(InputSnapshot snapshot, List<CompilationUnitTree> units, boolean compilerErrors) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(units, "units");
    var types = new ArrayList<JavaType>();
    var diagnostics = new DiagnosticEmissionCollector(DIAGNOSTIC_PRODUCER, "input-program-translation");
    var unitsBySource = new HashMap<String, CompilationUnitTree>();
    var translatedTypeKeys = new HashSet<String>();
    for (var unit : units) {
      var source = sourceFor(unit);
      if (unitsBySource.putIfAbsent(source.sourceFileKey(), unit) != null) {
        throw new IllegalArgumentException("Duplicate parsed compilation unit for source " + source.sourceFileKey());
      }
    }
    var sourceContributions = new ArrayList<SyntaxSourceContribution>();
    var retainedBodies = new ArrayList<CopiedAstValues.Body>();
    var retainedOrders = new ArrayList<DeclarationMemberOrder>();
    var retainedAssociations = new ArrayList<WrittenSourceAssociation>();
    var retainedTypes = new ArrayList<DeclarationTypeEvidence>();
    var retainedScopes = new ArrayList<CopiedAstValues.Scope>();
    var incompleteRoot = false;
    for (var source : snapshot.sourceFiles()) {
      var unit = unitsBySource.get(source.sourceFileKey());
      if (unit == null) {
        diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(source.sourceFileKey(),
            "frontend.missing-compilation-unit", "A captured source has no correlated compiler compilation unit", Optional.empty()));
        incompleteRoot = true;
        continue;
      }
      var unitTranslator = unitTranslations.create(unit, source);
      final TranslationResult<UnitDeclarations> translated;
      try {
        translated = unitTranslator.translateDeclarations();
      } catch (JavaSourceProjectionFailure failure) {
        diagnostics.include(failure.emissions());
        incompleteRoot = true;
        continue;
      }
      var declarations = translated.value();
      var unitTypes = declarations.types();
      types.addAll(unitTypes);
      diagnostics.include(translated.emissions());
      var duplicateType = false;
      for (var type : unitTypes) {
        if (!translatedTypeKeys.add(type.typeKey())) {
          diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(type.typeKey(), "frontend.duplicate-type-declaration",
              "Translated type declarations share a canonical key", type.origins().stream().findFirst()));
          duplicateType = true;
        }
      }
      var decision = !declarations.complete() || duplicateType || declarations.eligibility() == ProjectionEligibility.INELIGIBLE
          ? JavaUnitTranslator.ProjectionDecision.WITHHOLD
          : JavaUnitTranslator.ProjectionDecision.ALLOW;
      unitTranslator.decideProjection(decision);
      if (decision == JavaUnitTranslator.ProjectionDecision.WITHHOLD) {
        incompleteRoot = true;
        continue;
      }
      final FinalizedUnit ast;
      try {
        unitTranslator.projectSource();
        ast = unitTranslator.finalizeSource();
      } catch (JavaSourceProjectionFailure failure) {
        diagnostics.include(failure.emissions());
        incompleteRoot = true;
        continue;
      }
      diagnostics.include(ast.emissions());
      if (ast.contribution().isPresent()) {
        sourceContributions.add(ast.contribution().orElseThrow());
        retainedAssociations.addAll(ast.sourceAssociations());
        retainedScopes.addAll(ast.scopes());
        retainedTypes.addAll(ast.declarationTypes());
        retainedOrders.addAll(ast.memberOrders());
        retainedBodies.addAll(ast.bodies());
      } else {
        incompleteRoot = true;
      }
    }
    var program = new JavaProgram(List.of());
    Optional<JavaAstAggregate> aggregate;
    try {
      if (!incompleteRoot) {
        program = new JavaProgram(types);
      }
      aggregate = incompleteRoot
          ? Optional.empty()
          : Optional.of(AstValueAdmission.admit(new CopiedAstValues(snapshot, sourceContributions, retainedBodies, retainedOrders,
              retainedAssociations, retainedTypes, retainedScopes)));
    } catch (IllegalArgumentException failure) {
      if (!compilerErrors) {
        throw failure;
      }
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(snapshot.snapshotDigest(), "frontend.unavailable-source-structure",
              Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()), Optional.empty()));
      aggregate = Optional.empty();
      program = new JavaProgram(List.of());
    }
    return new TranslationResult<>(new TranslatedProgram(program, aggregate), diagnostics.snapshot());
  }

  private SourceFile sourceFor(CompilationUnitTree unit) {
    return sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
  }

}
