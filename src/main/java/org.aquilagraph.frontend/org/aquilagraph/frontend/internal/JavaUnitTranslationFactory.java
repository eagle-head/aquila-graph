package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.aquilagraph.identity.api.SourceFile;

/** Composes fresh unit collaborators while borrowing the compilation's semantic services and policy. */
final class JavaUnitTranslationFactory {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaTranslationPolicy policy;
  private final UnaryOperator<JavaUnitTranslator.SourceProjection> projections;

  JavaUnitTranslationFactory(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaTranslationPolicy policy) {
    this(semantics, sourceEvidence, treeInventory, policy, UnaryOperator.identity());
  }

  JavaUnitTranslationFactory(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaTranslationPolicy policy, UnaryOperator<JavaUnitTranslator.SourceProjection> projections) {
    this.projections = Objects.requireNonNull(projections, "projections");
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  JavaUnitTranslator create(CompilationUnitTree unit, SourceFile source) {
    var bodies = new JavaMethodBodyTranslator(semantics, sourceEvidence, treeInventory, JavaExecutableProjectionContext.metadata(policy));
    var methods = new JavaMethodTranslator(semantics, sourceEvidence, treeInventory, bodies);
    var types = new JavaTypeTranslator(semantics, sourceEvidence, treeInventory, methods, policy);
    var associations = new JavaWrittenSourceAssociations(treeInventory);
    var declarations = new JavaDeclarationScope();
    var correlation = new JavaBodySourceCorrelation(semantics, sourceEvidence, treeInventory, associations, declarations);
    var headers = new JavaDeclarationHeaderProjector(semantics, sourceEvidence, treeInventory,
        new JavaAnnotationMirrorCorrelation(semantics), policy, associations, declarations::fieldAnnotations);
    var renderer = new JavaDeclarationProjector(semantics, sourceEvidence, treeInventory, headers, policy, associations, declarations,
        correlation);
    var sourceProjection = new JavaUnitSourceProjector(semantics, sourceEvidence, treeInventory, policy, associations, correlation,
        renderer);
    var finalizer = new JavaUnitFinalizer(correlation, associations, declarations);
    return new JavaUnitTranslator(unit, source, types, projections.apply(sourceProjection::project), finalizer);
  }
}
