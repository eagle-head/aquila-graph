package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxNodeTuple;
import org.aquilagraph.source.api.SyntaxRangeTuple;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxStructuralEdge;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;

class JavaAstContractTest {
  private static final String SOURCE_FILE_KEY = "source-file";
  private static final SourceSpan LITERAL_RANGE = new SourceSpan(SOURCE_FILE_KEY, 10, 11);
  private static final SyntaxType INT_TYPE = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"));

  @Test
  void givenJava25TreeKinds_whenDurableKindContractIsInspected_thenExactClosedUniverseAndBoundariesMatch() {
    // Arrange
    var compilerKinds = Arrays.stream(Tree.Kind.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    // Act
    var durableKinds = Arrays.stream(JavaSourceKind.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    // Assert
    assertEquals(compilerKinds, durableKinds);
    assertEquals(Tree.Kind.values().length, JavaSourceKind.values().length);
    assertEquals(JavaSourceKind.ANNOTATED_TYPE, JavaSourceKind.values()[0]);
    assertEquals(JavaSourceKind.YIELD, JavaSourceKind.values()[JavaSourceKind.values().length - 1]);
  }

  @Test
  void givenExactOccurrenceFacts_whenNodeIsConstructed_thenFactsAndLengthFramedKeyAreExact() {
    // Arrange
    var path = methodBodyPath().child(SyntaxRole.STATEMENT, 2).child(SyntaxRole.EXPRESSION, 0);
    var expectedKey = StableId.sha256("syntax-node", SOURCE_FILE_KEY,
        SyntaxRole.SOURCE_FILE_ROOT.stableId(), "0",
        SyntaxRole.TYPE_DECLARATION.stableId(), "1",
        SyntaxRole.MEMBER.stableId(), "3",
        SyntaxRole.BODY.stableId(), "0",
        SyntaxRole.STATEMENT.stableId(), "2",
        SyntaxRole.EXPRESSION.stableId(), "0",
        JavaSourceKind.INT_LITERAL.stableId());

    // Act
    var node = literal(path);

    // Assert
    assertEquals(expectedKey, node.nodeId());
    assertEquals(SOURCE_FILE_KEY, node.sourceFileKey());
    assertEquals(path, node.structuralPath());
    assertEquals(JavaSourceKind.INT_LITERAL, node.sourceKind());
    assertEquals(new SyntaxNodeTuple(expectedKey, SOURCE_FILE_KEY, path, JavaSourceKind.INT_LITERAL), node.nodeTuple());
    assertEquals(Optional.of(new SyntaxRangeTuple(expectedKey, LITERAL_RANGE)), node.rangeTuple());
  }

  @Test
  void givenTypedStructuralAndSemanticRelations_whenCompared_thenRolesAndEndpointCategoriesRemainDistinct() {
    // Arrange
    var parent = literal(methodBodyPath().child(SyntaxRole.STATEMENT, 0));
    var child = literal(methodBodyPath().child(SyntaxRole.STATEMENT, 1));
    var localTarget = new SyntaxSemanticTarget.SourceOccurrence(child.nodeId());
    var externalTarget = new SyntaxSemanticTarget.Declaration(typeReference("type:external.Example"));

    // Act
    var expressionEdge = new SyntaxStructuralEdge(parent.nodeId(), SyntaxRole.EXPRESSION, 0, child.nodeId());
    var valueEdge = new SyntaxStructuralEdge(parent.nodeId(), SyntaxRole.VALUE, 0, child.nodeId());
    var localEdge = new SyntaxSemanticEdge(parent.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE, localTarget);
    var externalEdge = new SyntaxSemanticEdge(parent.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE, externalTarget);

    // Assert
    assertNotEquals(expressionEdge, valueEdge);
    assertNotEquals(localEdge, externalEdge);
    assertFalse(expressionEdge.equals(localEdge));
    assertEquals(new SyntaxSemanticTarget.SourceOccurrence(child.nodeId()), localEdge.target());
    assertEquals(new SyntaxSemanticTarget.Declaration(typeReference("type:external.Example")), externalEdge.target());
  }

  @Test
  void givenSameNodeSet_whenSemanticReferenceIsAdded_thenExactStructuralTuplesRemainUnchanged() {
    // Arrange
    var expression = literal(methodBodyPath().child(SyntaxRole.STATEMENT, 0).child(SyntaxRole.EXPRESSION, 0));
    var path = methodBodyPath().child(SyntaxRole.STATEMENT, 0);
    var children = List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, expression));
    var withoutReference = SyntaxNode.source(SOURCE_FILE_KEY, path, JavaSourceKind.TYPE_CAST, SyntaxKind.TYPE_CAST, INT_TYPE,
        new SyntaxValue.TypeName("int"), children, Optional.empty(), new SourceSpan(SOURCE_FILE_KEY, 5, 11));
    var reference = typeReference("type:int");

    // Act
    var withReference = SyntaxNode.source(SOURCE_FILE_KEY, path, JavaSourceKind.TYPE_CAST, SyntaxKind.TYPE_CAST, INT_TYPE,
        new SyntaxValue.TypeName("int"), children, Optional.of(reference), new SourceSpan(SOURCE_FILE_KEY, 5, 11));

    // Assert
    assertEquals(withoutReference.structuralEdges(), withReference.structuralEdges());
    assertEquals(List.of(), withoutReference.semanticEdges());
    assertEquals(List.of(new SyntaxSemanticEdge(withReference.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference))), withReference.semanticEdges());
  }

  @Test
  void givenMutablePathAndChildren_whenInputsAreReordered_thenConstructedOccurrenceRemainsUnchanged() {
    // Arrange
    var pathSteps = new ArrayList<>(methodBodyPath().steps());
    pathSteps.add(new SyntaxStructuralPath.Step(SyntaxRole.STATEMENT, 0));
    var child = SyntaxNode.source(SOURCE_FILE_KEY,
        new SyntaxStructuralPath(pathSteps).child(SyntaxRole.STATEMENT, 0), JavaSourceKind.EMPTY_STATEMENT, SyntaxKind.EMPTY,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), LITERAL_RANGE);
    var children = new ArrayList<>(List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, child)));
    var node = SyntaxNode.source(SOURCE_FILE_KEY, new SyntaxStructuralPath(pathSteps), JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, children, Optional.empty(),
        new SourceSpan(SOURCE_FILE_KEY, 0, 12));
    var expectedEdges = node.structuralEdges();
    var expectedPath = node.structuralPath();

    // Act
    pathSteps.clear();
    children.clear();

    // Assert
    assertEquals(expectedPath, node.structuralPath());
    assertEquals(expectedEdges, node.structuralEdges());
    assertEquals(1, node.children().size());
  }

  @Test
  void givenInvalidBoundaryValues_whenDurableValuesAreConstructed_thenInvariantSpecificFailuresAreExact() {
    // Arrange
    var rootPath = SyntaxStructuralPath.sourceFileRoot();

    // Act
    var blankSource = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source(" ", rootPath, JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL, INT_TYPE,
            new SyntaxValue.Literal("1"), List.of(), Optional.empty(), LITERAL_RANGE));
    var negativePosition = assertThrows(IllegalArgumentException.class,
        () -> rootPath.child(SyntaxRole.STATEMENT, -1));
    var blankEndpoint = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxStructuralEdge(" ", SyntaxRole.STATEMENT, 0, "child"));
    var missingEndpoint = assertThrows(NullPointerException.class,
        () -> new SyntaxSemanticEdge("source", SyntaxSemanticRelation.DECLARATION_REFERENCE, null));
    var reservedKind = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source(SOURCE_FILE_KEY, rootPath, JavaSourceKind.OTHER, SyntaxKind.UNSUPPORTED,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("OTHER"), List.of(), Optional.empty(),
            new SourceSpan(SOURCE_FILE_KEY, 0, 0)));

    // Assert
    assertEquals("Source-file key cannot be blank", blankSource.getMessage());
    assertEquals("A syntax structural path position cannot be negative", negativePosition.getMessage());
    assertEquals("Structural parent key cannot be blank", blankEndpoint.getMessage());
    assertEquals("target", missingEndpoint.getMessage());
    assertEquals("Java source kind does not identify source syntax: OTHER", reservedKind.getMessage());
  }

  @Test
  void givenNoncanonicalStructuralPaths_whenConstructed_thenRootInvariantFailuresAreExact() {
    // Arrange
    var misplacedRoot = List.of(new SyntaxStructuralPath.Step(SyntaxRole.STATEMENT, 0));
    var repeatedRoot = List.of(
        new SyntaxStructuralPath.Step(SyntaxRole.SOURCE_FILE_ROOT, 0),
        new SyntaxStructuralPath.Step(SyntaxRole.SOURCE_FILE_ROOT, 0));

    // Act
    var emptyPath = assertThrows(IllegalArgumentException.class, () -> new SyntaxStructuralPath(List.of()));
    var misplacedRootPath = assertThrows(IllegalArgumentException.class, () -> new SyntaxStructuralPath(misplacedRoot));
    var repeatedRootPath = assertThrows(IllegalArgumentException.class, () -> new SyntaxStructuralPath(repeatedRoot));

    // Assert
    assertEquals("A syntax structural path must begin with source-file root position 0", emptyPath.getMessage());
    assertEquals("A syntax structural path must begin with source-file root position 0", misplacedRootPath.getMessage());
    assertEquals("A syntax structural path cannot contain a second source-file root", repeatedRootPath.getMessage());
  }

  @Test
  void givenDurableKindVocabulary_whenNamesAreInspected_thenNoNormalizedOrGraphReplacementKindExists() {
    // Arrange
    var prohibitedNames = Set.of("CALL", "CFG_NODE", "BYTECODE_INSTRUCTION", "SSA_VALUE", "TAC_OPERATION", "NORMALIZED_CALL");

    // Act
    var actualNames = EnumSet.allOf(JavaSourceKind.class).stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    // Assert
    assertEquals(Set.of(), actualNames.stream().filter(prohibitedNames::contains).collect(Collectors.toUnmodifiableSet()));
  }

  private static SyntaxStructuralPath methodBodyPath() {
    return SyntaxStructuralPath.sourceFileRoot()
        .child(SyntaxRole.TYPE_DECLARATION, 1)
        .child(SyntaxRole.MEMBER, 3)
        .child(SyntaxRole.BODY, 0);
  }

  private static SyntaxNode literal(SyntaxStructuralPath path) {
    return SyntaxNode.source(SOURCE_FILE_KEY, path, JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL, INT_TYPE,
        new SyntaxValue.Literal("1"), List.of(), Optional.empty(), LITERAL_RANGE);
  }

  private static SemanticReference typeReference(String key) {
    return new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(key), Optional.of("int"),
        List.of("int"), List.of("int"), Optional.empty());
  }
}
