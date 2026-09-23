package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureClassification;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureCoverageResult;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaLanguageCompletionContractTest {
  private static final CompilerConfiguration JAVA_25_CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String WHILE_DIAGNOSTIC = "frontend.unsupported-tree-kind.while-loop";

  @TempDir Path temporaryDirectory;

  @Test
  void givenJava25Configuration_whenFrontendRuns_thenOneCombinedReleaseBoundCoverageContractIsReturned() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { int value() { return 1; } }");
    var expectedClassifications = new ArrayList<LanguageFeatureClassification>();
    expectedClassifications.addAll(JavaCompilerKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION).classifications());
    expectedClassifications.addAll(JavaDeclarationPropertyCoverage.fromConfiguration(JAVA_25_CONFIGURATION).classifications());

    // Act
    var input = analyze();

    // Assert
    assertEquals(JavaRelease.JAVA_25, input.languageFeatureCoverage().release());
    assertEquals(25, input.languageFeatureCoverage().releaseNumber());
    assertEquals(expectedClassifications.stream().sorted((left, right) -> {
      var category = left.feature().category().compareTo(right.feature().category());
      return category != 0 ? category : left.feature().identifier().compareTo(right.feature().identifier());
    }).toList(), input.languageFeatureCoverage().classifications());
  }

  @Test
  void givenModifiedCoverageContract_whenCoverageResultIsBuilt_thenExactAuthoritativeContractIsRequired() {
    // Arrange
    var authoritativeCoverage = JavaLanguageFeatureCoverage.fromConfiguration(JAVA_25_CONFIGURATION);
    var compilationUnit = new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "COMPILATION_UNIT");
    var reducedCoverage = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25,
        authoritativeCoverage.classifications().subList(1, authoritativeCoverage.classifications().size()));
    var augmentedClassifications = new ArrayList<>(authoritativeCoverage.classifications());
    augmentedClassifications.add(LanguageFeatureClassification.irrelevant(
        new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, "SyntheticTree::extra()"),
        "Synthetic extra used only to verify closed contract membership", "https://docs.oracle.com/en/java/javase/25/docs/api/"));
    var augmentedCoverage = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, augmentedClassifications);
    var reclassifiedClassifications = authoritativeCoverage.classifications().stream()
        .map(classification -> classification.feature().equals(compilationUnit)
            ? LanguageFeatureClassification.diagnosed(compilationUnit, "frontend.reclassified-compilation-unit")
            : classification)
        .toList();
    var reclassifiedCoverage = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, reclassifiedClassifications);

    // Act
    var reducedException = assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeatureCoverageResult(reducedCoverage, List.of(), List.of(), List.of()));
    var augmentedException = assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeatureCoverageResult(augmentedCoverage, List.of(), List.of(), List.of()));
    var reclassifiedException = assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeatureCoverageResult(reclassifiedCoverage, List.of(), List.of(), List.of()));

    // Assert
    var expectedMessage = "Language-feature coverage must match the authoritative release contract";
    assertEquals(expectedMessage, reducedException.getMessage());
    assertEquals(expectedMessage, augmentedException.getMessage());
    assertEquals(expectedMessage, reclassifiedException.getMessage());
  }

  @Test
  void givenImplicitDefaultConstructor_whenFrontendRuns_thenCoverageContainsOnlyParsedSourceTrees() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");

    // Act
    var input = analyze();

    // Assert
    assertFalse(input.languageFeatureCoverageResult().observations().stream()
        .anyMatch(observation -> observation.classification().feature().identifier().equals("METHOD")));
  }

  @Test
  void givenDiagnosedCompilerKind_whenFrontendRuns_thenExactCoverageDiagnosticPreventsCompletion() throws IOException {
    // Arrange
    writeSource("Looping.java", "final class Looping { void run() { while (false) { } } }");

    // Act
    var input = analyze();
    var run = new AnalysisEngine().analyze(input);

    // Assert
    var diagnostic = diagnostic(input, WHILE_DIAGNOSTIC);
    assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
    assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
    assertEquals(AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE, diagnostic.outcomeKind());
    assertEquals(1, diagnostic.origins().size());
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
  }

  @Test
  void givenUnencounteredDiagnosedKind_whenFrontendRuns_thenThatKindDoesNotEmitADiagnostic() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { int value() { return 1; } }");

    // Act
    var input = analyze();

    // Assert
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        classification(input, LanguageFeatureCategory.COMPILER_TREE_KIND, "COMPILATION_UNIT").policy());
    assertEquals(1, input.aggregate().orElseThrow().roots().size());
    assertFalse(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(WHILE_DIAGNOSTIC)));
  }

  @Test
  void givenMalformedSource_whenAnalysisRuns_thenCompilerErrorIdentityIsRetained() throws IOException {
    // Arrange
    writeSource("Broken.java", "class Broken { void run( } }");

    // Act
    var input = analyze();
    var run = new AnalysisEngine().analyze(input);

    // Assert
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
    var compilerErrors = run.diagnostics().stream()
        .filter(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR).toList();
    assertEquals(List.of("compiler.err.illegal.start.of.type", "compiler.err.class.method.or.field.expected",
        "compiler.err.missing.meth.body.or.decl.abstract"), compilerErrors.stream().map(AnalysisDiagnostic::code).toList());
    assertFalse(compilerErrors.isEmpty());
    assertTrue(compilerErrors.stream().allMatch(diagnostic -> diagnostic.stage() == DiagnosticStage.FRONTEND
        && diagnostic.severity() == DiagnosticSeverity.ERROR && !diagnostic.origins().isEmpty()));
  }

  @Test
  void givenCompilerErrorsWithNullOrVendorCode_whenTranslated_thenCompilerKindOwnsTheTypedOutcome() throws IOException {
    // Arrange
    var translator = new CompilerDiagnosticTranslator(new SnapshotSourceCatalog(new JavaSourceLoader().discover(temporaryDirectory)));

    // Act
    var diagnostics = translator.translate(List.of(new CompilerError(null), new CompilerError("vendor.parse.failure")));

    // Assert
    assertEquals(List.of("javac.unknown", "vendor.parse.failure"), diagnostics.stream().map(AnalysisDiagnostic::code).toList());
    assertTrue(diagnostics.stream().allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR));
  }

  @Test
  void givenErroneousCompilerTree_whenAnalysisRuns_thenErroneousOutcomeRetainsItsExactIdentity() throws IOException {
    // Arrange
    writeSource("Broken.java", "class Broken { void run( } }");

    // Act
    var run = new AnalysisEngine().analyze(analyze());

    // Assert
    var diagnostic = run.diagnostics().stream()
        .filter(candidate -> candidate.code().equals("frontend.unsupported-tree-kind.erroneous")).findFirst().orElseThrow();
    assertEquals(AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE, diagnostic.outcomeKind());
    assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
    assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
    assertFalse(diagnostic.origins().isEmpty());
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
  }

  @Test
  void givenIrrelevantCompilerKind_whenCoverageIsInspected_thenRationaleIsVisibleWithoutAnErrorPolicy() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");

    // Act
    var classification = classification(analyze(), LanguageFeatureCategory.COMPILER_TREE_KIND, "OTHER");

    // Assert
    assertEquals(LanguageFeaturePolicy.IRRELEVANT, classification.policy());
    assertTrue(classification.irrelevanceRationale().isPresent());
    assertTrue(classification.sourceLocator().isPresent());
    assertTrue(classification.diagnosticCode().isEmpty());
    assertFalse(classification.blocksCompletion());
    var observation = new LanguageFeatureObservation("catalog-inspection", classification, List.of(), Optional.empty());
    assertEquals(AnalysisOutcomeKind.IRRELEVANT_LANGUAGE_FEATURE, observation.outcomeKind());
  }

  @Test
  void givenDeclarationPropertyCapability_whenAnalysisRuns_thenExactDiagnosticParticipatesInTheCombinedContract()
      throws IOException {
    // Arrange
    writeSource("GenericMethod.java", "final class GenericMethod { <T> T identity(T value) { return value; } }");

    // Act
    var input = analyze();
    var run = new AnalysisEngine().analyze(input);
    var classification = classification(input, LanguageFeatureCategory.DECLARATION_PROPERTY,
        "MethodTree::MethodTree#getTypeParameters()");

    // Assert
    assertEquals(LanguageFeaturePolicy.DIAGNOSED, classification.policy());
    assertEquals(JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC,
        classification.diagnosticCode().orElseThrow());
    assertTrue(input.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC)));
    assertTrue(input.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC))
        .allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE));
    assertNotEquals(AnalysisCompletion.COMPLETED, run.completion());
  }

  @Test
  void givenDiagnosedDeclarationProperty_whenFrontendRuns_thenContextualOccurrencesOwnTheExactDiagnostic()
      throws IOException {
    // Arrange
    writeSource("GenericMethod.java", "final class GenericMethod { <T> T identity(T value) { return value; } }");

    // Act
    var input = analyze();
    var diagnostic = diagnostic(input, JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC);
    var propertyObservations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> observation.diagnostic().filter(diagnostic::equals).isPresent()).toList();

    // Assert
    assertEquals(List.of("ExecutableElement::ExecutableElement#getTypeParameters()",
        "MethodTree::MethodTree#getTypeParameters()"), propertyObservations.stream()
            .map(observation -> observation.classification().feature().identifier()).sorted().toList());
    assertTrue(propertyObservations.stream()
        .allMatch(observation -> observation.sourceFileKey().equals(diagnostic.origins().getFirst().sourceFileKey())));
  }

  @Test
  void givenContextualPropertyOccurrence_whenItsDiagnosticIsRemoved_thenCoverageResultRejectsTheDetachment()
      throws IOException {
    // Arrange
    writeSource("GenericMethod.java", "final class GenericMethod { <T> T identity(T value) { return value; } }");
    var input = analyze();
    var diagnostic = diagnostic(input, JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC);
    var capabilityDiagnostics = input.languageFeatureCoverageResult().capabilityDiagnostics().stream()
        .filter(candidate -> !candidate.equals(diagnostic)).toList();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeatureCoverageResult(input.languageFeatureCoverage(),
            input.languageFeatureCoverageResult().occurrences(), input.languageFeatureCoverageResult().observations(),
            capabilityDiagnostics));

    // Assert
    assertEquals("Observed capability diagnostic is absent from the coverage result", exception.getMessage());
  }

  @Test
  void givenSharedPropertyDiagnostic_whenOneContextualOccurrenceIsRemoved_thenCoverageResultRejectsThePartialInventory()
      throws IOException {
    // Arrange
    writeSource("GenericMethod.java", "final class GenericMethod { <T> T identity(T value) { return value; } }");
    var input = analyze();
    var diagnostic = diagnostic(input, JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC);
    var observations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> !observation.classification().feature().identifier()
            .equals("MethodTree::MethodTree#getTypeParameters()"))
        .toList();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeatureCoverageResult(input.languageFeatureCoverage(), input.languageFeatureCoverageResult().occurrences(),
            observations,
            input.languageFeatureCoverageResult().capabilityDiagnostics()));

    // Assert
    assertEquals("Language-feature observations must exactly match the source occurrence manifest", exception.getMessage());
    assertTrue(input.languageFeatureCoverageResult().observations().stream()
        .anyMatch(observation -> observation.diagnostic().filter(diagnostic::equals).isPresent()));
  }

  @Test
  void givenContextualPropertyOccurrence_whenItsWholeOutcomeBundleIsRemoved_thenCoverageResultRejectsTheMissingFact()
      throws IOException {
    // Arrange
    writeSource("GenericMethod.java", "final class GenericMethod { <T> T identity(T value) { return value; } }");
    var input = analyze();
    var diagnostic = diagnostic(input, JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC);
    var observations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> observation.diagnostic().filter(diagnostic::equals).isEmpty()).toList();
    var capabilityDiagnostics = input.languageFeatureCoverageResult().capabilityDiagnostics().stream()
        .filter(candidate -> !candidate.equals(diagnostic)).toList();
    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeatureCoverageResult(input.languageFeatureCoverage(), input.languageFeatureCoverageResult().occurrences(),
            observations, capabilityDiagnostics));

    // Assert
    assertEquals("Language-feature observations must exactly match the source occurrence manifest", exception.getMessage());
  }

  @Test
  void givenUnsupportedMethodSyntax_whenAnalysisRuns_thenFrontendAndControlFlowOutcomesRemainDistinct() throws IOException {
    // Arrange
    writeSource("Looping.java", "final class Looping { void run() { while (false) { } } }");

    // Act
    var run = new AnalysisEngine().analyze(analyze());

    // Assert
    assertTrue(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(WHILE_DIAGNOSTIC)
        && diagnostic.stage() == DiagnosticStage.FRONTEND));
    var controlFlowDiagnostic = run.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("control-flow.unsupported-method")).findFirst().orElseThrow();
    assertEquals(DiagnosticStage.CONTROL_FLOW, controlFlowDiagnostic.stage());
    assertEquals(DiagnosticSeverity.ERROR, controlFlowDiagnostic.severity());
    assertEquals(AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE, controlFlowDiagnostic.outcomeKind());
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
  }

  @Test
  void givenSameSnapshot_whenFrontendRunsTwice_thenCoverageDiagnosticsAndCompletionAreDeterministic() throws IOException {
    // Arrange
    writeSource("Looping.java", "final class Looping { void run() { while (false) { } } }");
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    var frontend = new JavaFrontend(loader);

    // Act
    var first = frontend.analyze(snapshot, JAVA_25_CONFIGURATION);
    var second = frontend.analyze(snapshot, JAVA_25_CONFIGURATION);
    var firstRun = new AnalysisEngine().analyze(first);
    var secondRun = new AnalysisEngine().analyze(second);

    // Assert
    assertEquals(first.languageFeatureCoverage(), second.languageFeatureCoverage());
    assertEquals(first.languageFeatureCoverageResult(), second.languageFeatureCoverageResult());
    assertEquals(diagnosticTuples(first.diagnostics()), diagnosticTuples(second.diagnostics()));
    assertEquals(AnalysisCompletion.INCOMPLETE, firstRun.completion());
    assertEquals(firstRun.completion(), secondRun.completion());
  }

  @Test
  void givenPropertyDiagnosticWithErroneousOutcome_whenCoverageResultIsBuilt_thenOutcomeMismatchIsRejected() throws IOException {
    // Arrange
    writeSource("GenericMethod.java", "final class GenericMethod { <T> T identity(T value) { return value; } }");
    var input = analyze();
    var original = diagnostic(input, JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC);
    var mismatched = new AnalysisDiagnostic(StableId.sha256("diagnostic", original.diagnosticKey(), "mismatched-outcome"),
        original.code(), original.stage(), original.severity(), original.message(), original.origins(),
        AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE);
    var diagnostics = new ArrayList<>(input.languageFeatureCoverageResult().capabilityDiagnostics());
    diagnostics.add(mismatched);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeatureCoverageResult(input.languageFeatureCoverage(),
            input.languageFeatureCoverageResult().occurrences(), input.languageFeatureCoverageResult().observations(), diagnostics));

    // Assert
    assertEquals("Language feature capability diagnostic has an inconsistent outcome kind", exception.getMessage());
  }

  @Test
  void givenDiagnosedCoverageOutcome_whenItsDiagnosticIsRemoved_thenSyntaxBoundaryRejectsTheInput() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { void run() { while (false) { } } }");
    var input = analyze();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(), List.of(),
            input.languageFeatureCoverageResult()));

    // Assert
    assertEquals("Coverage capability diagnostics must be retained in frontend diagnostics", exception.getMessage());
  }

  @Test
  void givenNonemptySnapshotWithoutCompilationUnitObservation_whenSyntaxBoundaryIsBuilt_thenInputIsRejected() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");
    var input = analyze();
    var observations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> !observation.classification().feature().identifier().equals("COMPILATION_UNIT")).toList();
    var occurrences = input.languageFeatureCoverageResult().occurrences().stream()
        .filter(occurrence -> !occurrence.classification().feature().identifier().equals("COMPILATION_UNIT")).toList();
    var incompleteResult = new LanguageFeatureCoverageResult(input.languageFeatureCoverage(), occurrences, observations,
        input.languageFeatureCoverageResult().capabilityDiagnostics());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
            input.diagnostics(), incompleteResult));

    // Assert
    assertEquals("Every source file requires exactly one COMPILATION_UNIT coverage observation", exception.getMessage());
  }

  @Test
  void givenForeignRepresentedObservation_whenSyntaxBoundaryIsBuilt_thenInputIsRejected() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");
    var input = analyze();
    var classification = classification(input, LanguageFeatureCategory.COMPILER_TREE_KIND, "CLASS");
    var occurrences = new ArrayList<>(input.languageFeatureCoverageResult().occurrences());
    occurrences.add(new LanguageFeatureOccurrence("foreign-source", classification, List.of()));
    var observations = new ArrayList<>(input.languageFeatureCoverageResult().observations());
    observations.add(new LanguageFeatureObservation("foreign-source", classification, List.of(), Optional.empty()));
    var result = new LanguageFeatureCoverageResult(input.languageFeatureCoverage(), occurrences, observations,
        input.languageFeatureCoverageResult().capabilityDiagnostics());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
            input.diagnostics(), result));

    // Assert
    assertEquals("Language feature observations must belong to the input snapshot", exception.getMessage());
  }

  @Test
  void givenForeignDiagnosedObservation_whenSyntaxBoundaryIsBuilt_thenInputIsRejected() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");
    var input = analyze();
    var classification = classification(input, LanguageFeatureCategory.COMPILER_TREE_KIND, "WHILE_LOOP");
    var origin = new SourceSpan("foreign-source", 0, 1);
    var code = classification.diagnosticCode().orElseThrow();
    var diagnostic = new AnalysisDiagnostic(StableId.sha256("diagnostic", "foreign-source", code), code, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "Foreign capability diagnostic", List.of(origin), AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE);
    var occurrences = new ArrayList<>(input.languageFeatureCoverageResult().occurrences());
    occurrences.add(new LanguageFeatureOccurrence("foreign-source", classification, List.of(origin)));
    var observations = new ArrayList<>(input.languageFeatureCoverageResult().observations());
    observations.add(new LanguageFeatureObservation("foreign-source", classification, List.of(origin), Optional.of(diagnostic)));
    var capabilityDiagnostics = new ArrayList<>(input.languageFeatureCoverageResult().capabilityDiagnostics());
    capabilityDiagnostics.add(diagnostic);
    var result = new LanguageFeatureCoverageResult(input.languageFeatureCoverage(), occurrences, observations, capabilityDiagnostics);
    var frontendDiagnostics = new ArrayList<>(input.diagnostics());
    frontendDiagnostics.add(diagnostic);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
            frontendDiagnostics, result));

    // Assert
    assertEquals("Language feature observations must belong to the input snapshot", exception.getMessage());
  }

  private SyntaxAnalysisInput analyze() throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25_CONFIGURATION);
  }

  private void writeSource(String relativePath, String content) throws IOException {
    var path = temporaryDirectory.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }

  private static LanguageFeatureClassification classification(SyntaxAnalysisInput input, LanguageFeatureCategory category,
      String identifier) {
    return input.languageFeatureCoverage().classificationOf(new LanguageFeature(category, identifier)).orElseThrow();
  }

  private static AnalysisDiagnostic diagnostic(SyntaxAnalysisInput input, String code) {
    return input.diagnostics().stream().filter(candidate -> candidate.code().equals(code)).findFirst().orElseThrow();
  }

  private static List<DiagnosticTuple> diagnosticTuples(List<AnalysisDiagnostic> diagnostics) {
    return diagnostics.stream().map(diagnostic -> new DiagnosticTuple(diagnostic.diagnosticKey(), diagnostic.code(), diagnostic.stage(),
        diagnostic.severity(), diagnostic.origins(), diagnostic.outcomeKind())).toList();
  }

  private record DiagnosticTuple(String key, String code, DiagnosticStage stage, DiagnosticSeverity severity,
      List<SourceSpan> origins, AnalysisOutcomeKind outcomeKind) {
  }

  private record CompilerError(String code) implements Diagnostic<JavaFileObject> {
    @Override
    public Kind getKind() {
      return Kind.ERROR;
    }

    @Override
    public JavaFileObject getSource() {
      return null;
    }

    @Override
    public long getPosition() {
      return NOPOS;
    }

    @Override
    public long getStartPosition() {
      return NOPOS;
    }

    @Override
    public long getEndPosition() {
      return NOPOS;
    }

    @Override
    public long getLineNumber() {
      return NOPOS;
    }

    @Override
    public long getColumnNumber() {
      return NOPOS;
    }

    @Override
    public String getCode() {
      return code;
    }

    @Override
    public String getMessage(Locale locale) {
      return "compiler error";
    }
  }
}
