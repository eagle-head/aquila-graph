package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;

/** Owns unfinished compiler-bearing work for one unit until detached contributions are sealed. */
final class JavaUnitTranslator {
  private final CompilationUnitTree unit;
  private final SourceFile source;
  private final JavaTypeTranslator types;
  private final JavaAstContributionTranslator sourceProjection;
  private final List<TranslatedMethodBody> unfinishedBodies = new ArrayList<>();
  private Optional<Declarations> declarations = Optional.empty();

  JavaUnitTranslator(CompilationUnitTree unit, SourceFile source, JavaTypeTranslator types,
      JavaAstContributionTranslator sourceProjection) {
    this.unit = unit;
    this.source = source;
    this.types = types;
    this.sourceProjection = sourceProjection;
  }

  TranslationResult<Declarations> translateDeclarations() {
    if (declarations.isPresent()) {
      throw new IllegalStateException("Unit declarations have already been translated");
    }
    var translatedTypes = new ArrayList<JavaType>();
    var orders = new ArrayList<DeclarationMemberOrder>();
    var declarationTypes = new ArrayList<DeclarationTypeEvidence>();
    var diagnostics = new TranslationDiagnosticEvidence("JavaProgramTranslator", "input-program-translation");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    var complete = true;
    var unitPath = new TreePath(unit);
    for (var declaration : unit.getTypeDecls()) {
      if (declaration instanceof ClassTree classTree) {
        var translation = types.translate(unit, unitPath, source, classTree);
        if (translation.value().isPresent()) {
          var value = translation.value().orElseThrow();
          translatedTypes.addAll(value.types());
          unfinishedBodies.addAll(value.methodBodies());
          orders.addAll(value.memberOrders());
          declarationTypes.addAll(value.declarationTypes());
        } else {
          complete = false;
        }
        diagnostics.addAll(translation.diagnostics());
        occurrences.addAll(translation.featureOccurrences());
        observations.addAll(translation.featureObservations());
      }
    }
    var value = new Declarations(translatedTypes, orders, declarationTypes, complete);
    declarations = Optional.of(value);
    return new TranslationResult<>(value, diagnostics, occurrences, observations);
  }

  JavaAstContributionTranslator.UnitTranslation projectSource() {
    var value = declarations.orElseThrow(() -> new IllegalStateException("Unit declarations have not been translated"));
    return sourceProjection.translate(unit, source, unfinishedBodies, value.types());
  }

  record Declarations(List<JavaType> types, List<DeclarationMemberOrder> memberOrders, List<DeclarationTypeEvidence> declarationTypes,
      boolean complete) {
    Declarations {
      types = List.copyOf(types);
      memberOrders = List.copyOf(memberOrders);
      declarationTypes = List.copyOf(declarationTypes);
    }
  }
}
