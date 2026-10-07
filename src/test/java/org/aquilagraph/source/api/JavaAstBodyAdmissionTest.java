package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.Test;

class JavaAstBodyAdmissionTest {
  private static final String CONTENT = "0123456789";
  private static final String METHOD_KEY = "type:Type#Type()";
  private static final SyntaxStructuralPath BODY_PATH = SyntaxStructuralPath.sourceFileRoot()
      .child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0).child(SyntaxRole.BODY, 0);

  @Test
  void givenOwnedBodyWithAnotherMethodKey_whenSealed_thenExecutableCorrespondenceIsRejected() {
    // Arrange
    var body = block("a", BODY_PATH, List.of(), Optional.of(new SourceSpan("a", 1, 2)));
    var sourceRoot = executableRoot(List.of(body));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(sourceRoot)
        .addMethodBody("type:Type#other()", body, MethodBodyOrigin.SOURCE_AST).seal());

    // Assert
    assertTrue(error.getMessage().contains("unique executable declaration"));
  }

  @Test
  void givenOwnedNestedBlock_whenRegisteredAsExecutableBody_thenExactBodyAttachmentIsRejected() {
    // Arrange
    var nested = block("a", BODY_PATH.child(SyntaxRole.STATEMENT, 0), List.of(), Optional.of(new SourceSpan("a", 1, 2)));
    var body = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)),
        Optional.of(new SourceSpan("a", 1, 2)));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(executableRoot(List.of(body)))
        .addMethodBody(METHOD_KEY, nested, MethodBodyOrigin.SOURCE_AST).seal());

    // Assert
    assertTrue(error.getMessage().contains("unique executable declaration"));
  }

  @Test
  void givenDuplicateSourceExecutableKeys_whenBodyIsRegistered_thenUniqueDeclarationIsRejected() {
    // Arrange
    var first = block("a", BODY_PATH, List.of(), Optional.of(new SourceSpan("a", 1, 2)));
    var secondPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 1).child(SyntaxRole.BODY, 0);
    var second = block("a", secondPath, List.of(), Optional.of(new SourceSpan("a", 1, 2)));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(executableRoot(List.of(first, second)))
        .addMethodBody(METHOD_KEY, first, MethodBodyOrigin.SOURCE_AST).seal());

    // Assert
    assertTrue(error.getMessage().contains("unique executable declaration"));
  }

  @Test
  void givenDistinctAuxiliaryRootSharingSourceDescendant_whenSealed_thenWholeTreeOwnershipIsRejected() {
    // Arrange
    var nested = block("a", BODY_PATH.child(SyntaxRole.STATEMENT, 0), List.of(), Optional.of(new SourceSpan("a", 1, 2)));
    var sourceBody = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)),
        Optional.of(new SourceSpan("a", 1, 2)));
    var auxiliary = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(executableRoot(List.of(sourceBody)))
        .addMethodBody(METHOD_KEY, auxiliary, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY).seal());

    // Assert
    assertNotSame(sourceBody, auxiliary);
    assertTrue(error.getMessage().contains("cannot also be a source AST occurrence"));
  }

  @Test
  void givenAuxiliaryTreesSharingOnlyDescendant_whenSealed_thenMethodOwnershipIsRejected() {
    // Arrange
    var nested = block("a", BODY_PATH.child(SyntaxRole.STATEMENT, 0), List.of(), Optional.empty());
    var first = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)), Optional.empty());
    var second = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(root("a", List.of()))
        .addMethodBody("first", first, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY)
        .addMethodBody("second", second, MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION).seal());

    // Assert
    assertNotSame(first, second);
    assertTrue(error.getMessage().contains("Auxiliary method body occurrence is shared"));
  }

  @Test
  void givenAuxiliaryRootSharedAcrossMethods_whenSealed_thenRootOwnershipIsRejected() {
    // Arrange
    var body = block("a", BODY_PATH, List.of(), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(root("a", List.of()))
        .addMethodBody("first", body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY)
        .addMethodBody("second", body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY).seal());

    // Assert
    assertTrue(error.getMessage().contains("Method body root is shared"));
  }

  @Test
  void givenDistinctTreesWithEqualNodeKeys_whenSealed_thenLookupsRetainTheirOwnershipScopes() {
    // Arrange
    var sourceNested = block("a", BODY_PATH.child(SyntaxRole.STATEMENT, 0), List.of(), Optional.of(new SourceSpan("a", 1, 2)));
    var sourceBody = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, sourceNested)),
        Optional.of(new SourceSpan("a", 1, 2)));
    var firstNested = block("a", sourceNested.structuralPath(), List.of(), Optional.empty());
    var first = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, firstNested)), Optional.empty());
    var secondNested = block("a", sourceNested.structuralPath(), List.of(), Optional.empty());
    var second = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, secondNested)), Optional.empty());
    var sourceRoot = executableRoot(List.of(sourceBody));

    // Act
    var aggregate = builder(sourceRoot).addMethodBody("first", first, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY)
        .addMethodBody("second", second, MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION).seal();

    // Assert
    assertEquals(sourceBody.nodeId(), first.nodeId());
    assertEquals(firstNested.nodeId(), secondNested.nodeId());
    assertSame(sourceBody, aggregate.node(first.nodeId()).orElseThrow());
    assertSame(sourceNested, aggregate.node(secondNested.nodeId()).orElseThrow());
    assertSame(first, aggregate.methodSyntax("first").orElseThrow().root());
    assertSame(second, aggregate.methodSyntax("second").orElseThrow().root());
    assertEquals(List.of(sourceRoot, sourceRoot.requiredChild(SyntaxRole.TYPE_DECLARATION),
        sourceRoot.requiredChild(SyntaxRole.TYPE_DECLARATION).requiredChild(SyntaxRole.MEMBER), sourceBody, sourceNested), aggregate.nodes());
  }

  @Test
  void givenCapturedForeignSourceInAuxiliaryDescendant_whenSealed_thenRootSourceContinuityIsRejected() {
    // Arrange
    var nested = block("b", BODY_PATH.child(SyntaxRole.STATEMENT, 0), List.of(), Optional.empty());
    var body = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)), Optional.empty());
    var builder = AggregateFixture.builder(snapshot("a", "b")).add(contribution(root("a", List.of())))
        .add(contribution(root("b", List.of())));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder.addMethodBody("method", body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY).seal());

    // Assert
    assertTrue(error.getMessage().contains("descendants must retain their root source"));
  }

  @Test
  void givenAuxiliaryDescendantAtAnotherPath_whenSealed_thenAttachmentPathIsRejected() {
    // Arrange
    var nested = block("a", BODY_PATH.child(SyntaxRole.STATEMENT, 1), List.of(), Optional.empty());
    var body = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(root("a", List.of()))
        .addMethodBody("method", body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY).seal());

    // Assert
    assertTrue(error.getMessage().contains("child path does not match its attachment"));
  }

  @Test
  void givenAuxiliaryDescendantWithOutOfBoundsOrigin_whenSealed_thenCapturedRangeIsRejected() {
    // Arrange
    var nested = block("a", BODY_PATH.child(SyntaxRole.STATEMENT, 0), List.of(), Optional.of(new SourceSpan("a", 1, 11)));
    var body = block("a", BODY_PATH, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, nested)), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(root("a", List.of()))
        .addMethodBody("method", body, MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION).seal());

    // Assert
    assertTrue(error.getMessage().contains("origin is outside its captured source"));
  }

  @Test
  void givenAuxiliaryRootOutsideCapturedSources_whenSealed_thenSourceMembershipIsRejected() {
    // Arrange
    var body = block("foreign", BODY_PATH, List.of(), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder(root("a", List.of()))
        .addMethodBody("method", body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY).seal());

    // Assert
    assertTrue(error.getMessage().contains("source is not in the captured snapshot"));
  }

  @Test
  void givenRegisteredEmptyBody_whenQueried_thenAbsenceAndImmutableOrderedViewsRemainDistinct() {
    // Arrange
    var firstRoot = root("a", List.of());
    var secondRoot = root("b", List.of());
    var body = block("a", BODY_PATH, List.of(), Optional.empty());
    var builder = AggregateFixture.builder(snapshot("a", "b")).add(contribution(secondRoot)).add(contribution(firstRoot))
        .addMethodBody("asserted-method", body, MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION);

    // Act
    var aggregate = builder.seal();

    // Assert
    assertEquals(List.of(firstRoot, secondRoot), aggregate.roots());
    assertEquals(List.of(firstRoot, secondRoot), aggregate.nodes());
    assertSame(body, aggregate.methodSyntax("asserted-method").orElseThrow().root());
    assertTrue(aggregate.methodSyntax("absent-method").isEmpty());
    assertTrue(aggregate.methodBodyOrigin("absent-method").isEmpty());
    assertTrue(aggregate.node(body.nodeId()).isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.nodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.roots().clear());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.structuralEdges().clear());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.semanticEdges().clear());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.methodBodyKeys().clear());
    assertThrows(IllegalStateException.class, builder::seal);
  }

  private static AggregateFixture builder(SyntaxNode sourceRoot) {
    return AggregateFixture.builder(snapshot("a")).add(contribution(sourceRoot));
  }

  private static InputSnapshot snapshot(String... sourceKeys) {
    var files = new ArrayList<SourceFile>();
    for (var key : sourceKeys) {
      files.add(new SourceFile(key, Path.of(key + ".java"), "digest-" + key, CONTENT));
    }
    return new InputSnapshot(Path.of("sources"), "SHA-256", "snapshot", files);
  }

  private static SyntaxNode root(String key, List<SyntaxChild> children) {
    return SyntaxNode.source(key, SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY), children,
        Optional.empty(), new SourceSpan(key, 0, CONTENT.length()));
  }

  private static SyntaxNode executableRoot(List<SyntaxNode> bodies) {
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var methods = new ArrayList<SyntaxChild>();
    for (var index = 0; index < bodies.size(); index++) {
      var value = new SyntaxValue.ExecutableDeclaration("Type", "type:Type", METHOD_KEY,
          SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, Set.of(), Set.of(),
          new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 0, true, false),
          SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty()));
      var method = SyntaxNode.source("a", typePath.child(SyntaxRole.MEMBER, index), JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
          SyntaxType.NotApplicable.INSTANCE, value, List.of(new SyntaxChild(SyntaxRole.BODY, 0, bodies.get(index))), Optional.empty(),
          new SourceSpan("a", 1, 2));
      methods.add(new SyntaxChild(SyntaxRole.MEMBER, index, method));
    }
    var typeValue = new SyntaxValue.TypeDeclaration("Type", "type:Type", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, methods.size()));
    var type = SyntaxNode.source("a", typePath, JavaSourceKind.CLASS, SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        typeValue, methods, Optional.empty(), new SourceSpan("a", 1, 2));
    return root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, type)));
  }

  private static SyntaxNode block(String key, SyntaxStructuralPath path, List<SyntaxChild> children, Optional<SourceSpan> origin) {
    return origin.map(range -> SyntaxNode.source(key, path, JavaSourceKind.BLOCK, SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, children, Optional.empty(), range)).orElseGet(() -> SyntaxNode.withoutDirectSource(key, path,
            JavaSourceKind.BLOCK, SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, children, Optional.empty()));
  }

  private static SyntaxSourceContribution contribution(SyntaxNode root) {
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = nodes.stream().flatMap(node -> {
      var kind = node.syntaxKind() == SyntaxKind.TYPE_DECLARATION ? ReferenceKind.TYPE : ReferenceKind.CONSTRUCTOR;
      var key = node.value() instanceof SyntaxValue.TypeDeclaration declaration
          ? Optional.of(declaration.declarationKey())
          : node.value() instanceof SyntaxValue.ExecutableDeclaration declaration ? Optional.of(declaration.declarationKey()) : Optional.<String>empty();
      return key.stream().map(target -> new SyntaxSemanticEdge(node.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
          new SyntaxSemanticTarget.Declaration(new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(target), Optional.empty(),
              List.of(), List.of()))));
    }).toList();
    return new SyntaxSourceContribution(root.sourceFileKey(), root, nodes, structural, semantic);
  }
}
