package org.aquilagraph.frontend.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;

/** Checks that declaration-header type tree forms have retained occurrences at their written ranges. */
final class JavaTypeUseCompleteness {
  private static final int EXACT_SOURCE_RANGE_COUNT = 1;

  private JavaTypeUseCompleteness() {
  }

  static DiagnosticEmissions reconcile(InputSnapshot snapshot, List<LanguageFeatureObservation> observations,
      Optional<JavaAstAggregate> aggregate) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(observations, "observations");
    Objects.requireNonNull(aggregate, "aggregate");
    var retained = new HashMap<TypeOccurrence, Integer>();
    aggregate.ifPresent(value -> {
      for (var node : value.nodes()) {
        if (node.syntaxKind() == SyntaxKind.TYPE_USE && requiresOccurrence(node.sourceKind())) {
          var occurrence = occurrence(node.sourceFileKey(), node.sourceKind(), node.origin().orElseThrow());
          retained.merge(occurrence, 1, Integer::sum);
        }
      }
    });

    var diagnostics = new DiagnosticEmissionCollector("JavaTypeUseCompleteness", "input-type-use-reconciliation");
    var sourceKindOrdinals = new HashMap<String, Integer>();
    for (var observation : observations) {
      var feature = observation.classification().feature();
      if (feature.category() != LanguageFeatureCategory.COMPILER_TREE_KIND) {
        continue;
      }
      var kind = JavaSourceKind.valueOf(feature.identifier());
      var sourceKind = observation.sourceFileKey() + ':' + kind.name();
      var ordinal = sourceKindOrdinals.merge(sourceKind, 1, Integer::sum);
      var context = sourceKind + ':' + ordinal;
      if (!requiresOccurrence(kind)) {
        continue;
      }
      if (observation.origins().size() != EXACT_SOURCE_RANGE_COUNT) {
        diagnostics.emit("JavaTypeUseCompleteness",
            FrontendDiagnosticFactory.otherError(context, "frontend.unavailable-source-type-range",
                "A written type tree in " + sourcePath(snapshot, observation.sourceFileKey()) + " has no exact source range: " + kind,
                Optional.empty()));
        continue;
      }
      var origin = observation.origins().getFirst();
      var occurrence = occurrence(observation.sourceFileKey(), kind, origin);
      var count = retained.getOrDefault(occurrence, 0);
      if (count == 0) {
        diagnostics.emit("JavaTypeUseCompleteness",
            FrontendDiagnosticFactory.capabilityError(context, "frontend.unrepresented-source-type-use",
                "A written type tree has no retained type-use occurrence: " + kind, Optional.of(origin)));
      } else {
        retained.put(occurrence, count - 1);
      }
    }
    return diagnostics.snapshot();
  }

  private static boolean requiresOccurrence(JavaSourceKind kind) {
    return switch (kind) {
      case ANNOTATED_TYPE, ARRAY_TYPE, EXTENDS_WILDCARD, PARAMETERIZED_TYPE, PRIMITIVE_TYPE, SUPER_WILDCARD, UNBOUNDED_WILDCARD -> true;
      default -> false;
    };
  }

  private static TypeOccurrence occurrence(String sourceFileKey, JavaSourceKind kind, SourceSpan range) {
    return new TypeOccurrence(sourceFileKey, kind, range);
  }

  private static String sourcePath(InputSnapshot snapshot, String sourceFileKey) {
    return snapshot.sourceFiles().stream().filter(sourceFile -> sourceFile.sourceFileKey().equals(sourceFileKey)).findFirst()
        .orElseThrow(() -> new IllegalStateException("Observed compiler tree is not owned by the input snapshot")).relativePath()
        .toString();
  }

  private record TypeOccurrence(String sourceFileKey, JavaSourceKind kind, SourceSpan range) {
  }
}
