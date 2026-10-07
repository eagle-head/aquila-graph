package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaLanguageCompletionContractTest {
  private static final CompilerConfiguration JAVA_25_CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String MONITOR_DIAGNOSTIC = "frontend.unsupported-tree-kind.synchronized";

  @TempDir Path temporaryDirectory;

  @Test
  void givenJava25Configuration_whenFrontendRuns_thenOneCombinedReleaseBoundCoverageContractIsReturned() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { int value() { return 1; } }");
    var expectedClassifications = new ArrayList<LanguageFeatureClassification>();
    expectedClassifications.addAll(JavaCompilerKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION).classifications());
    expectedClassifications.addAll(JavaTypeKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION).classifications());
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
        () -> JavaCoverageTestSupport.emptyResult(reducedCoverage, InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/coverage-check/empty"), List.of())));
    var augmentedException = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.emptyResult(augmentedCoverage, InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/coverage-check/empty"), List.of())));
    var reclassifiedException = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.emptyResult(reclassifiedCoverage, InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/coverage-check/empty"), List.of())));

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
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");

    // Act
    var input = analyze();
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    var diagnostic = diagnostic(input, MONITOR_DIAGNOSTIC);
    assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
    assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
    assertEquals(AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE, diagnostic.outcomeKind());
    assertEquals(1, diagnostic.origins().size());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
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
    assertFalse(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(MONITOR_DIAGNOSTIC)));
  }

  @Test
  void givenMalformedSource_whenAnalysisRuns_thenCompilerErrorIdentityIsRetained() throws IOException {
    // Arrange
    writeSource("Broken.java", "class Broken { void run( } }");

    // Act
    var input = analyze();
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
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
    var run = new SourceAnalysisFixture().analyze(analyze());

    // Assert
    var diagnostic = run.diagnostics().stream()
        .filter(candidate -> candidate.code().equals("frontend.unsupported-tree-kind.erroneous")).findFirst().orElseThrow();
    assertEquals(AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE, diagnostic.outcomeKind());
    assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
    assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
    assertFalse(diagnostic.origins().isEmpty());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
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
  void givenWrittenAnnotationDefault_whenAnalysisRuns_thenRepresentedPropertyAndTypedValueComplete()
      throws IOException {
    // Arrange
    writeSource("Settings.java", "@interface Settings { int count() default 1; }");

    // Act
    var input = analyze();
    var run = new SourceAnalysisFixture().analyze(input);
    var classification = classification(input, LanguageFeatureCategory.DECLARATION_PROPERTY,
        "MethodTree::MethodTree#getDefaultValue()");
    var defaultValue = input.aggregate().orElseThrow().nodes().stream().map(node -> node.value())
        .filter(SyntaxValue.ExecutableDeclaration.class::isInstance).map(SyntaxValue.ExecutableDeclaration.class::cast)
        .flatMap(declaration -> declaration.defaultValue().stream()).findFirst().orElseThrow();

    // Assert
    assertEquals(LanguageFeaturePolicy.REPRESENTED, classification.policy());
    assertTrue(classification.diagnosticCode().isEmpty());
    assertEquals(new AnnotationScalar.IntValue(1), defaultValue);
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
  }

  @Test
  void givenDiagnosedMonitorSyntax_whenFrontendRuns_thenContextualOccurrenceOwnsTheExactDiagnostic()
      throws IOException {
    // Arrange
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");

    // Act
    var input = analyze();
    var diagnostic = diagnostic(input, MONITOR_DIAGNOSTIC);
    var propertyObservations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> observation.diagnostic().filter(diagnostic::equals).isPresent()).toList();

    // Assert
    assertEquals(List.of("SYNCHRONIZED"), propertyObservations.stream()
            .map(observation -> observation.classification().feature().identifier()).sorted().toList());
    assertTrue(propertyObservations.stream()
        .allMatch(observation -> observation.sourceFileKey().equals(diagnostic.origins().getFirst().sourceFileKey())));
  }

  @Test
  void givenDiagnosedMonitorOccurrence_whenItsDiagnosticIsRemoved_thenCoverageRejectsTheDetachment()
      throws IOException {
    // Arrange
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");
    var input = analyze();
    var diagnostic = diagnostic(input, MONITOR_DIAGNOSTIC);
    var capabilityDiagnostics = input.languageFeatureCoverageResult().capabilityDiagnostics().stream()
        .filter(candidate -> !candidate.equals(diagnostic)).toList();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.alteredResult(input, input.languageFeatureCoverageResult().occurrences(), input.languageFeatureCoverageResult().observations(), capabilityDiagnostics));

    // Assert
    assertEquals("Diagnosed policy pair requires its creating-site diagnostic association", exception.getMessage());
  }

  @Test
  void givenDiagnosedMonitorOccurrence_whenItsObservationIsRemoved_thenCoverageRejectsThePartialInventory()
      throws IOException {
    // Arrange
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");
    var input = analyze();
    var diagnostic = diagnostic(input, MONITOR_DIAGNOSTIC);
    var observations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> !observation.classification().feature().identifier()
            .equals("SYNCHRONIZED"))
        .toList();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.alteredResult(input, input.languageFeatureCoverageResult().occurrences(), observations, input.languageFeatureCoverageResult().capabilityDiagnostics()));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", exception.getMessage());
    assertTrue(input.languageFeatureCoverageResult().observations().stream()
        .anyMatch(observation -> observation.diagnostic().filter(diagnostic::equals).isPresent()));
  }

  @Test
  void givenDiagnosedMonitorOccurrence_whenItsObservationAndDiagnosticAreRemoved_thenCoverageRejectsMissingFact()
      throws IOException {
    // Arrange
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");
    var input = analyze();
    var diagnostic = diagnostic(input, MONITOR_DIAGNOSTIC);
    var observations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> observation.diagnostic().filter(diagnostic::equals).isEmpty()).toList();
    var capabilityDiagnostics = input.languageFeatureCoverageResult().capabilityDiagnostics().stream()
        .filter(candidate -> !candidate.equals(diagnostic)).toList();
    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.alteredResult(input, input.languageFeatureCoverageResult().occurrences(), observations, capabilityDiagnostics));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", exception.getMessage());
  }

  @Test
  void givenUnsupportedMethodSyntax_whenAnalysisRuns_thenFrontendAndControlFlowOutcomesRemainDistinct() throws IOException {
    // Arrange
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");

    // Act
    var run = new SourceAnalysisFixture().analyze(analyze());

    // Assert
    assertTrue(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(MONITOR_DIAGNOSTIC)
        && diagnostic.stage() == DiagnosticStage.FRONTEND));
    var controlFlowDiagnostic = run.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("control-flow.source-unavailable")).findFirst().orElseThrow();
    assertEquals(DiagnosticStage.CONTROL_FLOW, controlFlowDiagnostic.stage());
    assertEquals(DiagnosticSeverity.ERROR, controlFlowDiagnostic.severity());
    assertEquals(AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE, controlFlowDiagnostic.outcomeKind());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
  }

  @Test
  void givenSameSnapshot_whenFrontendRunsTwice_thenCoverageDiagnosticsAndCompletionAreDeterministic() throws IOException {
    // Arrange
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    var frontend = new JavaFrontend(loader);

    // Act
    var first = frontend.analyze(snapshot, JAVA_25_CONFIGURATION);
    var second = frontend.analyze(snapshot, JAVA_25_CONFIGURATION);
    var firstRun = new SourceAnalysisFixture().analyze(first);
    var secondRun = new SourceAnalysisFixture().analyze(second);

    // Assert
    assertEquals(first.languageFeatureCoverage(), second.languageFeatureCoverage());
    assertEquals(first.languageFeatureCoverageResult(), second.languageFeatureCoverageResult());
    assertEquals(diagnosticTuples(first.diagnostics()), diagnosticTuples(second.diagnostics()));
    assertEquals(AnalysisCompletion.PARTIAL, firstRun.completion());
    assertEquals(firstRun.completion(), secondRun.completion());
  }

  @Test
  void givenMonitorDiagnosticWithErroneousOutcome_whenCoverageResultIsBuilt_thenOutcomeMismatchIsRejected() throws IOException {
    // Arrange
    writeSource("Monitoring.java", "final class Monitoring { void run() { synchronized (this) { } } }");
    var input = analyze();
    var original = diagnostic(input, MONITOR_DIAGNOSTIC);
    var mismatched = new AnalysisDiagnostic(StableId.sha256("diagnostic", original.diagnosticKey(), "mismatched-outcome"),
        original.code(), original.stage(), original.severity(), original.message(), original.origins(),
        AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE);
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var events = new ArrayList<>(evidence.diagnosticEmissions());
    var extraEvent = new DiagnosticEmission(new DiagnosticEmission.Address("capability-outcome-test", "monitor-context", 0), mismatched,
        mismatched.outcomeKind(), List.of(), events.size());
    events.add(extraEvent);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> LanguageFeatureCoverageResult.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
            evidence.outcomes(), evidence.unmatchedFacts(), events));

    // Assert
    assertEquals("Language feature capability diagnostic has an inconsistent outcome kind", exception.getMessage());
  }

  @Test
  void givenDiagnosedCoverageOutcome_whenItsDiagnosticIsRemoved_thenSyntaxBoundaryRejectsTheInput() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { void run() { synchronized (this) { } } }");
    var input = analyze();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(), List.of(),
            input.languageFeatureCoverageResult()));

    // Assert
    assertEquals("Coverage capability diagnostics must be retained in frontend diagnostics", exception.getMessage());
  }

  @Test
  void givenNonemptySnapshotWithoutCompilationUnitObservation_whenCoverageFactoryIsCalled_thenEvidenceIsRejected() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");
    var input = analyze();
    var observations = input.languageFeatureCoverageResult().observations().stream()
        .filter(observation -> !observation.classification().feature().identifier().equals("COMPILATION_UNIT")).toList();
    var occurrences = input.languageFeatureCoverageResult().occurrences().stream()
        .filter(occurrence -> !occurrence.classification().feature().identifier().equals("COMPILATION_UNIT")).toList();


    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.alteredResult(input, occurrences, observations, input.languageFeatureCoverageResult().capabilityDiagnostics()));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", exception.getMessage());
  }

  @Test
  void givenForeignRepresentedObservation_whenCoverageFactoryIsCalled_thenEvidenceIsRejected() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");
    var input = analyze();
    var classification = classification(input, LanguageFeatureCategory.COMPILER_TREE_KIND, "CLASS");
    var occurrences = new ArrayList<>(input.languageFeatureCoverageResult().occurrences());
    occurrences.add(new LanguageFeatureOccurrence("foreign-source", classification, List.of()));
    var observations = new ArrayList<>(input.languageFeatureCoverageResult().observations());
    observations.add(new LanguageFeatureObservation("foreign-source", classification, List.of(), Optional.empty()));


    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.alteredResult(input, occurrences, observations, input.languageFeatureCoverageResult().capabilityDiagnostics()));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", exception.getMessage());
  }

  @Test
  void givenForeignDiagnosedObservation_whenCoverageFactoryIsCalled_thenEvidenceIsRejected() throws IOException {
    // Arrange
    writeSource("Example.java", "final class Example { }");
    var input = analyze();
    var classification = classification(input, LanguageFeatureCategory.COMPILER_TREE_KIND, "SYNCHRONIZED");
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

    var frontendDiagnostics = new ArrayList<>(input.diagnostics());
    frontendDiagnostics.add(diagnostic);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCoverageTestSupport.alteredResult(input, occurrences, observations, capabilityDiagnostics));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", exception.getMessage());
  }

  private SyntaxAnalysisInput analyze() throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25_CONFIGURATION);
  }

  @Test
  void givenAnnotationArrayAndExecutableArray_whenAnalysisRuns_thenSharedKindPolicyPreservesTheirSeparateSourceContracts() throws IOException {
    // Arrange
    writeSource("Arrays.java", "@interface A { int[] value(); } @A({1,2}) class Arrays { int[] values() { return new int[] {3,4}; } }");

    // Act
    var input = analyze();
    var arrays = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.NEW_ARRAY).toList();

    // Assert
    assertEquals(LanguageFeaturePolicy.REPRESENTED, classification(input, LanguageFeatureCategory.COMPILER_TREE_KIND, "NEW_ARRAY").policy());
    assertEquals(List.of(SyntaxKind.ANNOTATION_ARGUMENT, SyntaxKind.ANNOTATION_ARRAY, SyntaxKind.ARRAY_CREATION),
        arrays.stream().map(node -> node.syntaxKind()).toList());
    assertEquals(List.of("1", "2"), arrays.get(1).children(SyntaxRole.VALUE).stream().map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
    assertEquals(List.of("3", "4"), arrays.getLast().children(SyntaxRole.ARRAY_ELEMENT).stream().map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
    assertTrue(input.diagnostics().isEmpty(), input.diagnostics().toString());
    assertFalse(input.diagnostics().stream().anyMatch(item -> item.code().equals("frontend.unrepresented-source-annotation")
        || item.code().equals("frontend.inconsistent-annotation-source-fact")));
    assertEquals(AnalysisCompletion.PARTIAL, new SourceAnalysisFixture().analyze(input).completion());
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
