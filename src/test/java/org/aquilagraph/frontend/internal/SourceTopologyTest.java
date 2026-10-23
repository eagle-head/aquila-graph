package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.admission.CoverageAdmission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.UnaryOperator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.FeatureContextOutcome.Support;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceContext.Partition;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SourceOwnerPath.Role;
import org.aquilagraph.source.api.SourceOwnerPath.Step;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.junit.jupiter.api.Test;

class SourceTopologyTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String RECURSIVE_SOURCE = "class Example { java.util.Map<String, java.util.List<? extends Number[]>> value; }";

  @Test
  void givenNestedWrittenHeaderTypes_whenCaptured_thenAllTwelveRecursiveOccurrencesHaveIndependentObligations() throws IOException {
    // Arrange
    var input = analyze(RECURSIVE_SOURCE);

    // Act
    var contexts = input.languageFeatureCoverageResult().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition().name().equals("WRITTEN_TYPE_USE")).toList();
    var actual = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE).toList();

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertEquals(12, actual.size());
    assertEquals(12, contexts.size());
    assertEquals(Set.copyOf(actual.stream().map(node -> input.aggregate().orElseThrow().writtenSourceAssociation(node.nodeId()).orElseThrow().ownerPath())
        .toList()), Set.copyOf(contexts.stream().map(context -> context.address().ownerPath()).toList()));
  }

  @Test
  void givenParameterAndTypeNameSlots_whenCaptured_thenTheirSeparateRolesKeepOrdinalZero() throws IOException {
    // Arrange
    var input = analyze("class Example { java.util.Map<String, Integer> value; }");

    // Act
    var typePaths = input.aggregate().orElseThrow().writtenSourceAssociations().stream()
        .filter(association -> association.sourceKind() == JavaSourceKind.IDENTIFIER).map(association -> association.ownerPath().toString()).toList();

    // Assert
    assertTrue(typePaths.stream().anyMatch(path -> path.contains("role=TYPE_ARGUMENT, ordinal=0")), () -> typePaths.toString());
    assertTrue(typePaths.stream().anyMatch(path -> path.contains("role=TYPE_ARGUMENT, ordinal=1")), () -> typePaths.toString());
    assertTrue(typePaths.stream().anyMatch(path -> path.contains("role=TYPE_NAME, ordinal=0")), () -> typePaths.toString());
  }

  @Test
  void givenSharedEqualSpanFieldTypes_whenCaptured_thenTypedMemberOwnersRemainDistinct() throws IOException {
    // Arrange
    var input = analyze("class Example { int first, second; }");
    var types = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE).toList();

    // Act
    var paths = types.stream().map(node -> input.aggregate().orElseThrow().writtenSourceAssociation(node.nodeId()).orElseThrow().ownerPath())
        .toList();

    // Assert
    assertEquals(2, types.size());
    assertEquals(types.getFirst().origin(), types.getLast().origin());
    assertNotEquals(paths.getFirst(), paths.getLast());
    assertTrue(paths.getFirst().toString().contains("role=MEMBER, ordinal=0"));
    assertTrue(paths.getLast().toString().contains("role=MEMBER, ordinal=1"));
    assertTrue(paths.stream().allMatch(path -> path.toString().contains("role=DECLARED_TYPE, ordinal=0")));
  }

  @Test
  void givenIndependentHeaderRoles_whenCaptured_thenAttributedRootsKeepTheirOriginalOrderAndLocalOrdinals() throws IOException {
    // Arrange
    var input = analyze("abstract class Example<T extends Number & Comparable<T>> implements java.io.Serializable { "
        + "abstract int value(Example<T> this, int first, String second) throws java.io.IOException, java.sql.SQLException; }");

    // Act
    var contexts = input.languageFeatureCoverageResult().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == Partition.TYPE).toList();

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertEquals(List.of("INTERFACE:0", "TYPE_BOUND:0", "TYPE_BOUND:1", "RETURN_TYPE:0", "DECLARED_TYPE:0",
        "THROWN_TYPE:0", "THROWN_TYPE:1", "DECLARED_TYPE:0", "DECLARED_TYPE:0"),
        contexts.stream().map(context -> context.address().role() + ':' + context.address().ordinal()).toList());
    assertEquals(9, input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().partition() == Partition.TYPE).count());
  }

  @Test
  void givenAnnotatedArraysAndBothWildcardBounds_whenCaptured_thenRecursiveRolesHaveExactActualSupport() throws IOException {
    // Arrange
    var input = analyze("import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} "
        + "class Example<T> { java.util.List<? extends @Mark Number[]> upper; java.util.List<? super T> lower; java.util.List<?> any; }");

    // Act
    var outcomes = recursiveOutcomes(input);
    var roles = outcomes.stream().flatMap(outcome -> outcome.address().ownerPath().steps().stream()).map(Step::role).toList();

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertTrue(roles.containsAll(List.of(Role.UPPER_BOUND, Role.LOWER_BOUND, Role.UNDERLYING_TYPE, Role.COMPONENT_TYPE, Role.TYPE_ARGUMENT)));
    assertTrue(outcomes.stream().allMatch(outcome -> outcome.support().mode() == Mode.FACTS && outcome.support().facts().size() == 1
        && outcome.occurrence().isEmpty() && outcome.observation().isEmpty() && outcome.encounteredValue().isEmpty()));
  }

  @Test
  void givenQualifiedPackageTypeName_whenCaptured_thenEachQualifierHasItsExactRecursiveTypeUse() throws IOException {
    // Arrange
    var input = analyze("class Example { java.util.Map.Entry<String, Integer> entry; }");

    // Act
    var qualifiers = recursiveOutcomes(input).stream()
        .filter(outcome -> outcome.address().ownerPath().lastStep().orElseThrow().role() == Role.TYPE_QUALIFIER).toList();

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertEquals(3, qualifiers.size());
    assertTrue(qualifiers.stream().allMatch(outcome -> outcome.support().mode() == Mode.FACTS));
    assertEquals(2, qualifiers.stream().map(outcome -> (CoverageFactValue.Source) outcome.support().facts().getFirst().value())
        .filter(source -> source.value().value().tag().name().equals("PACKAGE_QUALIFIER")).count());
  }

  @Test
  void givenSharedFieldTypeAnnotation_whenCaptured_thenOnlyOnePhysicalAnnotationOwnsItsOccurrence() throws IOException {
    // Arrange
    var input = analyze("import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} class Example { @Mark int first, second; }");

    // Act
    var annotations = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation).toList();
    var contexts = input.languageFeatureCoverageResult().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == Partition.TREE
            && context.feature().filter(feature -> List.of("ANNOTATION", "TYPE_ANNOTATION").contains(feature.identifier())).isPresent()).toList();

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertEquals(2, annotations.size());
    assertEquals(2, contexts.size());
    assertEquals(2, contexts.stream().map(SourceContext::origins).distinct().count());
  }

  @Test
  void givenCapturedTypedPaths_whenFrontendRunsAgain_thenDurableSyntaxKeysAndRolesRemainIdentical() throws IOException {
    // Arrange
    var first = analyze(RECURSIVE_SOURCE);

    // Act
    var second = analyze(RECURSIVE_SOURCE);

    // Assert
    assertEquals(first.aggregate().orElseThrow().nodes().stream().map(SyntaxNode::nodeId).toList(),
        second.aggregate().orElseThrow().nodes().stream().map(SyntaxNode::nodeId).toList());
    assertEquals(first.aggregate().orElseThrow().nodes().stream().map(SyntaxNode::structuralPath).toList(),
        second.aggregate().orElseThrow().nodes().stream().map(SyntaxNode::structuralPath).toList());
    assertEquals(first.aggregate().orElseThrow().writtenSourceAssociations(), second.aggregate().orElseThrow().writtenSourceAssociations());
  }

  @Test
  void givenMultipleAnnotationsOnMultipleArrayDimensions_whenCaptured_thenDimensionAndAnnotationOrdinalsStaySeparate() throws IOException {
    // Arrange
    var source = "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface A {} "
        + "@Target(ElementType.TYPE_USE) @interface B {} class Example { Object value() { return new int @A @B [1] @B @A [2]; } }";
    var input = analyze(source);

    // Act
    var paths = input.languageFeatureCoverageResult().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == Partition.TREE
            && context.feature().filter(feature -> List.of("ANNOTATION", "TYPE_ANNOTATION").contains(feature.identifier())).isPresent())
        .map(context -> context.address().ownerPath().steps())
        .filter(steps -> steps.stream().anyMatch(step -> step.role() == Role.DIMENSION_ANNOTATION_GROUP)).toList();

    // Assert
    assertEquals(4, paths.size());
    assertEquals(List.of("0:0", "0:1", "1:0", "1:1"), paths.stream().map(steps ->
        steps.get(steps.size() - 2).ordinal() + ":" + steps.getLast().ordinal()).toList());
    assertTrue(paths.stream().allMatch(steps -> steps.getLast().role() == Role.ANNOTATION));
    assertEquals(List.of(), input.diagnostics());
    var array = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_CREATION).findFirst().orElseThrow();
    assertEquals(new SyntaxValue.ArrayCreation(true, false, List.of(2, 2)), array.value());
    assertEquals(4, array.children(SyntaxRole.ANNOTATION).size());
  }

  @Test
  void givenFrozenRecursiveObligation_whenItsOutcomeIsOmitted_thenFactoryRejectsEqualRemainingEvidence() throws IOException {
    // Arrange
    var input = analyze(RECURSIVE_SOURCE);
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var selected = recursiveOutcomes(input).getLast();
    var outcomes = evidence.outcomes().stream().filter(outcome -> !outcome.equals(selected)).toList();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> result(input, outcomes));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", failure.getMessage());
  }

  @Test
  void givenRecursiveTypeUse_whenAncestorDeclarationReplacesItsFact_thenFactoryRejectsBorrowedSupport() throws IOException {
    // Arrange
    var input = analyze(RECURSIVE_SOURCE);
    var selected = recursiveOutcomes(input).getLast();
    var declaration = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().partition() == Partition.TREE && outcome.occurrence().orElseThrow().classification().feature()
            .identifier().equals("VARIABLE")).findFirst().orElseThrow().support().facts().getFirst();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> replacedSupport(input, selected, List.of(declaration)));

    // Assert
    assertEquals("Written type-use support must retain its exact source kind, owner path and origin", failure.getMessage());
  }

  @Test
  void givenFrozenRecursiveOwners_whenOnlyActualAssociationRoleChanges_thenInputRejectsTheDifferentActualValue() throws IOException {
    // Arrange
    var input = analyze(RECURSIVE_SOURCE);
    var selected = recursiveOutcomes(input).getLast().support().facts().getFirst();
    var aggregate = changedAssociations(input, association -> association.nodeKey().equals(selected.identity())
        ? association(association, replaceLast(association.ownerPath(), Role.TYPE_NAME, 0)) : association);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedInput(input, aggregate));

    // Assert
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", failure.getMessage());
  }

  @Test
  void givenFrozenRecursiveOwners_whenOnlyActualAssociationOrdinalChanges_thenInputRejectsTheDifferentActualValue() throws IOException {
    // Arrange
    var input = analyze(RECURSIVE_SOURCE);
    var selected = recursiveOutcomes(input).stream()
        .filter(outcome -> outcome.address().ownerPath().lastStep().orElseThrow().role() == Role.TYPE_ARGUMENT).findFirst().orElseThrow();
    var aggregate = changedAssociations(input, association -> association.nodeKey().equals(selected.support().facts().getFirst().identity())
        ? association(association, replaceLast(association.ownerPath(), Role.TYPE_ARGUMENT, 99)) : association);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedInput(input, aggregate));

    // Assert
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", failure.getMessage());
  }

  @Test
  void givenFrozenRecursiveTypeUse_whenClaimedAssociationRoleChanges_thenFactoryRejectsTheWrongCapturedParent() throws IOException {
    // Arrange
    var input = analyze(RECURSIVE_SOURCE);
    var selected = recursiveOutcomes(input).getLast();
    var fact = selected.support().facts().getFirst();
    var source = ((CoverageFactValue.Source) fact.value()).value();
    var original = source.sourceAssociation().orElseThrow();
    var changed = new CoverageSourceFact(source.structuralPath(), source.sourceKind(), source.syntaxKind(), source.type(), source.value(),
        source.children(), source.semanticReference(), Optional.of(new CoverageSourceFact.Association(original.nodeKey(), original.sourceFileKey(),
            replaceLast(original.ownerPath(), Role.TYPE_ARGUMENT, 0), original.sourceKind(), original.origin())));
    var supplied = new CoverageFactReference(fact.kind(), fact.identity(), fact.sourceFileKey(), new CoverageFactValue.Source(changed), fact.origin());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> replacedSupport(input, selected, List.of(supplied)));

    // Assert
    assertEquals("Written type-use support must retain its exact source kind, owner path and origin", failure.getMessage());
  }

  @Test
  void givenSameSpanSharedFieldTypes_whenOnlyActualAssociationsAreSwapped_thenInputRejectsFrozenSupport() throws IOException {
    // Arrange
    var input = analyze("class Example { int first, second; }");
    var associations = input.aggregate().orElseThrow().writtenSourceAssociations().stream()
        .filter(association -> association.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE).toList();
    var swaps = Map.of(associations.getFirst().nodeKey(), associations.getLast().ownerPath(),
        associations.getLast().nodeKey(), associations.getFirst().ownerPath());
    var changed = changedAssociations(input, association -> swaps.containsKey(association.nodeKey())
        ? association(association, swaps.get(association.nodeKey())) : association);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedInput(input, changed));

    // Assert
    assertEquals(associations.getFirst().origin(), associations.getLast().origin());
    assertEquals("Recursive written type support requires its exact actual parent role and ordinal", failure.getMessage());
  }

  @Test
  void givenSameSpanSharedFieldTypes_whenActualAssociationsAreSwappedBeforeReconciliation_thenFalseParentSupportIsRejected() throws IOException {
    // Arrange
    var input = analyze("class Example { int first, second; }");
    var associations = input.aggregate().orElseThrow().writtenSourceAssociations().stream()
        .filter(association -> association.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE).toList();
    var swaps = Map.of(associations.getFirst().nodeKey(), associations.getLast().ownerPath(),
        associations.getLast().nodeKey(), associations.getFirst().ownerPath());
    var changed = changedAssociations(input, association -> swaps.containsKey(association.nodeKey())
        ? association(association, swaps.get(association.nodeKey())) : association);
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var reconciler = new JavaSourceCoverageReconciler(evidence.writtenInventory(), evidence.attributedExpectations(), input.program(),
        Optional.of(changed), new DiagnosticEmissions(evidence.diagnosticEmissions()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> {
      var result = reconciler.reconcileRetained(input.languageFeatureCoverage(), evidence.outcomes());
      new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), Optional.of(changed), result.diagnostics(), result.coverageResult());
    });

    // Assert
    assertTrue(failure.getMessage().contains("parent"), failure::getMessage);
  }

  @Test
  void givenCallerOwnedTypedPathSteps_whenTheyAreMutated_thenTheCapturedRoleAndOrdinalRemainImmutable() {
    // Arrange
    var supplied = new ArrayList<>(List.of(new Step(Role.MEMBER, 3), new Step(Role.DECLARED_TYPE, 0)));
    var path = new SourceOwnerPath(supplied);

    // Act
    supplied.set(0, new Step(Role.RETURN_TYPE, 0));
    supplied.clear();

    // Assert
    assertEquals(List.of(new Step(Role.MEMBER, 3), new Step(Role.DECLARED_TYPE, 0)), path.steps());
    assertEquals(SourceOwnerPath.root().child(Role.MEMBER, 3), path.parent().orElseThrow());
    assertTrue(path.parent().orElseThrow().isAncestorOf(path));
    assertFalse(SourceOwnerPath.root().child(Role.MEMBER, 4).isAncestorOf(path));
  }

  private static SyntaxAnalysisInput analyze(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/topology-check/sources"),
        List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CONFIGURATION, JavaTranslationPolicy.legacyAnalysis());
  }

  private static List<FeatureContextOutcome> recursiveOutcomes(SyntaxAnalysisInput input) {
    return input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().partition() == Partition.WRITTEN_TYPE_USE).toList();
  }

  private static LanguageFeatureCoverageResult result(SyntaxAnalysisInput input, List<FeatureContextOutcome> outcomes) {
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    return CoverageAdmission.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private static LanguageFeatureCoverageResult replacedSupport(SyntaxAnalysisInput input, FeatureContextOutcome selected, List<CoverageFactReference> facts) {
    var outcomes = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new Support(Mode.FACTS, facts, Optional.empty(), Optional.empty(), Optional.empty()), outcome.emissions()) : outcome).toList();
    return result(input, outcomes);
  }

  private static SourceOwnerPath replaceLast(SourceOwnerPath path, Role role, int ordinal) {
    return path.parent().orElseThrow().child(role, ordinal);
  }

  private static WrittenSourceAssociation association(WrittenSourceAssociation association, SourceOwnerPath path) {
    return new WrittenSourceAssociation(association.nodeKey(), association.sourceFileKey(), path, association.sourceKind(), association.origin());
  }

  private static JavaAstAggregate changedAssociations(SyntaxAnalysisInput input, UnaryOperator<WrittenSourceAssociation> change) {
    var aggregate = input.aggregate().orElseThrow();
    var builder = AggregateFixture.builder(input.snapshot());
    for (var root : aggregate.roots()) {
      var nodes = root.descendantsAndSelf();
      builder.add(new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), aggregate.semanticEdges()));
    }
    aggregate.writtenSourceAssociations().stream().map(change).forEach(builder::addWrittenSourceAssociation);
    aggregate.declarationMemberOrders().forEach(builder::addDeclarationMemberOrder);
    aggregate.declarationTypeEvidence().forEach(builder::addDeclarationTypeEvidence);
    aggregate.methodBodyKeys().forEach(key -> {
      var syntax = aggregate.methodSyntax(key).orElseThrow();
      var bodyNodes = syntax.root().descendantsAndSelf();
      var uses = bodyNodes.stream().flatMap(node -> node.children().stream()
          .flatMap(child -> syntax.useDecision(node.nodeId(), child.role(), child.position()).stream())).toList();
      var invocations = bodyNodes.stream().flatMap(node -> syntax.invocationArity(node.nodeId()).stream()).toList();
      var staticAccesses = bodyNodes.stream().flatMap(node -> syntax.staticAccess(node.nodeId()).stream()).toList();
      var concatenations = bodyNodes.stream().flatMap(node -> syntax.stringConcatenation(node.nodeId()).stream()).toList();
      builder.addMethodBody(key, syntax.root(), aggregate.methodBodyOrigin(key).orElseThrow(), uses, invocations, staticAccesses, concatenations);
    });
    return builder.seal();
  }

  private static SyntaxAnalysisInput changedInput(SyntaxAnalysisInput input, JavaAstAggregate aggregate) {
    return new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), Optional.of(aggregate), input.diagnostics(),
        input.languageFeatureCoverageResult());
  }
}
