package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class LanguageFeatureCoverageTest {
  private static final LanguageFeature PLUS_KIND =
      new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "PLUS");
  private static final LanguageFeature IMPORT_KIND =
      new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "IMPORT");
  private static final LanguageFeature MODULE_KIND =
      new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "MODULE");
  private static final LanguageFeature TYPE_KIND_PROPERTY =
      new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, "ClassTree.getKind");

  @Test
  void givenPolicyVocabulary_whenPoliciesAreEnumerated_thenExactlyThreeRequiredPoliciesExist() {
    // Arrange
    var expectedPolicies = List.of("REPRESENTED", "IRRELEVANT", "DIAGNOSED");

    // Act
    var actualPolicies = Arrays.stream(LanguageFeaturePolicy.values()).map(Enum::name).toList();

    // Assert
    assertEquals(expectedPolicies, actualPolicies);
    assertFalse(LanguageFeaturePolicy.REPRESENTED.blocksCompletion());
    assertFalse(LanguageFeaturePolicy.IRRELEVANT.blocksCompletion());
    assertTrue(LanguageFeaturePolicy.DIAGNOSED.blocksCompletion());
  }

  @Test
  void givenValidPolicyMetadata_whenClassificationsAreCreated_thenEachPolicyRetainsItsEvidence() {
    // Arrange
    var representationBoundary = "JavaProgram declaration aggregate";
    var rationale = "Import syntax is not retained by the current semantic aggregate";
    var sourceLocator = "JLS 25 section 7.5";
    var diagnosticCode = "frontend.unsupported-module-declaration";

    // Act
    var represented = LanguageFeatureClassification.represented(PLUS_KIND, representationBoundary);
    var irrelevant = LanguageFeatureClassification.irrelevant(IMPORT_KIND, rationale, sourceLocator);
    var diagnosed = LanguageFeatureClassification.diagnosed(MODULE_KIND, diagnosticCode);

    // Assert
    assertEquals(LanguageFeaturePolicy.REPRESENTED, represented.policy());
    assertEquals(Optional.of(representationBoundary), represented.representationBoundary());
    assertEquals(LanguageFeaturePolicy.IRRELEVANT, irrelevant.policy());
    assertEquals(Optional.of(rationale), irrelevant.irrelevanceRationale());
    assertEquals(Optional.of(sourceLocator), irrelevant.sourceLocator());
    assertEquals(LanguageFeaturePolicy.DIAGNOSED, diagnosed.policy());
    assertEquals(Optional.of(diagnosticCode), diagnosed.diagnosticCode());
    assertTrue(diagnosed.blocksCompletion());
  }

  @Test
  void givenRepresentedPolicyWithMissingOrConflictingMetadata_whenClassificationIsCreated_thenConstructionIsRejected() {
    // Arrange
    var invalidClassifications =
        List.<Executable>of(
            () -> new LanguageFeatureClassification(PLUS_KIND, LanguageFeaturePolicy.REPRESENTED, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()),
            () -> new LanguageFeatureClassification(PLUS_KIND, LanguageFeaturePolicy.REPRESENTED, Optional.of("aggregate"),
                Optional.of("rationale"), Optional.empty(), Optional.empty()),
            () -> new LanguageFeatureClassification(PLUS_KIND, LanguageFeaturePolicy.REPRESENTED, Optional.of("aggregate"),
                Optional.empty(), Optional.of("JLS 19"), Optional.empty()),
            () -> new LanguageFeatureClassification(PLUS_KIND, LanguageFeaturePolicy.REPRESENTED, Optional.of("aggregate"),
                Optional.empty(), Optional.empty(), Optional.of("frontend.unsupported-plus")));

    // Act
    var failures = invalidClassifications.stream()
        .map(classification -> assertThrows(IllegalArgumentException.class, classification)).toList();

    // Assert
    assertEquals(invalidClassifications.size(), failures.size());
  }

  @Test
  void givenIrrelevantPolicyWithMissingOrConflictingMetadata_whenClassificationIsCreated_thenConstructionIsRejected() {
    // Arrange
    var invalidClassifications =
        List.<Executable>of(
            () -> new LanguageFeatureClassification(IMPORT_KIND, LanguageFeaturePolicy.IRRELEVANT, Optional.empty(),
                Optional.empty(), Optional.of("JLS 25 section 7.5"), Optional.empty()),
            () -> new LanguageFeatureClassification(IMPORT_KIND, LanguageFeaturePolicy.IRRELEVANT, Optional.empty(),
                Optional.of("semantic scope rationale"), Optional.empty(), Optional.empty()),
            () -> LanguageFeatureClassification.irrelevant(IMPORT_KIND, " ", "JLS 25 section 7.5"),
            () -> LanguageFeatureClassification.irrelevant(IMPORT_KIND, "semantic scope rationale", " "),
            () -> new LanguageFeatureClassification(IMPORT_KIND, LanguageFeaturePolicy.IRRELEVANT, Optional.empty(),
                Optional.of("semantic scope rationale"), Optional.of("JLS 25 section 7.5"),
                Optional.of("frontend.unsupported-import")),
            () -> new LanguageFeatureClassification(IMPORT_KIND, LanguageFeaturePolicy.IRRELEVANT, Optional.of("aggregate"),
                Optional.of("semantic scope rationale"), Optional.of("JLS 25 section 7.5"), Optional.empty()));

    // Act
    var failures = invalidClassifications.stream()
        .map(classification -> assertThrows(IllegalArgumentException.class, classification)).toList();

    // Assert
    assertEquals(invalidClassifications.size(), failures.size());
  }

  @Test
  void givenDiagnosedPolicyWithMissingOrConflictingMetadata_whenClassificationIsCreated_thenConstructionIsRejected() {
    // Arrange
    var invalidClassifications =
        List.<Executable>of(
            () -> new LanguageFeatureClassification(MODULE_KIND, LanguageFeaturePolicy.DIAGNOSED, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()),
            () -> LanguageFeatureClassification.diagnosed(MODULE_KIND, " "),
            () -> new LanguageFeatureClassification(MODULE_KIND, LanguageFeaturePolicy.DIAGNOSED, Optional.of("aggregate"),
                Optional.empty(), Optional.empty(), Optional.of("frontend.unsupported-module-declaration")),
            () -> new LanguageFeatureClassification(MODULE_KIND, LanguageFeaturePolicy.DIAGNOSED, Optional.empty(),
                Optional.of("rationale"), Optional.empty(), Optional.of("frontend.unsupported-module-declaration")),
            () -> new LanguageFeatureClassification(MODULE_KIND, LanguageFeaturePolicy.DIAGNOSED, Optional.empty(),
                Optional.empty(), Optional.of("JLS 25 section 7.7"),
                Optional.of("frontend.unsupported-module-declaration")));

    // Act
    var failures = invalidClassifications.stream()
        .map(classification -> assertThrows(IllegalArgumentException.class, classification)).toList();

    // Assert
    assertEquals(invalidClassifications.size(), failures.size());
  }

  @Test
  void givenMetadataWithSurroundingWhitespace_whenClassificationIsCreated_thenConstructionIsRejectedWithoutNormalization() {
    // Arrange
    var invalidClassifications = List.<Executable>of(
        () -> LanguageFeatureClassification.represented(PLUS_KIND, " SyntaxNode aggregate"),
        () -> LanguageFeatureClassification.irrelevant(IMPORT_KIND, "semantic scope rationale ", "JLS 25 section 7.5"),
        () -> LanguageFeatureClassification.irrelevant(IMPORT_KIND, "semantic scope rationale", " JLS 25 section 7.5"),
        () -> LanguageFeatureClassification.diagnosed(MODULE_KIND, "frontend.unsupported-module-declaration "));

    // Act
    var failures = invalidClassifications.stream()
        .map(classification -> assertThrows(IllegalArgumentException.class, classification)).toList();

    // Assert
    assertEquals(invalidClassifications.size(), failures.size());
  }

  @Test
  void givenDuplicateFeatures_whenCoverageIsCreated_thenConstructionIsRejectedBeforeNormalization() {
    // Arrange
    var firstClassification = LanguageFeatureClassification.represented(PLUS_KIND, "SyntaxNode aggregate");
    var duplicateClassification = LanguageFeatureClassification.diagnosed(PLUS_KIND, "frontend.unsupported-plus");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, List.of(firstClassification, duplicateClassification)));

    // Assert
    assertEquals("Duplicate language feature classification: COMPILER_TREE_KIND:PLUS", exception.getMessage());
  }

  @Test
  void givenMutableUnorderedClassifications_whenCoverageIsCreated_thenClassificationsAreImmutableAndDeterministic() {
    // Arrange
    var classifications = new ArrayList<LanguageFeatureClassification>();
    classifications.add(LanguageFeatureClassification.represented(TYPE_KIND_PROPERTY, "JavaType kind"));
    classifications.add(LanguageFeatureClassification.diagnosed(MODULE_KIND, "frontend.unsupported-module-declaration"));
    classifications.add(LanguageFeatureClassification.represented(PLUS_KIND, "SyntaxNode aggregate"));
    var reversedClassifications = classifications.reversed();

    // Act
    var firstCoverage = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, classifications);
    var secondCoverage = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, reversedClassifications);
    classifications.clear();

    // Assert
    assertEquals(firstCoverage, secondCoverage);
    assertEquals(firstCoverage.toString(), secondCoverage.toString());
    assertEquals(List.of(MODULE_KIND, PLUS_KIND, TYPE_KIND_PROPERTY),
        firstCoverage.classifications().stream().map(LanguageFeatureClassification::feature).toList());
    assertThrows(UnsupportedOperationException.class,
        () -> firstCoverage.classifications()
            .add(LanguageFeatureClassification.irrelevant(IMPORT_KIND, "rationale", "JLS 7.5")));
  }

  @Test
  void givenDeclaredAndMissingFeatures_whenCoverageIsQueried_thenOnlyTheExactFeatureMatches() {
    // Arrange
    var plusClassification = LanguageFeatureClassification.represented(PLUS_KIND, "SyntaxNode aggregate");
    var coverage = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, List.of(plusClassification));

    // Act
    var declared = coverage.classificationOf(PLUS_KIND);
    var missing = coverage.classificationOf(MODULE_KIND);

    // Assert
    assertEquals(Optional.of(plusClassification), declared);
    assertTrue(missing.isEmpty());
  }

  @Test
  void givenMalformedExactIdentifiers_whenLanguageFeatureIsCreated_thenConstructionIsRejectedWithoutNormalization() {
    // Arrange
    var malformedIdentifiers = List.of(" PLUS ", "plus");

    // Act
    var failures = malformedIdentifiers.stream()
        .map(identifier -> assertThrows(IllegalArgumentException.class,
            () -> new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, identifier)))
        .toList();

    // Assert
    assertEquals(malformedIdentifiers.size(), failures.size());
  }

  @Test
  void givenCatchAllIdentifiers_whenLanguageFeatureIsCreated_thenConstructionIsRejected() {
    // Arrange
    var catchAllIdentifiers = List.of("*", "DEFAULT", "UNKNOWN");

    // Act
    var failures = catchAllIdentifiers.stream()
        .map(identifier -> assertThrows(IllegalArgumentException.class,
            () -> new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, identifier)))
        .toList();

    // Assert
    assertEquals(catchAllIdentifiers.size(), failures.size());
  }
}
