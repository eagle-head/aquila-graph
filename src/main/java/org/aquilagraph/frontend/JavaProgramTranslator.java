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
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;

final class JavaProgramTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaProgramTranslator";
  private final SnapshotSourceCatalog sources;
  private final JavaUnitTranslationFactory unitTranslations;

  JavaProgramTranslator(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence,
      ParsedTreeInventory treeInventory, LanguageFeatureCoverage coverage, JavaTranslationPolicy policy) {
    this.sources = Objects.requireNonNull(sources, "sources");
    unitTranslations = new JavaUnitTranslationFactory(semantics, sourceEvidence, treeInventory, coverage, policy);
  }

  TranslationResult<TranslatedProgram> translate(InputSnapshot snapshot, List<CompilationUnitTree> units) {
    return translate(snapshot, units, false);
  }

  TranslationResult<TranslatedProgram> translate(InputSnapshot snapshot, List<CompilationUnitTree> units, boolean compilerErrors) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(units, "units");
    var types = new ArrayList<JavaType>();
    var diagnostics = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, "input-program-translation");
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
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(source.sourceFileKey(),
            "frontend.missing-compilation-unit", "A captured source has no correlated compiler compilation unit", Optional.empty()));
        incompleteRoot = true;
        continue;
      }
      var unitTranslator = unitTranslations.create(unit, source);
      var translated = unitTranslator.translateDeclarations();
      var declarations = translated.value();
      var unitTypes = declarations.types();
      types.addAll(unitTypes);
      diagnostics.addAll(translated.diagnostics());
      occurrences.addAll(translated.featureOccurrences());
      observations.addAll(translated.featureObservations());
      var duplicateType = false;
      for (var type : unitTypes) {
        if (!translatedTypeKeys.add(type.typeKey())) {
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
              FrontendDiagnosticFactory.otherError(type.typeKey(), "frontend.duplicate-type-declaration",
                  "Translated type declarations share a canonical key", type.origins().stream().findFirst()));
          duplicateType = true;
        }
      }
      var duplicateMember = translated.diagnostics().stream()
          .anyMatch(diagnostic -> "frontend.duplicate-member-declaration".equals(diagnostic.code()));
      if (!declarations.complete() || duplicateType || duplicateMember) {
        incompleteRoot = true;
        continue;
      }
      final JavaDeclarationProjector.UnitTranslation ast;
      try {
        ast = unitTranslator.projectSource();
      } catch (JavaSourceProjectionFailure failure) {
        diagnostics.addAll(failure.diagnostics());
        incompleteRoot = true;
        continue;
      }
      diagnostics.addAll(ast.diagnostics());
      if (ast.contribution().isPresent()) {
        sourceContributions.add(ast.contribution().orElseThrow());
        retainedAssociations.addAll(ast.sourceAssociations());
        retainedScopes.addAll(ast.scopes());
        var retainedSource = ast.contribution().orElseThrow();
        declarations.declarationTypes().stream()
            .filter(evidence -> retainsTypeOwner(retainedSource, evidence.sourceFileKey(), evidence.ownerTypeKey()))
            .forEach(retainedTypes::add);
        declarations.memberOrders().stream().filter(order -> retainsTypeOwner(retainedSource, order.sourceFileKey(), order.ownerTypeKey()))
            .map(order -> attachWrittenOrder(order, retainedSource)).forEach(retainedOrders::add);
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
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(snapshot.snapshotDigest(), "frontend.unavailable-source-structure",
              Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()), Optional.empty()));
      aggregate = Optional.empty();
      program = new JavaProgram(List.of());
    }
    return new TranslationResult<>(new TranslatedProgram(program, aggregate), diagnostics, occurrences, observations);
  }

  private SourceFile sourceFor(CompilationUnitTree unit) {
    return sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
  }

  private static boolean retainsTypeOwner(SyntaxSourceContribution source, String sourceFileKey, String ownerTypeKey) {
    return source.nodes().stream().filter(node -> node.sourceFileKey().equals(sourceFileKey)).anyMatch(
        node -> (node.value() instanceof SyntaxValue.TypeDeclaration declaration && declaration.declarationKey().equals(ownerTypeKey))
            || (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT
                && node.children(SyntaxRole.COMPACT_MEMBER).stream()
                    .anyMatch(member -> member.value() instanceof SyntaxValue.ExecutableDeclaration declaration
                        && declaration.ownerKey().equals(ownerTypeKey))));
  }

  private static DeclarationMemberOrder attachWrittenOrder(DeclarationMemberOrder order, SyntaxSourceContribution source) {
    var owner = source.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration declaration
        && declaration.declarationKey().equals(order.ownerTypeKey())).findFirst();
    var members = owner.map(node -> node.children(SyntaxRole.MEMBER)).orElseGet(() -> source.root().children(SyntaxRole.COMPACT_MEMBER));
    var positions = new HashMap<String, Integer>();
    for (var index = 0; index < members.size(); index++) {
      var key = sourceMemberKey(members.get(index));
      if (key.isPresent()) {
        positions.put(key.orElseThrow(), index);
      }
    }
    return new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(),
        order.members().stream().map(member -> new DeclarationMemberOrder.Member(member.declarationKey(), member.kind(),
            member.displayValue(), member.provenance(), Optional.ofNullable(positions.get(member.declarationKey())))).toList(),
        order.attributedTreeOrder());
  }

  private static Optional<String> sourceMemberKey(SyntaxNode member) {
    return switch (member.value()) {
      case SyntaxValue.TypeDeclaration type -> Optional.of(type.declarationKey());
      case SyntaxValue.ExecutableDeclaration method -> Optional.of(method.declarationKey());
      case SyntaxValue.VariableDeclaration field -> Optional.of(field.declarationKey());
      default -> Optional.empty();
    };
  }

}
