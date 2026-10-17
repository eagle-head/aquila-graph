package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.UnaryOperator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.JavaSourceCoverageReconciler.ReconciledCoverage;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.FeatureContextOutcome.Support;
import org.aquilagraph.source.api.JavaField;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext.Partition;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class DeclarationPropertyCorrespondenceTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String SOURCE = "class Sample { int first; void one() {} int second; void two() {} }";
  private static final String UNAVAILABLE_SOURCE = "class Sample { Sample() {} int first; void one() {} }";
  private static final String UNIT_KIND = "CompilationUnitTree::Tree#getKind()";
  private static final String UNIT_SOURCE = "CompilationUnitTree::CompilationUnitTree#getSourceFile()";
  private static final String MEMBERS = "TypeElement::TypeElement#getEnclosedElements()";

  @TempDir
  Path directory;

  @Test
  void givenActualDeclarationPropertyProducer_whenReconciledAgainstEarlierCaptures_thenEveryEncounterRetainsItsExactPairAndValue()
      throws IOException {
    // Arrange
    var original = analyze();
    withCompiler(original, false, fixture -> {
      // Act
      var reconciled = fixture.reconcile(fixture.written(), fixture.attributed());
      var admitted = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), fixture.translation().value().program(),
          fixture.translation().value().aggregate(), reconciled.diagnostics(), reconciled.coverageResult());

      // Assert
      assertEquals(List.of(), admitted.diagnostics());
      assertEquals(original.languageFeatureCoverageResult().validationEvidence().writtenInventory(), fixture.capture().inventory());
      assertEquals(original.languageFeatureCoverageResult().validationEvidence().attributedExpectations(), fixture.expectations());
      assertEquals(Set.of("CompilationUnitTree", "ClassTree", "MethodTree", "VariableTree", "BlockTree", "TypeElement", "ExecutableElement"),
          Stream.concat(fixture.written().encounters().stream(), fixture.attributed().encounters().stream())
              .map(encounter -> encounter.address().role().substring(0, encounter.address().role().indexOf("::"))).collect(Collectors.toSet()));
      var outcomes = admitted.languageFeatureCoverageResult().validationEvidence().outcomes();
      for (var encounter : Stream.concat(fixture.written().encounters().stream(), fixture.attributed().encounters().stream()).toList()) {
        var outcome = outcomes.stream().filter(value -> value.address().equals(encounter.address())).findFirst().orElseThrow();
        assertEquals(Optional.of(encounter.occurrence()), outcome.occurrence());
        assertEquals(Optional.of(encounter.observation()), outcome.observation());
        assertEquals(Optional.of(encounter.value()), outcome.encounteredValue());
      }
    });
  }

  @TestFactory
  Stream<DynamicTest> givenActualProducerCarriers_whenAnEncounterIsLostOrReordered_thenReconciliationRejectsIncompleteAccounting() {
    return Stream.of(
        new EncounterChange("missing-compilation-unit-row", Partition.WRITTEN_PROPERTY, removeSurface("CompilationUnitTree")),
        new EncounterChange("missing-class-row", Partition.WRITTEN_PROPERTY, removeSurface("ClassTree")),
        new EncounterChange("missing-method-row", Partition.WRITTEN_PROPERTY, removeSurface("MethodTree")),
        new EncounterChange("missing-variable-row", Partition.WRITTEN_PROPERTY, removeSurface("VariableTree")),
        new EncounterChange("missing-type-element-row", Partition.ATTRIBUTED_PROPERTY, removeSurface("TypeElement")),
        new EncounterChange("missing-executable-element-row", Partition.ATTRIBUTED_PROPERTY, removeSurface("ExecutableElement")),
        new EncounterChange("missing-written-carrier", Partition.WRITTEN_PROPERTY, ignored -> List.of()),
        new EncounterChange("missing-attributed-carrier", Partition.ATTRIBUTED_PROPERTY, ignored -> List.of()),
        new EncounterChange("reordered-written-rows", Partition.WRITTEN_PROPERTY, DeclarationPropertyCorrespondenceTest::swapFirstRows),
        new EncounterChange("reordered-attributed-rows", Partition.ATTRIBUTED_PROPERTY, DeclarationPropertyCorrespondenceTest::swapFirstRows))
        .map(change -> DynamicTest.dynamicTest(change.name(), () -> assertCarrierRejected(change)));
  }

  @Test
  void givenExplicitlyUnavailableCompilerOwners_whenCapturedAndEncounteredIndependently_thenKnownPairsRemainWithBlockingFailures()
      throws IOException {
    // Arrange
    var original = analyze(UNAVAILABLE_SOURCE);
    assertTrue(original.program().types().getFirst().methods().stream().allMatch(method -> !method.origins().isEmpty()));
    withCompiler(original, true, fixture -> {
      // Act
      var reconciled = fixture.reconcile(fixture.written(), fixture.attributed());
      var admitted = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), fixture.translation().value().program(),
          fixture.translation().value().aggregate(), reconciled.diagnostics(), reconciled.coverageResult());

      // Assert
      var captured = fixture.expectations().projections().stream()
          .filter(projection -> projection.address().partition() == Partition.ATTRIBUTED_PROPERTY).toList();
      assertFalse(captured.isEmpty());
      assertTrue(captured.stream().allMatch(projection -> projection.value().state() == State.UNAVAILABLE
          && projection.value().items().isEmpty() && projection.ownerIdentity().isEmpty()));
      assertEquals(captured.stream().map(AttributedCoverageExpectations.Projection::address).toList(),
          fixture.attributed().encounters().stream().map(ObservedPropertyEncounters.Encounter::address).toList());
      assertTrue(fixture.attributed().encounters().stream().allMatch(encounter -> encounter.value().state() == State.UNAVAILABLE));
      var outcomes = admitted.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
          .filter(outcome -> outcome.address().partition() == Partition.ATTRIBUTED_PROPERTY).toList();
      assertEquals(captured.size(), outcomes.size());
      assertTrue(outcomes.stream().allMatch(outcome -> outcome.support().mode() == Mode.FAILED && outcome.occurrence().isPresent()
          && outcome.observation().isPresent() && !outcome.emissions().isEmpty()));
      assertTrue(admitted.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.inconsistent-source-evidence")));
      assertEquals(fixture.expectations(), admitted.languageFeatureCoverageResult().validationEvidence().attributedExpectations());
    });
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableAttribution_whenAnActualEncounterClaimsAbsenceOrZero_thenFrozenSemanticExpectationsRejectIt() {
    return Stream.of(State.EMPTY, State.ABSENT)
        .map(state -> DynamicTest.dynamicTest("unavailable-to-" + state, () -> assertUnavailableEncounterRejected(state)));
  }

  @Test
  void givenMixedWrittenAndDerivedMembers_whenCanonicalFactsRemainUnchanged_thenIndependentSupportKeepsCompilerOrderAndProvenance()
      throws IOException {
    // Arrange
    var original = analyze();
    var type = original.program().types().getFirst();
    var order = original.aggregate().orElseThrow().declarationMemberOrder(type.typeKey()).orElseThrow();

    // Act
    var admitted = JavaCoverageTestSupport.withFacts(original, original.program(), original.aggregate(),
        JavaCoverageTestSupport.emissions(original));

    // Assert
    assertEquals(List.of("first", "second"), type.fields().stream().map(JavaField::name).toList());
    assertEquals(List.of("type:Sample#field:first", "type:Sample#one()", "type:Sample#field:second", "type:Sample#two()"),
        order.members().stream().filter(member -> member.writtenOrdinal().isPresent()).map(DeclarationMemberOrder.Member::declarationKey).toList());
    assertEquals(List.of(0, 1, 2, 3), order.members().stream().flatMap(member -> member.writtenOrdinal().stream()).toList());
    assertEquals(Provenance.COMPILER_DERIVED, order.members().stream()
        .filter(member -> member.declarationKey().equals("type:Sample#<init>()")).findFirst().orElseThrow().provenance());
    assertEquals(Mode.FACTS, members(admitted).support().mode());
    assertEquals(List.of(), admitted.diagnostics());
    assertFrozenStages(original, admitted);
  }

  @Test
  void givenFixedSourceAndMemberDisplay_whenCanonicalFieldNameChanges_thenActualSupportRejectsTheContradiction() throws IOException {
    // Arrange
    var original = analyze();
    var actual = original.program().types().getFirst();
    var fields = actual.fields().stream().map(field -> field.name().equals("first")
        ? new JavaField(field.fieldKey(), field.ownerTypeKey(), "altered", field.staticType(), field.modifiers(), field.enumConstant(),
            field.sourceDeclared(), field.origins()) : field).toList();
    var changed = new JavaProgram(List.of(copyType(actual, fields, actual.methods())));
    assertEquals("altered", changed.types().getFirst().fields().getFirst().name());
    assertEquals("FIELD:first", members(original).encounteredValue().orElseThrow().items().stream()
        .filter(item -> item.identity().equals(fields.getFirst().fieldKey())).findFirst().orElseThrow().value());

    // Act
    var partial = JavaCoverageTestSupport.withFacts(original, changed, original.aggregate(),
        JavaCoverageTestSupport.emissions(original));

    // Assert
    assertEquals(Mode.FAILED, members(partial).support().mode());
    assertEquals(members(original).occurrence(), members(partial).occurrence());
    assertEquals(members(original).observation(), members(partial).observation());
    assertTrue(partial.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-evidence")));
    assertFrozenStages(original, partial);
    assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        changed, original.aggregate(), original.diagnostics(), original.languageFeatureCoverageResult()));
  }

  @Test
  void givenFixedDerivedConstructorProvenance_whenCanonicalDeclarationClaimsWrittenSource_thenActualSupportRejectsTheContradiction()
      throws IOException {
    // Arrange
    var original = analyze();
    var actual = original.program().types().getFirst();
    var derived = actual.methods().stream().filter(method -> method.constructor() && !method.sourceDeclared()).findFirst().orElseThrow();
    assertEquals(List.of(), derived.origins());
    var methods = actual.methods().stream().map(method -> method.methodKey().equals(derived.methodKey())
        ? new JavaMethod(method.methodKey(), method.ownerTypeKey(), method.name(), method.signature(), method.returnType(), method.parameters(),
            method.modifiers(), method.constructor(), true, method.hasBody(), method.origins()) : method).toList();
    var changed = new JavaProgram(List.of(copyType(actual, actual.fields(), methods)));
    assertEquals(Provenance.COMPILER_DERIVED, members(original).encounteredValue().orElseThrow().items().stream()
        .filter(item -> item.identity().equals(derived.methodKey())).findFirst().orElseThrow().provenance());
    assertTrue(changed.types().getFirst().methods().stream().filter(method -> method.methodKey().equals(derived.methodKey()))
        .findFirst().orElseThrow().sourceDeclared());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> JavaCoverageTestSupport.withFacts(original, changed,
        original.aggregate(), JavaCoverageTestSupport.emissions(original)));

    // Assert
    assertTrue(failure.getMessage().contains("provenance") || failure.getMessage().contains("source") || failure.getMessage().contains("member"));
  }

  @Test
  void givenFixedMixedMemberCapture_whenOnlyCrossFamilyOrderChanges_thenSuccessfulSupportFailsWithoutLosingTheKnownPair() throws IOException {
    // Arrange
    var original = analyze();
    var actual = original.aggregate().orElseThrow();
    var order = actual.declarationMemberOrders().getFirst();
    var changedMembers = new ArrayList<>(order.members());
    var first = changedMembers.stream().filter(member -> member.declarationKey().equals("type:Sample#field:first")).findFirst().orElseThrow();
    var method = changedMembers.stream().filter(member -> member.declarationKey().equals("type:Sample#one()")).findFirst().orElseThrow();
    Collections.swap(changedMembers, changedMembers.indexOf(first), changedMembers.indexOf(method));
    var changedOrder = new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(), changedMembers);
    assertEquals(List.of("type:Sample#field:first", "type:Sample#field:second"), changedOrder.members().stream()
        .filter(member -> member.kind().equals("FIELD")).map(DeclarationMemberOrder.Member::declarationKey).toList());
    assertEquals(order.members().stream().filter(member -> member.kind().equals("METHOD") || member.kind().equals("CONSTRUCTOR"))
        .map(DeclarationMemberOrder.Member::declarationKey).toList(), changedOrder.members().stream()
            .filter(member -> member.kind().equals("METHOD") || member.kind().equals("CONSTRUCTOR"))
            .map(DeclarationMemberOrder.Member::declarationKey).toList());
    var changed = copyAggregate(original, changedOrder);

    // Act
    var partial = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(changed),
        JavaCoverageTestSupport.emissions(original));

    // Assert
    assertFrozenStages(original, partial);
    assertEquals(Mode.FAILED, members(partial).support().mode());
    assertEquals(members(original).occurrence(), members(partial).occurrence());
    assertEquals(members(original).observation(), members(partial).observation());
    assertTrue(partial.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-evidence")));
  }

  @Test
  void givenCorrectUnitKindAccessor_whenItsPolicyLinkNamesSameFileClass_thenFactoryRejectsTheWrongUnitBoundary() throws IOException {
    // Arrange
    var original = analyze();
    var selected = property(original, UNIT_KIND);
    var wrong = original.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().partition() == Partition.TREE
            && outcome.observation().orElseThrow().classification().feature().identifier().equals("CLASS")).findFirst().orElseThrow();
    assertEquals(selected.address().sourceFileKey(), wrong.address().sourceFileKey());
    var support = new Support(Mode.POLICY_PAIR, List.of(), Optional.of(wrong.address()), Optional.empty(), Optional.empty());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedSupport(original, selected, support));

    // Assert
    assertEquals("Unit-kind property must reference the exact same-owner compilation-unit policy pair", failure.getMessage());
  }

  @TestFactory
  Stream<DynamicTest> givenCorrectSourceFileAccessor_whenClaimNamesDifferentSnapshotValue_thenFactoryRejectsTheWrongSourceBoundary() {
    return Stream.of("different-file", "same-key-different-content").map(change -> DynamicTest.dynamicTest(change,
        () -> assertSourceRejected(change)));
  }

  private void assertCarrierRejected(EncounterChange change) throws IOException {
    // Arrange
    var original = analyze();
    withCompiler(original, false, fixture -> {
      var baseline = change.partition() == Partition.WRITTEN_PROPERTY ? fixture.written() : fixture.attributed();
      var changed = new ObservedPropertyEncounters(change.change().apply(baseline.encounters()));
      assertFalse(baseline.encounters().equals(changed.encounters()));

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> fixture.reconcile(
          change.partition() == Partition.WRITTEN_PROPERTY ? changed : fixture.written(),
          change.partition() == Partition.ATTRIBUTED_PROPERTY ? changed : fixture.attributed()));

      // Assert
      assertEquals("Actual property encounters must independently cover every ordered captured owner/accessor exactly once", failure.getMessage());
      assertEquals(original.languageFeatureCoverageResult().validationEvidence().writtenInventory(), fixture.capture().inventory());
      assertEquals(original.languageFeatureCoverageResult().validationEvidence().attributedExpectations(), fixture.expectations());
    });
  }

  private void assertUnavailableEncounterRejected(State state) throws IOException {
    // Arrange
    var original = analyze(UNAVAILABLE_SOURCE);
    withCompiler(original, true, fixture -> {
      var selected = fixture.attributed().encounters().stream().filter(encounter -> encounter.address().role().equals(MEMBERS))
          .findFirst().orElseThrow();
      var changed = new ObservedPropertyEncounters(fixture.attributed().encounters().stream().map(encounter -> encounter.equals(selected)
          ? new ObservedPropertyEncounters.Encounter(encounter.address(), encounter.occurrence(), encounter.observation(),
              new CoveragePropertyValue(state, List.of())) : encounter).toList());

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> fixture.reconcile(fixture.written(), changed));

      // Assert
      assertEquals("Actual accessor encounters must exactly match the independent scalar/list projection", failure.getMessage());
      assertEquals(State.UNAVAILABLE, fixture.expectations().projections().stream().filter(projection -> projection.address().equals(selected.address()))
          .findFirst().orElseThrow().value().state());
    });
  }

  private void assertSourceRejected(String change) throws IOException {
    // Arrange
    var original = analyze();
    var selected = property(original, UNIT_SOURCE);
    var source = original.snapshot().sourceFiles().getFirst();
    var wrong = change.equals("different-file") ? SourceFile.fromContent(Path.of("Other.java"), "class Other {}")
        : new SourceFile(source.sourceFileKey(), source.relativePath(), source.contentDigest(), "class Changed {}");
    var support = new Support(Mode.SOURCE_FILE, List.of(), Optional.empty(), Optional.of(wrong), Optional.empty());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedSupport(original, selected, support));

    // Assert
    assertEquals("Source-file support must name the exact captured source value", failure.getMessage());
  }

  private SyntaxAnalysisInput analyze() throws IOException {
    return analyze(SOURCE);
  }

  private SyntaxAnalysisInput analyze(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), source)));
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CONFIGURATION);
    assertEquals(List.of(), input.diagnostics());
    return input;
  }

  private static void withCompiler(SyntaxAnalysisInput original, boolean unavailableOwners, FixtureAction action) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var sources = new SnapshotSourceCatalog(original.snapshot());
    try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, JavaCompilerInvocation.from(CONFIGURATION).compilerOptions(), null,
          sources.compilationUnits());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      var trees = Trees.instance(task);
      var sourceEvidence = new JavaSourceEvidence(trees.getSourcePositions());
      var inventory = ParsedTreeInventory.capture(units, sources::find, sourceEvidence);
      var parsed = new ParsedJavaSource(units, sourceEvidence, inventory);
      var coverage = original.languageFeatureCoverage();
      var capture = JavaSourceCoverageCapture.capture(original.snapshot(), parsed, sources, coverage, JavaTranslationPolicy.legacyAnalysis());
      var producer = new JavaDeclarationPropertyEncounterProducer(units, sources, sourceEvidence, coverage, JavaTranslationPolicy.legacyAnalysis());
      var written = producer.observeWritten();
      var kinds = new JavaCompilerKindDiagnostics(sources, sourceEvidence, coverage, JavaTranslationPolicy.legacyAnalysis()).observe(units);
      task.analyze();
      assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      var types = task.getTypes();
      var semantics = new SemanticResolver(trees, types, task.getElements(), new DeclarationIdentityRegistry(trees, types));
      var projectionTrees = unavailableOwners ? new UnavailableOwnerTrees(trees) : trees;
      var projectionSemantics = new SemanticResolver(projectionTrees, types, task.getElements(), new DeclarationIdentityRegistry(projectionTrees, types));
      var expectations = capture.captureAttributed(projectionSemantics);
      var attributed = producer.observeAttributed(projectionSemantics);
      var typeKinds = new JavaTypeKindObserver(sources, semantics, sourceEvidence, inventory, coverage, JavaTranslationPolicy.legacyAnalysis()).observe(units);
      var translation = new JavaProgramTranslator(sources, semantics, sourceEvidence, inventory, JavaTranslationPolicy.legacyAnalysis()).translate(original.snapshot(), units);
      assertEquals(List.of(), kinds.diagnostics());
      assertEquals(List.of(), typeKinds.diagnostics());
      assertEquals(List.of(), translation.diagnostics());
      action.accept(new Fixture(original, capture, expectations, kinds, typeKinds, translation, written, attributed));
    }
  }

  private static UnaryOperator<List<ObservedPropertyEncounters.Encounter>> removeSurface(String surface) {
    return encounters -> {
      var changed = new ArrayList<>(encounters);
      var selected = changed.stream().filter(encounter -> encounter.address().role().startsWith(surface + "::")).findFirst().orElseThrow();
      changed.remove(selected);
      return List.copyOf(changed);
    };
  }

  private static List<ObservedPropertyEncounters.Encounter> swapFirstRows(List<ObservedPropertyEncounters.Encounter> encounters) {
    var changed = new ArrayList<>(encounters);
    Collections.swap(changed, 0, 1);
    return List.copyOf(changed);
  }

  private static FeatureContextOutcome members(SyntaxAnalysisInput input) {
    return property(input, MEMBERS);
  }

  private static FeatureContextOutcome property(SyntaxAnalysisInput input, String accessor) {
    return input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals(accessor)).findFirst().orElseThrow();
  }

  private static LanguageFeatureCoverageResult changedSupport(SyntaxAnalysisInput original, FeatureContextOutcome selected, Support support) {
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(), support, outcome.emissions()) : outcome).toList();
    return LanguageFeatureCoverageResult.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private static JavaType copyType(JavaType actual, List<JavaField> fields, List<JavaMethod> methods) {
    return new JavaType(actual.typeKey(), actual.qualifiedName(), actual.kind(), actual.modifiers(), actual.enclosingType(), actual.directSuperclass(),
        actual.directInterfaces(), actual.permittedSubclasses(), actual.typeParameters(), actual.sourceDeclared(), fields, actual.recordComponents(), methods,
        actual.synthesizedDeclarations(), actual.origins());
  }

  private static JavaAstAggregate copyAggregate(SyntaxAnalysisInput original, DeclarationMemberOrder changedOrder) {
    var actual = original.aggregate().orElseThrow();
    var builder = AggregateFixture.builder(original.snapshot());
    for (var root : actual.roots()) {
      var nodes = root.descendantsAndSelf();
      builder.add(new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), actual.semanticEdges()));
    }
    actual.writtenSourceAssociations().forEach(builder::addWrittenSourceAssociation);
    actual.declarationTypeEvidence().forEach(builder::addDeclarationTypeEvidence);
    actual.declarationMemberOrders().stream().map(order -> order.ownerTypeKey().equals(changedOrder.ownerTypeKey()) ? changedOrder : order)
        .forEach(builder::addDeclarationMemberOrder);
    for (var key : actual.methodBodyKeys()) {
      var syntax = actual.methodSyntax(key).orElseThrow();
      var nodes = syntax.root().descendantsAndSelf();
      var uses = nodes.stream().flatMap(node -> node.children().stream().flatMap(child -> syntax.useDecision(node.nodeId(), child.role(), child.position()).stream())).toList();
      var calls = nodes.stream().flatMap(node -> syntax.invocationArity(node.nodeId()).stream()).toList();
      var accesses = nodes.stream().flatMap(node -> syntax.staticAccess(node.nodeId()).stream()).toList();
      var concatenations = nodes.stream().flatMap(node -> syntax.stringConcatenation(node.nodeId()).stream()).toList();
      builder.addMethodBody(key, syntax.root(), actual.methodBodyOrigin(key).orElseThrow(), uses, calls, accesses, concatenations);
    }
    return builder.seal();
  }

  private static void assertFrozenStages(SyntaxAnalysisInput original, SyntaxAnalysisInput changed) {
    var frozen = original.languageFeatureCoverageResult().validationEvidence();
    var retained = changed.languageFeatureCoverageResult().validationEvidence();
    assertEquals(frozen.writtenInventory(), retained.writtenInventory());
    assertEquals(frozen.attributedExpectations(), retained.attributedExpectations());
  }

  private record EncounterChange(String name, Partition partition, UnaryOperator<List<ObservedPropertyEncounters.Encounter>> change) {
  }

  @FunctionalInterface
  private interface FixtureAction {
    void accept(Fixture fixture) throws IOException;
  }

  private record Fixture(SyntaxAnalysisInput original, JavaSourceCoverageCapture capture, AttributedCoverageExpectations expectations,
      ObservedLanguageFeatures kinds, JavaTypeKindObserver.ObservedTypeKinds typeKinds, TranslationResult<TranslatedProgram> translation,
      ObservedPropertyEncounters written, ObservedPropertyEncounters attributed) {
    ReconciledCoverage reconcile(ObservedPropertyEncounters writtenEvidence, ObservedPropertyEncounters attributedEvidence) {
      return new JavaSourceCoverageReconciler(capture.inventory(), expectations, translation.value().program(), translation.value().aggregate(),
          translation.emissions()).reconcile(original.languageFeatureCoverage(), kinds, typeKinds, translation, writtenEvidence, attributedEvidence);
    }
  }

  private static final class UnavailableOwnerTrees extends Trees {
    private final Trees delegate;

    private UnavailableOwnerTrees(Trees delegate) { this.delegate = delegate; }

    @Override public SourcePositions getSourcePositions() { return delegate.getSourcePositions(); }
    @Override public Tree getTree(Element element) { return delegate.getTree(element); }
    @Override public ClassTree getTree(TypeElement element) { return delegate.getTree(element); }
    @Override public MethodTree getTree(ExecutableElement element) { return delegate.getTree(element); }
    @Override public Tree getTree(Element element, AnnotationMirror mirror) { return delegate.getTree(element, mirror); }
    @Override public Tree getTree(Element element, AnnotationMirror mirror, AnnotationValue value) { return delegate.getTree(element, mirror, value); }
    @Override public TreePath getPath(CompilationUnitTree unit, Tree tree) { return delegate.getPath(unit, tree); }
    @Override public TreePath getPath(Element element) { return delegate.getPath(element); }
    @Override public TreePath getPath(Element element, AnnotationMirror mirror) { return delegate.getPath(element, mirror); }
    @Override public TreePath getPath(Element element, AnnotationMirror mirror, AnnotationValue value) { return delegate.getPath(element, mirror, value); }
    @Override public Element getElement(TreePath path) { return path.getLeaf() instanceof ClassTree || path.getLeaf() instanceof MethodTree ? null : delegate.getElement(path); }
    @Override public TypeMirror getTypeMirror(TreePath path) { return delegate.getTypeMirror(path); }
    @Override public Scope getScope(TreePath path) { return delegate.getScope(path); }
    @Override public String getDocComment(TreePath path) { return delegate.getDocComment(path); }
    @Override public boolean isAccessible(Scope scope, TypeElement type) { return delegate.isAccessible(scope, type); }
    @Override public boolean isAccessible(Scope scope, Element element, DeclaredType type) { return delegate.isAccessible(scope, element, type); }
    @Override public TypeMirror getOriginalType(ErrorType type) { return delegate.getOriginalType(type); }
    @Override public void printMessage(Diagnostic.Kind kind, CharSequence message, Tree tree, CompilationUnitTree unit) { delegate.printMessage(kind, message, tree, unit); }
    @Override public TypeMirror getLub(CatchTree tree) { return delegate.getLub(tree); }
  }
}
