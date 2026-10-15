package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.jspecify.annotations.Nullable;

/** Emits exact diagnostics for encountered compiler tree kinds classified as diagnosed. */
final class JavaCompilerKindDiagnostics {
  private final SnapshotSourceCatalog sources;
  private final JavaSourceEvidence sourceEvidence;
  private final LanguageFeatureCoverage coverage;
  private final JavaTranslationPolicy policy;

  JavaCompilerKindDiagnostics(SnapshotSourceCatalog sources, JavaSourceEvidence sourceEvidence, LanguageFeatureCoverage coverage,
      JavaTranslationPolicy policy) {
    this.sources = Objects.requireNonNull(sources, "sources");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.coverage = Objects.requireNonNull(coverage, "coverage");
    this.policy = policy;
  }

  ObservedLanguageFeatures observe(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    var diagnostics = new TranslationDiagnosticEvidence("JavaCompilerKindDiagnostics", "input-tree-scan");
    var scanner = new DiagnosticScanner(occurrences, observations, diagnostics);
    for (var unit : units) {
      scanUnit(scanner, unit);
    }
    return new ObservedLanguageFeatures(occurrences, observations, diagnostics);
  }

  private void scanUnit(DiagnosticScanner scanner, CompilationUnitTree unit) {
    var sourceFile = sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
    scanner.scan(unit, new ScanContext(unit, sourceFile));
  }

  private final class DiagnosticScanner extends TreePathScanner<Void, ScanContext> {
    private final List<LanguageFeatureOccurrence> occurrences;
    private final List<LanguageFeatureObservation> observations;
    private final Set<SourceSpan> observedAnnotationRanges = new HashSet<>();
    private final List<AnalysisDiagnostic> diagnostics;

    private DiagnosticScanner(List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations,
        List<AnalysisDiagnostic> diagnostics) {
      this.occurrences = occurrences;
      this.observations = observations;
      this.diagnostics = diagnostics;
    }

    @Override
    public Void scan(@Nullable Tree tree, ScanContext context) {
      if (tree == null) {
        return null;
      }
      if (JavaCoverageTopology.isDerivedCompactParameter(tree, getCurrentPath(), context.unit(), context.sourceFile(), sourceEvidence)) {
        return null;
      }
      var parent = getCurrentPath();
      if (policy.supportsEnumInitialization() && parent != null
          && JavaEnumConstantSyntax.isImplicitEnvelope(new TreePath(parent, tree), sourceEvidence)) {
        return super.scan(tree, context);
      }
      var kind = tree.getKind();
      var origins = sourceEvidence.origins(context.unit(), context.sourceFile(), tree);
      if ((kind == Tree.Kind.ANNOTATION || kind == Tree.Kind.TYPE_ANNOTATION) && origins.size() == 1
          && !observedAnnotationRanges.add(origins.getFirst())) {
        return null;
      }
      var feature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, kind.name());
      var classification = coverage.classificationOf(feature)
          .orElseThrow(() -> new IllegalStateException("Unclassified Java compiler tree kind: " + kind));
      Optional<AnalysisDiagnostic> diagnostic = Optional.empty();
      if (classification.policy() == LanguageFeaturePolicy.DIAGNOSED) {
        var code = classification.diagnosticCode().orElseThrow();
        var outcomeKind = kind == Tree.Kind.ERRONEOUS
            ? AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE
            : AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE;
        diagnostic = Optional.of(FrontendDiagnosticFactory.error(context.sourceFile().sourceFileKey(), code,
            "Java compiler tree kind " + kind + " is outside the supported semantic graph profile", origins.stream().findFirst(),
            outcomeKind));
        TranslationDiagnosticEvidence.emit(diagnostics, "JavaCompilerKindDiagnostics", diagnostic.orElseThrow());
      }
      occurrences.add(new LanguageFeatureOccurrence(context.sourceFile().sourceFileKey(), classification, origins));
      observations.add(new LanguageFeatureObservation(context.sourceFile().sourceFileKey(), classification, origins, diagnostic));
      return super.scan(tree, context);
    }
  }

  private record ScanContext(CompilationUnitTree unit, SourceFile sourceFile) {
    private ScanContext {
      Objects.requireNonNull(unit, "unit");
      Objects.requireNonNull(sourceFile, "sourceFile");
    }
  }
}
