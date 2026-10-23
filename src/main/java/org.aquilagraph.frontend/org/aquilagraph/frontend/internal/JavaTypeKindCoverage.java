package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.type.TypeKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;

/** Defines the closed attributed type-kind coverage contract for the configured Java release. */
final class JavaTypeKindCoverage {
  private static final String OBSERVATION_BOUNDARY = "LanguageFeatureObservation attributed type kind";
  private static final String DIAGNOSTIC_PREFIX = "frontend.unsupported-type-kind.";

  private JavaTypeKindCoverage() {
  }

  static LanguageFeatureCoverage fromConfiguration(CompilerConfiguration configuration) {
    return fromClassifications(configuration, classifications());
  }

  static LanguageFeatureCoverage fromClassifications(CompilerConfiguration configuration,
      List<LanguageFeatureClassification> classifications) {
    Objects.requireNonNull(configuration, "configuration");
    var coverage = LanguageFeatureCoverageFactory.fromConfiguration(configuration, classifications);
    var declaredKinds = EnumSet.noneOf(TypeKind.class);
    var unrecognizedKinds = new TreeSet<String>();
    for (var classification : coverage.classifications()) {
      var feature = classification.feature();
      if (feature.category() != LanguageFeatureCategory.COMPILER_TYPE_KIND) {
        throw new IllegalArgumentException("Java type-kind coverage contains a non-type-kind feature: " + feature.identifier());
      }
      try {
        declaredKinds.add(TypeKind.valueOf(feature.identifier()));
      } catch (IllegalArgumentException exception) {
        unrecognizedKinds.add(feature.identifier());
      }
    }
    if (!unrecognizedKinds.isEmpty()) {
      throw new IllegalArgumentException("Unrecognized Java type kind: " + String.join(", ", unrecognizedKinds));
    }
    validateCompleteUniverse(declaredKinds);
    return coverage;
  }

  private static void validateCompleteUniverse(Set<TypeKind> declaredKinds) {
    var expectedKinds = EnumSet.allOf(TypeKind.class);
    var missingKinds = EnumSet.copyOf(expectedKinds);
    missingKinds.removeAll(declaredKinds);
    var extraKinds = EnumSet.copyOf(declaredKinds);
    extraKinds.removeAll(expectedKinds);
    if (!missingKinds.isEmpty() || !extraKinds.isEmpty()) {
      throw new IllegalArgumentException("Java type-kind coverage mismatch: missing=" + missingKinds + ", extra=" + extraKinds);
    }
  }

  private static List<LanguageFeatureClassification> classifications() {
    var classifications = new ArrayList<LanguageFeatureClassification>();
    for (var kind : TypeKind.values()) {
      classifications.add(switch (kind) {
        case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID, NULL, ARRAY, DECLARED, TYPEVAR, WILDCARD, EXECUTABLE, UNION,
            INTERSECTION ->
          represented(kind.name());
        case ERROR, NONE, PACKAGE, MODULE, OTHER -> diagnosed(kind.name());
      });
    }
    return List.copyOf(classifications);
  }

  private static LanguageFeatureClassification represented(String identifier) {
    return LanguageFeatureClassification.represented(feature(identifier), OBSERVATION_BOUNDARY);
  }

  private static LanguageFeatureClassification diagnosed(String identifier) {
    var suffix = identifier.toLowerCase(Locale.ROOT).replace('_', '-');
    var code = "ERROR".equals(identifier) ? "frontend.erroneous-type-kind.error" : DIAGNOSTIC_PREFIX + suffix;
    return LanguageFeatureClassification.diagnosed(feature(identifier), code);
  }

  private static LanguageFeature feature(String identifier) {
    return new LanguageFeature(LanguageFeatureCategory.COMPILER_TYPE_KIND, identifier);
  }
}
