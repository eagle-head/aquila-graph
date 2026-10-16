package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.util.List;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.JavaType;

/** Composes the real projection/finalization collaborators for controlled source-evidence tests. */
final class UnitSourceFixture {
  private final JavaUnitSourceProjector projection;
  private final JavaUnitFinalizer finalizer;
  private final JavaDeclarationProjector declarations;

  UnitSourceFixture(SemanticResolver semantics, JavaSourceEvidence evidence, ParsedTreeInventory inventory, JavaTranslationPolicy policy) {
    this(semantics, evidence, inventory, new JavaAnnotationMirrorCorrelation(semantics), policy);
  }

  UnitSourceFixture(SemanticResolver semantics, JavaSourceEvidence evidence, ParsedTreeInventory inventory,
      RecordAnnotationMirrorCorrelation mirrors, JavaTranslationPolicy policy) {
    var associations = new JavaWrittenSourceAssociations(inventory);
    var scope = new JavaDeclarationScope();
    var correlation = new JavaBodySourceCorrelation(semantics, evidence, inventory, associations, scope);
    declarations = new JavaDeclarationProjector(semantics, evidence, inventory, mirrors, policy, associations, scope, correlation);
    projection = new JavaUnitSourceProjector(semantics, evidence, inventory, policy, associations, correlation, declarations);
    finalizer = new JavaUnitFinalizer(correlation, associations, scope);
  }

  JavaDeclarationProjector declarations() {
    return declarations;
  }

  FinalizedUnit translate(CompilationUnitTree unit, SourceFile source, List<TranslatedMethodBody> bodies, List<JavaType> types) {
    var projected = projection.project(unit, source, bodies, types);
    return finalizer.finish(source, projected.value(), new UnitDeclarations(types, List.of(), List.of(), true), projected.emissions());
  }
}
