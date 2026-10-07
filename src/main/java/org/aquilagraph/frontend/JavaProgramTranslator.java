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
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnalysisDiagnostic;
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
    var memberOrders = new ArrayList<DeclarationMemberOrder>();
    var declarationTypes = new ArrayList<DeclarationTypeEvidence>();
    var diagnostics = new TranslationDiagnosticEvidence("JavaProgramTranslator", "input-program-translation");
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
    var incompleteRoot = false;
    for (var source : snapshot.sourceFiles()) {
      var unit = Objects.requireNonNull(unitsBySource.get(source.sourceFileKey()), "parsed compilation unit");
      var firstBody = methodBodies.size();
      var firstType = types.size();
      var firstDiagnostic = diagnostics.size();
      var firstOrder = memberOrders.size();
      var firstDeclarationType = declarationTypes.size();
      var completeUnit = translateUnit(unit, types, methodBodies, memberOrders, declarationTypes, diagnostics, occurrences, observations);
      var unitTypes = types.subList(firstType, types.size());
      var duplicateType = false;
      for (var type : unitTypes) {
        if (!translatedTypeKeys.add(type.typeKey())) {
          TranslationDiagnosticEvidence.emit(diagnostics, "JavaProgramTranslator",
              FrontendDiagnosticFactory.otherError(type.typeKey(), "frontend.duplicate-type-declaration",
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
        sourceContributions.add(ast.contribution().orElseThrow());
        retainedAssociations.addAll(ast.sourceAssociations());
        var retainedSource = ast.contribution().orElseThrow();
        declarationTypes.subList(firstDeclarationType, declarationTypes.size()).stream()
            .filter(evidence -> retainsTypeOwner(retainedSource, evidence.sourceFileKey(), evidence.ownerTypeKey()))
            .forEach(retainedTypes::add);
        memberOrders.subList(firstOrder, memberOrders.size()).stream()
            .filter(order -> retainsTypeOwner(retainedSource, order.sourceFileKey(), order.ownerTypeKey()))
            .map(order -> attachWrittenOrder(order, retainedSource)).forEach(retainedOrders::add);
        ast.bodies()
            .forEach(body -> retainedBodies.add(new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(),
                body.decisions().uses(), body.decisions().invocations(), body.decisions().staticAccesses(),
                body.decisions().concatenations(), body.decisions().constructors(), body.attributedOccurrences())));
      } else {
        incompleteRoot = true;
      }
    }
    var program = incompleteRoot ? new JavaProgram(List.of()) : new JavaProgram(types);
    var aggregate = incompleteRoot
        ? Optional.<JavaAstAggregate>empty()
        : Optional.of(AstValueAdmission.admit(
            new CopiedAstValues(snapshot, sourceContributions, retainedBodies, retainedOrders, retainedAssociations, retainedTypes)));
    return new TranslationResult<>(new TranslatedProgram(program, aggregate), diagnostics, occurrences, observations);
  }

  private boolean translateUnit(CompilationUnitTree unit, List<JavaType> types, List<TranslatedMethodBody> methodBodies,
      List<DeclarationMemberOrder> memberOrders, List<DeclarationTypeEvidence> declarationTypes, List<AnalysisDiagnostic> diagnostics,
      List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    var sourceFile = sourceFor(unit);
    var unitPath = new TreePath(unit);
    var complete = true;
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = typeTranslator.translate(unit, unitPath, sourceFile, classTree);
        if (translation.value().isPresent()) {
          types.addAll(translation.value().orElseThrow().types());
          methodBodies.addAll(translation.value().orElseThrow().methodBodies());
          memberOrders.addAll(translation.value().orElseThrow().memberOrders());
          declarationTypes.addAll(translation.value().orElseThrow().declarationTypes());
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
            member.displayValue(), member.provenance(), Optional.ofNullable(positions.get(member.declarationKey())))).toList());
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
