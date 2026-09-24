package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.type.TypeKind;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;

/** Observes attributed kinds of written declaration-header type uses. */
final class JavaTypeKindObserver {
  private final SnapshotSourceCatalog sources;
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final LanguageFeatureCoverage coverage;

  JavaTypeKindObserver(SnapshotSourceCatalog sources, SemanticResolver semantics, JavaSourceEvidence sourceEvidence,
      ParsedTreeInventory treeInventory, LanguageFeatureCoverage coverage) {
    this.sources = Objects.requireNonNull(sources, "sources");
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.coverage = Objects.requireNonNull(coverage, "coverage");
  }

  ObservedTypeKinds observe(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var scanner = new HeaderScanner(occurrences, observations, diagnostics);
    for (var unit : units) {
      scanner.observeUnit(unit, sourceFor(unit));
    }
    return new ObservedTypeKinds(occurrences, observations, diagnostics);
  }

  private SourceFile sourceFor(CompilationUnitTree unit) {
    return sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
  }

  private final class HeaderScanner extends TreePathScanner<Void, SourceFile> {
    private final List<LanguageFeatureOccurrence> occurrences;
    private final List<LanguageFeatureObservation> observations;
    private final List<AnalysisDiagnostic> diagnostics;
    private int typeOrdinal;

    private HeaderScanner(List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations,
        List<AnalysisDiagnostic> diagnostics) {
      this.occurrences = occurrences;
      this.observations = observations;
      this.diagnostics = diagnostics;
    }

    private void observeUnit(CompilationUnitTree unit, SourceFile source) {
      typeOrdinal = 0;
      scan(unit, source);
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree unit, SourceFile source) {
      for (var declaration : unit.getTypeDecls()) {
        if (declaration instanceof ClassTree) {
          scan(declaration, source);
        }
      }
      return null;
    }

    @Override
    public Void visitClass(ClassTree declaration, SourceFile source) {
      observeType(declaration.getExtendsClause(), source);
      declaration.getImplementsClause().forEach(tree -> observeType(tree, source));
      declaration.getPermitsClause().forEach(tree -> observeType(tree, source));
      declaration.getTypeParameters().forEach(parameter -> scan(parameter, source));
      declaration.getMembers().stream()
          .filter(member -> member instanceof ClassTree || member instanceof MethodTree || member instanceof VariableTree)
          .forEach(member -> scan(member, source));
      return null;
    }

    @Override
    public Void visitMethod(MethodTree declaration, SourceFile source) {
      observeType(declaration.getReturnType(), source);
      if (declaration.getReceiverParameter() != null) {
        scan(declaration.getReceiverParameter(), source);
      }
      declaration.getThrows().forEach(tree -> observeType(tree, source));
      declaration.getTypeParameters().forEach(parameter -> scan(parameter, source));
      declaration.getParameters().forEach(parameter -> scan(parameter, source));
      return null;
    }

    @Override
    public Void visitVariable(VariableTree declaration, SourceFile source) {
      if (treeInventory.isWritten(declaration)) {
        observeType(declaration.getType(), source);
      }
      return null;
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree declaration, SourceFile source) {
      declaration.getBounds().forEach(tree -> observeType(tree, source));
      return null;
    }

    private void observeType(Tree tree, SourceFile source) {
      if (tree == null || !treeInventory.isWritten(tree)) {
        return;
      }
      var diagnosticContext = source.sourceFileKey() + ":header-type:" + typeOrdinal;
      typeOrdinal++;
      var path = new TreePath(getCurrentPath(), tree);
      var unit = getCurrentPath().getCompilationUnit();
      var origin = sourceEvidence.origin(unit, source, tree);
      var nearestOrigin = nearestOrigin(unit, source, path, origin);
      var attribution = semantics.attributedType(path);
      if (attribution == JavaTypeAttribution.Unresolved.INSTANCE) {
        diagnostics.add(FrontendDiagnosticFactory.otherError(diagnosticContext, "frontend.unresolved-attributed-type-kind",
            "A written declaration-header type has no compiler attribution", nearestOrigin));
        return;
      }
      var kind = switch (attribution) {
        case JavaTypeAttribution.Resolved resolved -> resolved.kind();
        case JavaTypeAttribution.NoType noType -> noType.kind();
        case JavaTypeAttribution.Erroneous ignored -> TypeKind.ERROR;
        case JavaTypeAttribution.Unsupported ignored -> TypeKind.OTHER;
        case JavaTypeAttribution.Unresolved ignored -> throw new IllegalStateException("Unavailable attribution was handled above");
      };
      observeKind(kind, source, origin, nearestOrigin, diagnosticContext);
    }

    private Optional<SourceSpan> nearestOrigin(CompilationUnitTree unit, SourceFile source, TreePath path, Optional<SourceSpan> origin) {
      var nearest = origin;
      var ancestor = path.getParentPath();
      while (nearest.isEmpty() && ancestor != null) {
        nearest = sourceEvidence.origin(unit, source, ancestor.getLeaf());
        ancestor = ancestor.getParentPath();
      }
      return nearest;
    }

    private void observeKind(TypeKind kind, SourceFile source, Optional<SourceSpan> origin, Optional<SourceSpan> diagnosticOrigin,
        String diagnosticContext) {
      var feature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TYPE_KIND, kind.name());
      var classification = coverage.classificationOf(feature)
          .orElseThrow(() -> new IllegalStateException("Unclassified Java type kind: " + kind));
      var origins = (classification.policy() == LanguageFeaturePolicy.DIAGNOSED ? diagnosticOrigin : origin).stream().toList();
      Optional<AnalysisDiagnostic> diagnostic = Optional.empty();
      if (classification.policy() == LanguageFeaturePolicy.DIAGNOSED) {
        var outcome = kind == TypeKind.ERROR
            ? AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE
            : AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE;
        diagnostic = Optional.of(FrontendDiagnosticFactory.error(diagnosticContext, classification.diagnosticCode().orElseThrow(),
            "Java attributed type kind " + kind + " is not a supported declaration-header type", diagnosticOrigin, outcome));
        diagnostics.add(diagnostic.orElseThrow());
      }
      occurrences.add(new LanguageFeatureOccurrence(source.sourceFileKey(), classification, origins));
      observations.add(new LanguageFeatureObservation(source.sourceFileKey(), classification, origins, diagnostic));
    }
  }

  record ObservedTypeKinds(List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations,
      List<AnalysisDiagnostic> diagnostics) {
    ObservedTypeKinds {
      occurrences = List.copyOf(occurrences);
      observations = List.copyOf(observations);
      diagnostics = List.copyOf(diagnostics);
    }
  }
}
