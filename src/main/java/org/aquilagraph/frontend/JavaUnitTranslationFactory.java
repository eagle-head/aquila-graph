package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.LanguageFeatureCoverage;

/** Composes fresh unit collaborators while borrowing the compilation's semantic services and policy. */
final class JavaUnitTranslationFactory {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final LanguageFeatureCoverage coverage;
  private final JavaTranslationPolicy policy;
  private final UnaryOperator<JavaUnitTranslator.SourceProjection> projections;

  JavaUnitTranslationFactory(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      LanguageFeatureCoverage coverage, JavaTranslationPolicy policy) {
    this(semantics, sourceEvidence, treeInventory, coverage, policy, UnaryOperator.identity());
  }

  JavaUnitTranslationFactory(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      LanguageFeatureCoverage coverage, JavaTranslationPolicy policy, UnaryOperator<JavaUnitTranslator.SourceProjection> projections) {
    this.projections = Objects.requireNonNull(projections, "projections");
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.coverage = Objects.requireNonNull(coverage, "coverage");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  JavaUnitTranslator create(CompilationUnitTree unit, SourceFile source) {
    var bodies = new JavaMethodBodyTranslator(semantics, sourceEvidence, treeInventory);
    var methods = new JavaMethodTranslator(semantics, sourceEvidence, treeInventory, bodies);
    var types = new JavaTypeTranslator(semantics, sourceEvidence, treeInventory, new JavaDeclarationPropertyObserver(coverage), methods,
        policy);
    var associations = new JavaWrittenSourceAssociations(treeInventory);
    var declarations = new JavaDeclarationScope();
    var correlation = new JavaBodySourceCorrelation(semantics, sourceEvidence, treeInventory, associations, declarations);
    var renderer = new JavaDeclarationProjector(semantics, sourceEvidence, treeInventory, new JavaAnnotationMirrorCorrelation(semantics),
        policy, associations, declarations, correlation);
    var sourceProjection = new JavaUnitSourceProjector(semantics, sourceEvidence, treeInventory, policy, associations, correlation,
        renderer);
    var finalizer = new JavaUnitFinalizer(correlation, associations, declarations);
    return new JavaUnitTranslator(unit, source, types, projections.apply(sourceProjection::project), finalizer);
  }
}
