package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.util.Objects;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.LanguageFeatureCoverage;

/** Composes fresh unit collaborators while borrowing the compilation's semantic services and policy. */
final class JavaUnitTranslationFactory {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final LanguageFeatureCoverage coverage;
  private final JavaTranslationPolicy policy;

  JavaUnitTranslationFactory(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      LanguageFeatureCoverage coverage, JavaTranslationPolicy policy) {
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
    var sourceProjection = new JavaAstContributionTranslator(semantics, sourceEvidence, treeInventory, policy);
    return new JavaUnitTranslator(unit, source, types, sourceProjection);
  }
}
