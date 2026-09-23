package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.junit.jupiter.api.Test;

class JavaAstAggregateTest {
  private static final String CONTENT = "0123456789";

  @Test
  void givenAggregateConstructionBoundary_whenConstructorsAreInspected_thenPublicationIsPrivate() {
    // Arrange
    var aggregateConstructors = JavaAstAggregate.class.getDeclaredConstructors();
    var builderConstructors = JavaAstAggregate.Builder.class.getDeclaredConstructors();

    // Act
    var aggregatePrivate = List.of(aggregateConstructors).stream()
        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    var builderPrivate = List.of(builderConstructors).stream().allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));

    // Assert
    assertTrue(aggregatePrivate);
    assertTrue(builderPrivate);
  }

  @Test
  void givenEmptySnapshot_whenAggregateIsSealed_thenAllQueriesAreEmpty() {
    // Arrange
    var snapshot = snapshot();

    // Act
    var aggregate = JavaAstAggregate.builder(snapshot).seal();

    // Assert
    assertEquals(List.of(), aggregate.roots());
    assertEquals(List.of(), aggregate.nodes());
    assertEquals(List.of(), aggregate.nodeTuples());
    assertEquals(List.of(), aggregate.rangeTuples());
    assertEquals(List.of(), aggregate.structuralEdges());
    assertEquals(List.of(), aggregate.semanticEdges());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.roots().add(root("a", List.of())));
  }

  @Test
  void givenTwoSourcesAndReversedArrival_whenAggregateIsSealed_thenRootsFollowSnapshotOrder() {
    // Arrange
    var snapshot = snapshot("a", "b");
    var first = root("a", List.of());
    var second = root("b", List.of());

    // Act
    var aggregate = JavaAstAggregate.builder(snapshot).add(contribution(second)).add(contribution(first)).seal();

    // Assert
    assertEquals(List.of(first.nodeId(), second.nodeId()), aggregate.orderedNodeKeys());
    assertEquals(List.of(first, second), aggregate.roots());
    assertEquals(List.of(first.nodeTuple(), second.nodeTuple()), aggregate.nodeTuples());
    assertEquals(List.of(first.rangeTuple().orElseThrow(), second.rangeTuple().orElseThrow()), aggregate.rangeTuples());
  }

  @Test
  void givenSameFactsInDifferentInsertionOrders_whenSealed_thenCanonicalTuplesAndValuesAreEqual() {
    // Arrange
    var snapshot = snapshot("a", "b");
    var path = SyntaxStructuralPath.sourceFileRoot();
    var statement = leaf("a", path.child(SyntaxRole.STATEMENT, 0));
    var member = leaf("a", path.child(SyntaxRole.MEMBER, 0));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, statement),
        new SyntaxChild(SyntaxRole.MEMBER, 0, member)));
    var secondRoot = root("b", List.of());
    var sourceTarget = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence(member.nodeId()));
    var declarationTarget = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var childTarget = new SyntaxSemanticEdge(statement.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("other")));
    var first = new SyntaxSourceContribution("a", root, root.descendantsAndSelf(), root.structuralEdges(),
        List.of(childTarget, declarationTarget, sourceTarget));
    var reversed = new SyntaxSourceContribution("a", root, List.of(member, statement, root),
        List.of(root.structuralEdges().get(1), root.structuralEdges().get(0)),
        List.of(sourceTarget, declarationTarget, childTarget));

    // Act
    var left = JavaAstAggregate.builder(snapshot).add(first).add(contribution(secondRoot)).seal();
    var right = JavaAstAggregate.builder(snapshot).add(contribution(secondRoot)).add(reversed).seal();

    // Assert
    assertEquals(left, right);
    assertEquals(left.hashCode(), right.hashCode());
    assertEquals(List.of(root.nodeId(), member.nodeId(), statement.nodeId(), secondRoot.nodeId()), left.orderedNodeKeys());
    assertEquals(List.of(root.nodeTuple(), member.nodeTuple(), statement.nodeTuple(), secondRoot.nodeTuple()), left.nodeTuples());
    assertEquals(List.of(root.rangeTuple().orElseThrow(), member.rangeTuple().orElseThrow(), statement.rangeTuple().orElseThrow(),
        secondRoot.rangeTuple().orElseThrow()), left.rangeTuples());
    assertEquals(List.of(root.structuralEdges().get(1), root.structuralEdges().get(0)), left.structuralEdges());
    assertEquals(List.of(sourceTarget, declarationTarget, childTarget), left.semanticEdges());
    assertEquals(left.nodeTuples(), right.nodeTuples());
    assertEquals(left.rangeTuples(), right.rangeTuples());
    assertEquals(left.structuralEdges(), right.structuralEdges());
    assertEquals(left.semanticEdges(), right.semanticEdges());
    assertSame(statement, right.node(statement.nodeId()).orElseThrow());
    assertTrue(right.node("absent").isEmpty());
  }

  @Test
  void givenExplicitSemanticTargets_whenEdgesAreAdded_thenStructuralTuplesRemainIdentical() {
    // Arrange
    var snapshot = snapshot("a");
    var child = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, child)));
    var basic = contribution(root);
    var local = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence(child.nodeId()));
    var external = new SyntaxSemanticEdge(child.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));

    // Act
    var without = JavaAstAggregate.builder(snapshot).add(basic).seal();
    var with = JavaAstAggregate.builder(snapshot).add(new SyntaxSourceContribution("a", root, basic.nodes(),
        basic.structuralEdges(), List.of(external, local))).seal();

    // Assert
    assertEquals(without.structuralEdges(), with.structuralEdges());
    assertEquals(without.nodeTuples(), with.nodeTuples());
    assertEquals(List.of(local, external).stream().sorted(Comparator.comparing(SyntaxSemanticEdge::sourceKey)).toList(),
        with.semanticEdges());
    assertNotEquals(without, with);
  }

  @Test
  void givenMutableCallerCollections_whenAggregateIsSealed_thenMutationCannotChangeOwnedFacts() {
    // Arrange
    var sourceFiles = new ArrayList<>(List.of(new SourceFile("a", Path.of("a.java"), "digest-a", CONTENT)));
    var snapshot = new InputSnapshot(Path.of("sources"), "SHA-256", "snapshot", sourceFiles);
    var child = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var children = new ArrayList<>(List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, child)));
    var root = root("a", children);
    var structuralEdge = root.structuralEdges().getFirst();
    var semanticEdge = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var nodes = new ArrayList<>(List.of(root, child));
    var structural = new ArrayList<>(List.of(structuralEdge));
    var semantic = new ArrayList<>(List.of(semanticEdge));
    var contribution = new SyntaxSourceContribution("a", root, nodes, structural, semantic);
    var expectedChild = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var expectedRoot = root("a", List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, expectedChild)));
    var expected = JavaAstAggregate.builder(snapshot("a")).add(new SyntaxSourceContribution("a", expectedRoot,
        List.of(expectedRoot, expectedChild), expectedRoot.structuralEdges(), List.of(semanticEdge))).seal();

    // Act
    var aggregate = JavaAstAggregate.builder(snapshot).add(contribution).seal();
    sourceFiles.clear();
    children.clear();
    nodes.clear();
    structural.clear();
    structural.add(new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, "absent"));
    semantic.clear();
    semantic.add(new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence("absent")));

    // Assert
    assertEquals(expected, aggregate);
    assertEquals(expected.snapshot().sourceFiles(), aggregate.snapshot().sourceFiles());
    assertEquals(List.of(root), aggregate.roots());
    assertEquals(List.of(root, child), aggregate.nodes());
    assertEquals(List.of(root.nodeTuple(), child.nodeTuple()), aggregate.nodeTuples());
    assertEquals(List.of(root.rangeTuple().orElseThrow(), child.rangeTuple().orElseThrow()), aggregate.rangeTuples());
    assertEquals(List.of(structuralEdge), aggregate.structuralEdges());
    assertEquals(List.of(semanticEdge), aggregate.semanticEdges());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.structuralEdges().add(structural.getFirst()));
    assertThrows(UnsupportedOperationException.class, () -> aggregate.nodes().clear());
  }

  @Test
  void givenDuplicateNodeKey_whenAggregateIsSealed_thenDuplicateIsRejected() {
    // Arrange
    var snapshot = snapshot("a");
    var root = root("a", List.of());
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, root), List.of(), List.of());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> JavaAstAggregate.builder(snapshot).add(contribution).seal());

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST node key: " + root.nodeId()));
  }

  @Test
  void givenMissingStructuralParent_whenAggregateIsSealed_thenEndpointIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxStructuralEdge("absent", SyntaxRole.STATEMENT, 0, root.nodeId());

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(edge), List.of()));

    // Assert
    assertTrue(error.getMessage().contains("structural parent absent"));
  }

  @Test
  void givenMissingStructuralChild_whenAggregateIsSealed_thenEndpointIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, "absent");

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(edge), List.of()));

    // Assert
    assertTrue(error.getMessage().contains("structural child absent"));
  }

  @Test
  void givenMissingSemanticSource_whenAggregateIsSealed_thenEndpointIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxSemanticEdge("absent", SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(), List.of(edge)));

    // Assert
    assertTrue(error.getMessage().contains("semantic source absent"));
  }

  @Test
  void givenDanglingSemanticOccurrence_whenAggregateIsSealed_thenTypedTargetIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence("absent"));

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(), List.of(edge)));

    // Assert
    assertTrue(error.getMessage().contains("semantic target absent"));
  }

  @Test
  void givenTwoStructuralParents_whenAggregateIsSealed_thenChildIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot();
    var first = leaf("a", path.child(SyntaxRole.STATEMENT, 0));
    var second = leaf("a", path.child(SyntaxRole.STATEMENT, 1));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, first),
        new SyntaxChild(SyntaxRole.STATEMENT, 1, second)));
    var edges = new ArrayList<>(root.structuralEdges());
    edges.add(new SyntaxStructuralEdge(second.nodeId(), SyntaxRole.BODY, 0, first.nodeId()));

    // Act
    var error = failure(snapshot("a"), contribution(root, edges, List.of()));

    // Assert
    assertTrue(error.getMessage().contains("exactly one structural parent: " + first.nodeId() + " count 2"));
  }

  @Test
  void givenParentlessNonRoot_whenAggregateIsSealed_thenMissingParentIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var orphan = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, orphan), List.of(), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("exactly one structural parent: " + orphan.nodeId() + " count 0"));
  }

  @Test
  void givenRootWithStructuralParent_whenAggregateIsSealed_thenRootParentIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var other = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var edge = new SyntaxStructuralEdge(other.nodeId(), SyntaxRole.BODY, 0, root.nodeId());
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, other), List.of(edge), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("source root has a structural parent: " + root.nodeId()));
  }

  @Test
  void givenSelfCycle_whenAggregateIsSealed_thenCycleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, root.nodeId());

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(edge), List.of()));

    // Assert
    assertTrue(error.getMessage().contains("structural cycle involves node " + root.nodeId()));
  }

  @Test
  void givenMultiNodeCycle_whenAggregateIsSealed_thenCycleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var first = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var second = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 1));
    var edges = List.of(new SyntaxStructuralEdge(first.nodeId(), SyntaxRole.BODY, 0, second.nodeId()),
        new SyntaxStructuralEdge(second.nodeId(), SyntaxRole.BODY, 0, first.nodeId()));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, first, second), edges, List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("AST structural cycle involves node"));
    assertTrue(error.getMessage().contains(first.nodeId()) || error.getMessage().contains(second.nodeId()));
  }

  @Test
  void givenNegativeRolePosition_whenEdgeIsConstructed_thenParentRoleAndPositionAreReported() {
    // Arrange
    var parent = "parent";

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxStructuralEdge(parent, SyntaxRole.STATEMENT, -1, "child"));

    // Assert
    assertTrue(error.getMessage().contains("parent"));
    assertTrue(error.getMessage().contains("STATEMENT"));
    assertTrue(error.getMessage().contains("-1"));
  }

  @Test
  void givenDuplicateRolePosition_whenAggregateIsSealed_thenParentAndRoleAreReported() {
    // Arrange
    var root = root("a", List.of());
    var first = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var second = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.MEMBER, 0));
    var edges = List.of(new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, first.nodeId()),
        new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, second.nodeId()));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, first, second), edges, List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("parent " + root.nodeId() + " and role STATEMENT"));
  }

  @Test
  void givenGapInRolePositions_whenAggregateIsSealed_thenExpectedPositionIsReported() {
    // Arrange
    var root = root("a", List.of());
    var first = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var second = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 2));
    var edges = List.of(new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, first.nodeId()),
        new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 2, second.nodeId()));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, first, second), edges, List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("parent " + root.nodeId() + " and role STATEMENT: expected 1"));
  }

  @Test
  void givenSourceOutsideSnapshot_whenContributionIsAdded_thenSourceIsRejected() {
    // Arrange
    var builder = JavaAstAggregate.builder(snapshot("a"));
    var unknown = contribution(root("b", List.of()));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder.add(unknown));

    // Assert
    assertTrue(error.getMessage().contains("captured snapshot: b"));
    assertTrue(error.getMessage().contains(unknown.root().nodeId()));
  }

  @Test
  void givenRootOwnedByAnotherSource_whenAggregateIsSealed_thenContributionAndNodeAreReported() {
    // Arrange
    var wrongRoot = root("b", List.of());
    var wrongContribution = new SyntaxSourceContribution("a", wrongRoot, List.of(wrongRoot), List.of(), List.of());

    // Act
    var error = failure(snapshot("a"), wrongContribution);

    // Assert
    assertTrue(error.getMessage().contains("source a"));
    assertTrue(error.getMessage().contains(wrongRoot.nodeId()));
  }

  @Test
  void givenNodeOwnedByAnotherSource_whenAggregateIsSealed_thenContributionAndNodeAreReported() {
    // Arrange
    var first = root("a", List.of());
    var foreign = leaf("b", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var contribution = new SyntaxSourceContribution("a", first, List.of(first, foreign), List.of(), List.of());

    // Act
    var error = failure(snapshot("a", "b"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("contribution a"));
    assertTrue(error.getMessage().contains(foreign.nodeId()));
    assertTrue(error.getMessage().contains("belongs to b"));
  }

  @Test
  void givenStructuralEdgeFiledUnderAnotherSource_whenAggregateIsSealed_thenContributionIsRejected() {
    // Arrange
    var first = root("a", List.of());
    var child = leaf("b", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var second = root("b", List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, child)));
    var edge = second.structuralEdges().getFirst();
    var misfiled = new SyntaxSourceContribution("a", first, List.of(first), List.of(edge), List.of());
    var missing = new SyntaxSourceContribution("b", second, List.of(second, child), List.of(), List.of());

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> JavaAstAggregate.builder(snapshot("a", "b")).add(misfiled).add(missing).seal());

    // Assert
    assertTrue(error.getMessage().contains("structural parent " + second.nodeId()));
    assertTrue(error.getMessage().contains("source b, not contribution a"));
  }

  @Test
  void givenSemanticEdgeFiledUnderAnotherSource_whenAggregateIsSealed_thenContributionIsRejected() {
    // Arrange
    var first = root("a", List.of());
    var second = root("b", List.of());
    var edge = new SyntaxSemanticEdge(second.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var misfiled = new SyntaxSourceContribution("a", first, List.of(first), List.of(), List.of(edge));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> JavaAstAggregate.builder(snapshot("a", "b")).add(misfiled).add(contribution(second)).seal());

    // Assert
    assertTrue(error.getMessage().contains("semantic source " + second.nodeId()));
    assertTrue(error.getMessage().contains("source b, not contribution a"));
  }

  @Test
  void givenCrossSourceStructuralEdge_whenAggregateIsSealed_thenBothOwnersAreReported() {
    // Arrange
    var first = root("a", List.of());
    var second = root("b", List.of());
    var edge = new SyntaxStructuralEdge(first.nodeId(), SyntaxRole.STATEMENT, 0, second.nodeId());
    var firstContribution = contribution(first, List.of(edge), List.of());

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> JavaAstAggregate.builder(snapshot("a", "b")).add(firstContribution).add(contribution(second)).seal());

    // Assert
    assertTrue(error.getMessage().contains("crosses source owners a and b"));
  }

  @Test
  void givenRangeEndBeyondContent_whenAggregateIsSealed_thenNodeAndOffsetsAreReported() {
    // Arrange
    var root = SyntaxNode.source("a", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("COMPILATION_UNIT"), List.of(),
        Optional.empty(), new SourceSpan("a", 0, CONTENT.length() + 1));

    // Act
    var error = failure(snapshot("a"), contribution(root));

    // Assert
    assertTrue(error.getMessage().contains("source a for node " + root.nodeId()));
    assertTrue(error.getMessage().contains("[0, 11) of 10"));
  }

  @Test
  void givenNegativeStart_whenRangeIsConstructed_thenOffsetsAreRejected() {
    // Arrange
    var start = -1;

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> new SourceSpan("a", start, 0));

    // Assert
    assertTrue(error.getMessage().contains("a: [-1, 0)"));
  }

  @Test
  void givenEndBeforeStart_whenRangeIsConstructed_thenOffsetsAreRejected() {
    // Arrange
    var end = 1;

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> new SourceSpan("a", 2, end));

    // Assert
    assertTrue(error.getMessage().contains("a: [2, 1)"));
  }

  @Test
  void givenDuplicateStructuralTuple_whenAggregateIsSealed_thenTupleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var child = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var edge = new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, child.nodeId());
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, child), List.of(edge, edge), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST structural edge tuple"));
    assertTrue(error.getMessage().contains(child.nodeId()));
  }

  @Test
  void givenDuplicateSemanticTuple_whenAggregateIsSealed_thenTupleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var contribution = contribution(root, List.of(), List.of(edge, edge));

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST semantic edge tuple"));
  }

  @Test
  void givenMissingSourceRoot_whenAggregateIsSealed_thenCapturedSourceIsReported() {
    // Arrange
    var builder = JavaAstAggregate.builder(snapshot("a", "b")).add(contribution(root("a", List.of())));

    // Act
    var error = assertThrows(IllegalArgumentException.class, builder::seal);

    // Assert
    assertTrue(error.getMessage().contains("Missing AST source root for captured source: b"));
  }

  @Test
  void givenDuplicateSourceContribution_whenSecondIsAdded_thenSourceIsRejected() {
    // Arrange
    var contribution = contribution(root("a", List.of()));
    var builder = JavaAstAggregate.builder(snapshot("a")).add(contribution);

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder.add(contribution));

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST source contribution: a"));
  }

  @Test
  void givenChildWithEqualKeyButDifferentInstance_whenAggregateIsSealed_thenUnownedInstanceIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0);
    var embedded = leaf("a", path);
    var registered = leaf("a", path);
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, embedded)));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, registered), root.structuralEdges(), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("child is not the identical owned node: " + embedded.nodeId()));
  }

  @Test
  void givenDifferentEmbeddedReference_whenAggregatesAreCompared_thenNodeFactsRemainDistinct() {
    // Arrange
    var snapshot = snapshot("a");
    var plain = root("a", List.of());
    var reference = reference("external");
    var linked = SyntaxNode.source("a", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("COMPILATION_UNIT"), List.of(),
        Optional.of(reference), new SourceSpan("a", 0, CONTENT.length()));
    var edge = linked.semanticEdges().getFirst();

    // Act
    var left = JavaAstAggregate.builder(snapshot).add(contribution(plain, List.of(), List.of(edge))).seal();
    var right = JavaAstAggregate.builder(snapshot).add(contribution(linked)).seal();

    // Assert
    assertEquals(left.semanticEdges(), right.semanticEdges());
    assertNotEquals(left, right);
  }

  @Test
  void givenSourceBody_whenRegistered_thenMethodViewResolvesIdenticalOwnedOccurrence() {
    // Arrange
    var body = block("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, body)));

    // Act
    var aggregate = JavaAstAggregate.builder(snapshot("a")).add(contribution(root))
        .addMethodBody("method", body, MethodBodyOrigin.SOURCE_AST).seal();

    // Assert
    assertSame(body, aggregate.methodSyntax("method").orElseThrow().root());
    assertSame(body, aggregate.node(body.nodeId()).orElseThrow());
    assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin("method").orElseThrow());
  }

  @Test
  void givenAuxiliaryBody_whenRegistered_thenSourceAstQueriesExcludeItsNodes() {
    // Arrange
    var root = root("a", List.of());
    var body = block("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));

    // Act
    var aggregate = JavaAstAggregate.builder(snapshot("a")).add(contribution(root))
        .addMethodBody("method", body, MethodBodyOrigin.EXECUTION_PROJECTION).seal();

    // Assert
    assertSame(body, aggregate.methodSyntax("method").orElseThrow().root());
    assertTrue(aggregate.node(body.nodeId()).isEmpty());
    assertEquals(List.of(root), aggregate.nodes());
    assertEquals(MethodBodyOrigin.EXECUTION_PROJECTION, aggregate.methodBodyOrigin("method").orElseThrow());
  }

  @Test
  void givenUnownedSourceBody_whenRegisteredAsSource_thenSealRejectsIt() {
    // Arrange
    var root = root("a", List.of());
    var body = block("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> JavaAstAggregate.builder(snapshot("a"))
        .add(contribution(root)).addMethodBody("method", body, MethodBodyOrigin.SOURCE_AST).seal());

    // Assert
    assertTrue(error.getMessage().contains("identical aggregate occurrence: method"));
  }

  private static IllegalArgumentException failure(InputSnapshot snapshot, SyntaxSourceContribution contribution) {
    return assertThrows(IllegalArgumentException.class, () -> JavaAstAggregate.builder(snapshot).add(contribution).seal());
  }

  private static InputSnapshot snapshot(String... sourceKeys) {
    var files = new ArrayList<SourceFile>();
    for (var key : sourceKeys) {
      files.add(new SourceFile(key, Path.of(key + ".java"), "digest-" + key, CONTENT));
    }
    return new InputSnapshot(Path.of("sources"), "SHA-256", "snapshot", files);
  }

  private static SyntaxNode root(String sourceKey, List<SyntaxChild> children) {
    return SyntaxNode.source(sourceKey, SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("COMPILATION_UNIT"), children,
        Optional.empty(), new SourceSpan(sourceKey, 0, CONTENT.length()));
  }

  private static SyntaxNode leaf(String sourceKey, SyntaxStructuralPath path) {
    return SyntaxNode.source(sourceKey, path, JavaSourceKind.EMPTY_STATEMENT, SyntaxKind.EMPTY,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(),
        new SourceSpan(sourceKey, 1, 2));
  }

  private static SyntaxNode block(String sourceKey, SyntaxStructuralPath path) {
    return SyntaxNode.source(sourceKey, path, JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(),
        new SourceSpan(sourceKey, 1, 2));
  }

  private static SyntaxSourceContribution contribution(SyntaxNode root) {
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = nodes.stream().flatMap(node -> node.semanticEdges().stream()).toList();
    return new SyntaxSourceContribution(root.sourceFileKey(), root, nodes, structural, semantic);
  }

  private static SyntaxSourceContribution contribution(SyntaxNode root, List<SyntaxStructuralEdge> structural,
      List<SyntaxSemanticEdge> semantic) {
    return new SyntaxSourceContribution(root.sourceFileKey(), root, root.descendantsAndSelf(), structural, semantic);
  }

  private static SemanticReference reference(String key) {
    return new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(),
        List.of(), List.of());
  }
}
