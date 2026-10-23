package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxNode;
import java.util.List;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaType;

/** Owns one unit's legal progression and confines unfinished work to its compiler session. */
final class JavaUnitTranslator {
  enum ProjectionDecision {
    ALLOW, WITHHOLD
  }
  private enum Phase {
    UNSTARTED, DECLARING, DECLARATIONS_PRODUCED, ALLOWED, PROJECTING, PROJECTED, FINALIZING, FINALIZED, WITHHELD, FAILED
  }

  @FunctionalInterface
  interface SourceProjection {
    TranslationResult<Optional<SyntaxNode>> project(CompilationUnitTree unit, SourceFile source, List<TranslatedMethodBody> bodies,
        List<JavaType> types);
  }

  private final CompilationUnitTree unit;
  private final SourceFile source;
  private final JavaTypeTranslator types;
  private final SourceProjection sourceProjection;
  private final JavaUnitFinalizer finalizer;
  private final List<TranslatedMethodBody> unfinishedBodies = new ArrayList<>();
  private Phase phase = Phase.UNSTARTED;
  private Optional<UnitDeclarations> declarations = Optional.empty();
  private Optional<TranslationResult<Optional<SyntaxNode>>> projected = Optional.empty();

  JavaUnitTranslator(CompilationUnitTree unit, SourceFile source, JavaTypeTranslator types, SourceProjection sourceProjection,
      JavaUnitFinalizer finalizer) {
    this.unit = Objects.requireNonNull(unit, "unit");
    this.source = Objects.requireNonNull(source, "source");
    this.types = Objects.requireNonNull(types, "types");
    this.sourceProjection = Objects.requireNonNull(sourceProjection, "sourceProjection");
    this.finalizer = Objects.requireNonNull(finalizer, "finalizer");
  }

  TranslationResult<UnitDeclarations> translateDeclarations() {
    transition(Phase.UNSTARTED, Phase.DECLARING);
    try {
      var translatedTypes = new ArrayList<JavaType>();
      var orders = new ArrayList<DeclarationMemberOrder>();
      var declarationTypes = new ArrayList<DeclarationTypeEvidence>();
      var diagnostics = new DiagnosticEmissionCollector("JavaProgramTranslator", "input-program-translation");
      var complete = true;
      var eligibility = ProjectionEligibility.ELIGIBLE;
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
            eligibility = eligibility.and(value.eligibility());
          } else {
            complete = false;
          }
          diagnostics.include(translation.emissions());
        }
      }
      var metadata = new UnitDeclarations(translatedTypes, orders, declarationTypes, complete, eligibility);
      declarations = Optional.of(metadata);
      var result = new TranslationResult<>(metadata, diagnostics.snapshot());
      transition(Phase.DECLARING, Phase.DECLARATIONS_PRODUCED);
      return result;
    } finally {
      failUnfinished(Phase.DECLARING);
    }
  }

  void decideProjection(ProjectionDecision decision) {
    require(Phase.DECLARATIONS_PRODUCED);
    Objects.requireNonNull(decision, "decision");
    if (decision == ProjectionDecision.ALLOW
        && (!declarations.orElseThrow().complete() || declarations.orElseThrow().eligibility() == ProjectionEligibility.INELIGIBLE)) {
      phase = Phase.FAILED;
      throw new IllegalStateException(declarations.orElseThrow().complete()
          ? "Ineligible unit declarations cannot be projected"
          : "Incomplete unit declarations cannot be projected");
    }
    phase = decision == ProjectionDecision.ALLOW ? Phase.ALLOWED : Phase.WITHHELD;
  }

  void projectSource() {
    transition(Phase.ALLOWED, Phase.PROJECTING);
    try {
      var value = sourceProjection.project(unit, source, List.copyOf(unfinishedBodies), declarations.orElseThrow().types());
      projected = Optional.of(value);
      transition(Phase.PROJECTING, Phase.PROJECTED);
    } finally {
      failUnfinished(Phase.PROJECTING);
    }
  }

  FinalizedUnit finalizeSource() {
    transition(Phase.PROJECTED, Phase.FINALIZING);
    try {
      var projection = projected.orElseThrow();
      var result = finalizer.finish(source, projection.value(), declarations.orElseThrow(), projection.emissions());
      transition(Phase.FINALIZING, result.contribution().isPresent() ? Phase.FINALIZED : Phase.FAILED);
      return result;
    } finally {
      failUnfinished(Phase.FINALIZING);
    }
  }

  private void failUnfinished(Phase active) {
    if (phase == active) {
      phase = Phase.FAILED;
    }
  }

  private void transition(Phase expected, Phase next) {
    require(expected);
    phase = next;
  }

  private void require(Phase expected) {
    if (phase != expected) {
      throw new IllegalStateException("Unit translation requires " + expected + " but is " + phase);
    }
  }
}
