package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.model.SourceFile;
import org.jspecify.annotations.Nullable;

/** Emits exact diagnostics for encountered compiler tree kinds classified as diagnosed. */
final class JavaCompilerKindDiagnostics {
  private final SnapshotSourceCatalog sources;
  private final JavaSourceEvidence sourceEvidence;
  private final LanguageFeatureCoverage coverage;

  JavaCompilerKindDiagnostics(SnapshotSourceCatalog sources, JavaSourceEvidence sourceEvidence, LanguageFeatureCoverage coverage) {
    this.sources = Objects.requireNonNull(sources, "sources");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.coverage = Objects.requireNonNull(coverage, "coverage");
  }

  ObservedLanguageFeatures observe(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    var scanner = new DiagnosticScanner(occurrences, observations);
    for (var unit : units) {
      scanUnit(scanner, unit);
    }
    return new ObservedLanguageFeatures(occurrences, observations);
  }

  private void scanUnit(DiagnosticScanner scanner, CompilationUnitTree unit) {
    var sourceFile = sources.find(unit).orElseThrow(() -> new IllegalStateException("Compilation unit is not owned by the input snapshot"));
    scanner.scan(unit, new ScanContext(unit, sourceFile));
  }

  private final class DiagnosticScanner extends TreeScanner<Void, ScanContext> {
    private final List<LanguageFeatureOccurrence> occurrences;
    private final List<LanguageFeatureObservation> observations;

    private DiagnosticScanner(List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
      this.occurrences = occurrences;
      this.observations = observations;
    }

    @Override
    public Void scan(@Nullable Tree tree, ScanContext context) {
      if (tree == null) {
        return null;
      }
      var kind = tree.getKind();
      var feature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, kind.name());
      var classification = coverage.classificationOf(feature)
          .orElseThrow(() -> new IllegalStateException("Unclassified Java compiler tree kind: " + kind));
      var origins = sourceEvidence.origins(context.unit(), context.sourceFile(), tree);
      Optional<AnalysisDiagnostic> diagnostic = Optional.empty();
      if (classification.policy() == LanguageFeaturePolicy.DIAGNOSED) {
        var code = classification.diagnosticCode().orElseThrow();
        var outcomeKind = kind == Tree.Kind.ERRONEOUS
            ? AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE
            : AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE;
        diagnostic = Optional.of(FrontendDiagnosticFactory.error(context.sourceFile().sourceFileKey(), code,
            "Java compiler tree kind " + kind + " is outside the supported semantic graph profile", origins.stream().findFirst(),
            outcomeKind));
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
