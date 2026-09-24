package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureClassification;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaTypeKindCoverageTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final Set<TypeKind> NON_SUCCESS_KINDS = EnumSet.of(TypeKind.ERROR, TypeKind.NONE, TypeKind.PACKAGE,
      TypeKind.MODULE, TypeKind.OTHER);

  @TempDir Path temporaryDirectory;

  @Test
  void givenJava25Compiler_whenTypeKindCoverageIsCreated_thenEveryKindHasOneExactPolicy() {
    // Arrange
    var expected = EnumSet.allOf(TypeKind.class);

    // Act
    var coverage = JavaTypeKindCoverage.fromConfiguration(CONFIGURATION);
    var actual = kindsIn(coverage);
    var diagnosed = kindsWithPolicy(coverage, LanguageFeaturePolicy.DIAGNOSED);
    var represented = kindsWithPolicy(coverage, LanguageFeaturePolicy.REPRESENTED);

    // Assert
    assertEquals(CONFIGURATION.release(), coverage.release());
    assertEquals(CONFIGURATION.release().feature(), coverage.releaseNumber());
    assertEquals(expected, actual);
    assertEquals(NON_SUCCESS_KINDS, diagnosed);
    var expectedRepresented = EnumSet.copyOf(expected);
    expectedRepresented.removeAll(NON_SUCCESS_KINDS);
    assertEquals(expectedRepresented, represented);
    assertEquals(0, kindsWithPolicy(coverage, LanguageFeaturePolicy.IRRELEVANT).size());
    assertEquals(expected.size(), coverage.classifications().size());
  }

  @Test
  void givenNonSuccessKinds_whenCoverageIsInspected_thenEachHasItsOwnBlockingOutcome() {
    // Arrange
    var coverage = JavaTypeKindCoverage.fromConfiguration(CONFIGURATION);

    // Act
    var codes = NON_SUCCESS_KINDS.stream().map(kind -> classificationOf(coverage, kind).diagnosticCode().orElseThrow()).toList();

    // Assert
    assertEquals(NON_SUCCESS_KINDS.size(), Set.copyOf(codes).size());
    assertEquals("frontend.erroneous-type-kind.error", classificationOf(coverage, TypeKind.ERROR).diagnosticCode().orElseThrow());
    assertEquals("frontend.unsupported-type-kind.none", classificationOf(coverage, TypeKind.NONE).diagnosticCode().orElseThrow());
    assertEquals("frontend.unsupported-type-kind.package", classificationOf(coverage, TypeKind.PACKAGE).diagnosticCode().orElseThrow());
    assertEquals("frontend.unsupported-type-kind.module", classificationOf(coverage, TypeKind.MODULE).diagnosticCode().orElseThrow());
    assertEquals("frontend.unsupported-type-kind.other", classificationOf(coverage, TypeKind.OTHER).diagnosticCode().orElseThrow());
    assertTrue(coverage.classifications().stream().filter(classification -> classification.policy() == LanguageFeaturePolicy.REPRESENTED)
        .allMatch(classification -> classification.representationBoundary().isPresent()));
  }

  @Test
  void givenCompilerKindsAndMissingAttribution_whenOutcomesAreClassified_thenStatesRemainDistinct() {
    // Arrange
    var concreteKinds = EnumSet.allOf(TypeKind.class);
    concreteKinds.removeAll(NON_SUCCESS_KINDS);

    // Act
    var concreteOutcomes = concreteKinds.stream().map(JavaTypeAttribution::fromKind).toList();
    var noTypeOutcomes = List.of(TypeKind.NONE, TypeKind.PACKAGE, TypeKind.MODULE).stream()
        .map(JavaTypeAttribution::fromKind).toList();
    var error = JavaTypeAttribution.fromKind(TypeKind.ERROR);
    var unsupported = JavaTypeAttribution.fromKind(TypeKind.OTHER);
    var unresolved = JavaTypeAttribution.fromKind(null);

    // Assert
    assertEquals(concreteKinds.stream().map(JavaTypeAttribution.Resolved::new).toList(), concreteOutcomes);
    assertEquals(List.of(new JavaTypeAttribution.NoType(TypeKind.NONE), new JavaTypeAttribution.NoType(TypeKind.PACKAGE),
        new JavaTypeAttribution.NoType(TypeKind.MODULE)), noTypeOutcomes);
    assertEquals(JavaTypeAttribution.Erroneous.INSTANCE, error);
    assertEquals(JavaTypeAttribution.Unsupported.INSTANCE, unsupported);
    assertEquals(JavaTypeAttribution.Unresolved.INSTANCE, unresolved);
  }

  @Test
  void givenNonSuccessKinds_whenResolvedStateIsConstructed_thenInvalidStateIsRejected() {
    // Arrange
    var invalid = List.of(TypeKind.ERROR, TypeKind.NONE, TypeKind.PACKAGE, TypeKind.MODULE, TypeKind.OTHER);

    // Act
    var failures = invalid.stream().map(kind -> assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeAttribution.Resolved(kind))).toList();

    // Assert
    assertEquals(invalid.size(), failures.size());
  }

  @Test
  void givenMissingKind_whenCatalogIsConstructed_thenExactDifferenceIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.removeIf(classification -> classification.feature().identifier().equals("INT"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> JavaTypeKindCoverage.fromClassifications(CONFIGURATION, classifications));

    // Assert
    assertEquals("Java type-kind coverage mismatch: missing=[INT], extra=[]", failure.getMessage());
  }

  @Test
  void givenDuplicateKind_whenCatalogIsConstructed_thenDuplicateIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.add(classificationOf(JavaTypeKindCoverage.fromConfiguration(CONFIGURATION), TypeKind.INT));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> JavaTypeKindCoverage.fromClassifications(CONFIGURATION, classifications));

    // Assert
    assertEquals("Duplicate language feature classification: COMPILER_TYPE_KIND:INT", failure.getMessage());
  }

  @Test
  void givenUnknownKind_whenCatalogIsConstructed_thenUnknownIdentifierIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.removeIf(classification -> classification.feature().identifier().equals("INT"));
    classifications.add(LanguageFeatureClassification.diagnosed(
        new LanguageFeature(LanguageFeatureCategory.COMPILER_TYPE_KIND, "FUTURE_TYPE"), "frontend.unsupported-type-kind.future-type"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> JavaTypeKindCoverage.fromClassifications(CONFIGURATION, classifications));

    // Assert
    assertEquals("Unrecognized Java type kind: FUTURE_TYPE", failure.getMessage());
  }

  @Test
  void givenCatchAllIdentifier_whenTypeKindFeatureIsCreated_thenIdentifierIsRejected() {
    // Arrange
    var invalid = List.of("*", "DEFAULT", "UNKNOWN");

    // Act
    var failures = invalid.stream().map(identifier -> assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeature(LanguageFeatureCategory.COMPILER_TYPE_KIND, identifier))).toList();

    // Assert
    assertEquals(invalid.size(), failures.size());
  }

  @Test
  void givenPolicylessKind_whenClassificationIsCreated_thenMissingPolicyIsRejected() {
    // Arrange
    var feature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TYPE_KIND, "INT");

    // Act
    var failure = assertThrows(NullPointerException.class,
        () -> new LanguageFeatureClassification(feature, null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("policy", failure.getMessage());
  }

  @Test
  void givenPrimitiveAndDeclaredHeaders_whenFrontendAnalyzesThem_thenExactAttributedKindsHaveSourceOrigins() throws IOException {
    // Arrange
    var source = "abstract class TypeEvidence { int field; String text; abstract int map(int value); }";
    Files.writeString(temporaryDirectory.resolve("TypeEvidence.java"), source);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CONFIGURATION);
    var typeObservations = result.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> observation.classification().feature().category() == LanguageFeatureCategory.COMPILER_TYPE_KIND).toList();
    var kinds = typeObservations.stream().map(observation -> observation.classification().feature().identifier()).toList();
    var excerpts = typeObservations.stream().map(observation -> {
      var span = observation.origins().getFirst();
      return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
    }).toList();

    // Assert
    assertEquals(List.of("INT", "DECLARED", "INT", "INT"), kinds);
    assertEquals(List.of("int", "String", "int", "int"), excerpts);
    assertTrue(typeObservations.stream().allMatch(observation -> observation.classification().policy() == LanguageFeaturePolicy.REPRESENTED
        && observation.diagnostic().isEmpty() && observation.origins().size() == 1));
    assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-tree-kind.primitive-type")));
  }

  @Test
  void givenUnresolvedHeaderType_whenFrontendAnalyzesIt_thenErrorKindBlocksCompletion() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("MissingType.java"), "class MissingType { Absent field; }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CONFIGURATION);
    var errorObservation = result.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> observation.classification().feature().category() == LanguageFeatureCategory.COMPILER_TYPE_KIND
            && observation.classification().feature().identifier().equals("ERROR"))
        .findFirst().orElseThrow();

    // Assert
    assertEquals(LanguageFeaturePolicy.DIAGNOSED, errorObservation.classification().policy());
    assertEquals("frontend.erroneous-type-kind.error", errorObservation.diagnostic().orElseThrow().code());
    assertEquals(1, errorObservation.origins().size());
    assertTrue(result.diagnostics().contains(errorObservation.diagnostic().orElseThrow()));
  }

  @Test
  void givenInitializerLocalAndField_whenFrontendObservesHeaderTypes_thenOnlyFieldTypeIsRecorded() throws IOException {
    // Arrange
    var source = "class HeaderOnly { static { int local = 1; } int field; }";
    Files.writeString(temporaryDirectory.resolve("HeaderOnly.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, CONFIGURATION);
    var observations = result.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> observation.classification().feature().category() == LanguageFeatureCategory.COMPILER_TYPE_KIND)
        .toList();

    // Assert
    assertEquals(List.of("INT"), observations.stream().map(observation -> observation.classification().feature().identifier()).toList());
    assertEquals(List.of("int"), observations.stream().map(observation -> {
      var span = observation.origins().getFirst();
      return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
    }).toList());
  }

  @Test
  void givenMissingTypePositions_whenErrorKindsAreObserved_thenDiagnosticsUseAvailableOwnersAndDistinctOccurrences()
      throws IOException {
    // Arrange
    var source = "class MissingKinds { Absent first; Absent second; }";
    Files.writeString(temporaryDirectory.resolve("MissingKinds.java"), source);
    var snapshot = new JavaSourceLoader().discover(temporaryDirectory);
    var sources = new SnapshotSourceCatalog(snapshot);
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION);
    try (var session = CompilerSession.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed);
      var unit = compilation.units().getFirst();
      var declaration = (ClassTree) unit.getTypeDecls().getFirst();
      var fields = declaration.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast).toList();
      var missingTypes = fields.stream().map(VariableTree::getType).toList();
      var positions = compilation.sourceEvidence();
      var sourceFile = sources.find(unit).orElseThrow();
      var ownerSpans = fields.stream().map(field -> positions.origin(unit, sourceFile, field).orElseThrow()).toList();

      // Act
      var located = new JavaTypeKindObserver(sources, compilation.semantics(),
          new JavaSourceEvidence(withMissingPositions(parsed.sourceEvidence(), sources, missingTypes)),
          compilation.treeInventory(), coverage).observe(compilation.units());
      var missingOwners = new ArrayList<Tree>(missingTypes);
      missingOwners.addAll(fields);
      var ancestorLocated = new JavaTypeKindObserver(sources, compilation.semantics(),
          new JavaSourceEvidence(withMissingPositions(parsed.sourceEvidence(), sources, missingOwners)),
          compilation.treeInventory(), coverage).observe(compilation.units());
      missingOwners.add(declaration);
      missingOwners.add(unit);
      var unlocated = new JavaTypeKindObserver(sources, compilation.semantics(),
          new JavaSourceEvidence(withMissingPositions(parsed.sourceEvidence(), sources, missingOwners)),
          compilation.treeInventory(), coverage).observe(compilation.units());

      // Assert
      assertEquals(2, fields.size());
      assertEquals(List.of("ERROR", "ERROR"), located.observations().stream()
          .map(observation -> observation.classification().feature().identifier()).toList());
      assertEquals(ownerSpans, located.observations().stream().map(observation -> observation.origins().getFirst()).toList());
      assertEquals(ownerSpans, located.diagnostics().stream().map(diagnostic -> diagnostic.origins().getFirst()).toList());
      assertNotEquals(located.diagnostics().get(0).diagnosticKey(), located.diagnostics().get(1).diagnosticKey());
      var classSpan = positions.origin(unit, sourceFile, declaration).orElseThrow();
      assertEquals(List.of(classSpan, classSpan), ancestorLocated.observations().stream()
          .map(observation -> observation.origins().getFirst()).toList());
      assertEquals(List.of(classSpan, classSpan), ancestorLocated.diagnostics().stream()
          .map(diagnostic -> diagnostic.origins().getFirst()).toList());
      assertNotEquals(ancestorLocated.diagnostics().get(0).diagnosticKey(), ancestorLocated.diagnostics().get(1).diagnosticKey());
      assertTrue(unlocated.observations().stream().allMatch(observation -> observation.origins().isEmpty()));
      assertTrue(unlocated.diagnostics().stream().allMatch(diagnostic -> diagnostic.origins().isEmpty()));
      assertNotEquals(unlocated.diagnostics().get(0).diagnosticKey(), unlocated.diagnostics().get(1).diagnosticKey());
    }
  }

  @Test
  void givenEarlierUnrelatedSource_whenDiagnosedHeaderTypesAreObserved_thenExistingDiagnosticKeysRemainStable()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("B.java"), "class B { Absent first; Absent second; }");
    var baseline = diagnosedKeysWithSharedClassSpan();
    Files.writeString(temporaryDirectory.resolve("A.java"), "class A { int extra; }");

    // Act
    var withEarlierSource = diagnosedKeysWithSharedClassSpan();

    // Assert
    assertEquals(2, baseline.size());
    assertNotEquals(baseline.get(0), baseline.get(1));
    assertEquals(baseline, withEarlierSource);
  }

  private List<String> diagnosedKeysWithSharedClassSpan() throws IOException {
    var snapshot = new JavaSourceLoader().discover(temporaryDirectory);
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSession.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed);
      var bUnit = compilation.units().stream()
          .filter(unit -> sources.find(unit).orElseThrow().relativePath().equals(Path.of("B.java"))).findFirst().orElseThrow();
      var declaration = (ClassTree) bUnit.getTypeDecls().getFirst();
      var fields = declaration.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast).toList();
      var missing = new ArrayList<Tree>(fields.stream().map(VariableTree::getType).toList());
      missing.addAll(fields);
      var observed = new JavaTypeKindObserver(sources, compilation.semantics(),
          new JavaSourceEvidence(withMissingPositions(parsed.sourceEvidence(), sources, missing)),
          compilation.treeInventory(), JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION)).observe(compilation.units());
      return observed.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("frontend.erroneous-type-kind.error"))
          .map(diagnostic -> diagnostic.diagnosticKey()).toList();
    }
  }

  private static SourcePositions withMissingPositions(JavaSourceEvidence evidence, SnapshotSourceCatalog sources,
      List<? extends Tree> missingTrees) {
    var missing = Collections.newSetFromMap(new IdentityHashMap<Tree, Boolean>());
    missing.addAll(missingTrees);
    return new SourcePositions() {
      @Override
      public long getStartPosition(CompilationUnitTree currentUnit, Tree tree) {
        if (missing.contains(tree)) {
          return Diagnostic.NOPOS;
        }
        return evidence.origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
            .map(span -> span.startOffset()).orElse(Diagnostic.NOPOS);
      }

      @Override
      public long getEndPosition(CompilationUnitTree currentUnit, Tree tree) {
        if (missing.contains(tree)) {
          return Diagnostic.NOPOS;
        }
        return evidence.origin(currentUnit, sources.find(currentUnit).orElseThrow(), tree)
            .map(span -> span.endOffsetExclusive()).orElse(Diagnostic.NOPOS);
      }
    };
  }

  private static ArrayList<LanguageFeatureClassification> mutableClassifications() {
    return new ArrayList<>(JavaTypeKindCoverage.fromConfiguration(CONFIGURATION).classifications());
  }

  private static LanguageFeatureClassification classificationOf(LanguageFeatureCoverage coverage, TypeKind kind) {
    return coverage.classificationOf(new LanguageFeature(LanguageFeatureCategory.COMPILER_TYPE_KIND, kind.name())).orElseThrow();
  }

  private static Set<TypeKind> kindsIn(LanguageFeatureCoverage coverage) {
    return coverage.classifications().stream().map(classification -> TypeKind.valueOf(classification.feature().identifier()))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static EnumSet<TypeKind> kindsWithPolicy(LanguageFeatureCoverage coverage, LanguageFeaturePolicy policy) {
    var kinds = EnumSet.noneOf(TypeKind.class);
    coverage.classifications().stream().filter(classification -> classification.policy() == policy)
        .map(classification -> TypeKind.valueOf(classification.feature().identifier())).forEach(kinds::add);
    return kinds;
  }
}
