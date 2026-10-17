package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScopedSourcePublicationTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final JavaTranslationPolicy POLICY = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
  @TempDir Path directory;

  @Test
  void givenAcceptedDescendantsInRefusedParent_whenSiblingStarts_thenHeaderOwnershipAndReferencesRemainLexicallyIsolated() {
    // Arrange
    var root = new JavaDeclarationScope();
    var range = new SourceSpan("source", 0, 1);
    root.fieldAnnotations("header").retain(range, "original");
    var parent = root.child();
    var child = parent.child();

    // Act
    child.fieldAnnotations("header").retain(range, "provisional");
    child.localAnnotations("local").retain(range, "local-child");
    child.accept();
    var inside = parent.child();
    var sharedField = inside.fieldAnnotations("header").find(range);
    var sharedLocal = inside.localAnnotations("local").find(range);
    inside.accept();
    parent.refuse();
    var sibling = root.child();

    // Assert
    assertEquals("provisional", sharedField);
    assertEquals("local-child", sharedLocal);
    assertEquals("original", sibling.fieldAnnotations("header").find(range));
    assertNull(sibling.localAnnotations("local").find(range));
    assertThrows(IllegalStateException.class, parent::accept);
    assertThrows(IllegalStateException.class, child::accept);
    assertThrows(IllegalStateException.class, parent::child);
    var accepted = root.child();
    var detachedHeader = accepted.fieldAnnotations("header");
    detachedHeader.retain(range, "accepted");
    accepted.accept();
    detachedHeader.retain(range, "later-alias");
    assertEquals("accepted", root.child().fieldAnnotations("header").find(range));
  }

  @Test
  void givenEqualValuedDistinctOccurrencesAndScopedCopyChains_whenParentsAcceptOrRefuse_thenOnlyAcceptedInstancesResolve() throws IOException {
    // Arrange
    withCompilation("class Sample {}", fixture -> {
      var original = empty(fixture.source(), 0);
      var equal = empty(fixture.source(), 0);
      var copied = empty(fixture.source(), 1);
      var finalCopy = empty(fixture.source(), 2);
      var root = new JavaSourceCopyCorrespondence();
      var parent = root.child();
      var child = parent.child();
      var associations = new JavaWrittenSourceAssociations(fixture.compilation().treeInventory());
      var stagedAssociations = associations.child();
      var descendant = stagedAssociations.child();
      var association = new WrittenSourceAssociation(original.nodeId(), original.sourceFileKey(), new SourceOwnerPath(List.of()),
          original.sourceKind(), original.origin());

      // Act
      child.retain(original, copied);
      child.accept();
      parent.retain(copied, finalCopy);
      parent.retain(finalCopy, finalCopy);
      descendant.retain(original, association);
      descendant.copied(original, copied);
      descendant.accept();

      // Assert
      assertEquals(original, equal);
      assertNotSame(original, equal);
      assertTrue(parent.contains(original));
      assertFalse(parent.contains(equal));
      assertSame(finalCopy, parent.finalCopy(original));
      assertSame(equal, parent.finalCopy(equal));
      assertTrue(parent.scopeCopies(equal, true).isEmpty());
      assertSame(equal, parent.scopeCopies(equal, false).get(equal.nodeId()));
      assertSame(original, root.child().finalCopy(original));
      assertTrue(associations.forNodes(List.of(original, copied)).isEmpty());
      assertEquals(2, stagedAssociations.forNodes(List.of(original, copied)).size());
      assertTrue(stagedAssociations.forNodes(List.of(equal)).isEmpty());
      assertSame(equal, stagedAssociations.copied(equal, equal));
      assertTrue(stagedAssociations.forNodes(List.of(equal)).isEmpty());
      parent.accept();
      stagedAssociations.accept();
      assertSame(finalCopy, root.child().finalCopy(original));
      assertEquals(2, associations.forNodes(List.of(original, copied)).size());
      var refused = root.child();
      refused.retain(equal, copied);
      assertSame(equal, root.child().finalCopy(equal));
      assertSame(finalCopy, root.finalCopy(original));
    });
  }

  @Test
  void givenEnclosingRefusalAfterRecursiveStaging_whenHealthySiblingIsFinalized_thenAbsentOwnersPublishNoCanonicalPayload() throws IOException {
    // Arrange
    var text = """
        import java.lang.annotation.*;
        @Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Mark {}
        class Refused {
          @Mark int first = 1, second = 2;
          class Nested { int field = 3; int value() { return field; } }
          Object item = new Object() { int value() { return 4; } };
          { int number = Math.abs(-5); }
          int value() {
            @Mark int first = 6, second = 7;
            class Local { int value() { return first; } }
            return new Local().value();
          }
          int broken;
        }
        class Healthy {
          @Mark int first = 8, second = 9;
          int value() { @Mark int first = 10, second = 11; return first + second; }
        }
        """;
    withCompilation(text, fixture -> {
      var actual = fixture.compilation().sourceEvidence();
      var evidence = new JavaSourceEvidence(new SourcePositions() {
        @Override public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          return tree instanceof VariableTree variable && variable.getName().contentEquals("broken")
              ? Diagnostic.NOPOS : actual.rawStart(unit, tree);
        }
        @Override public long getEndPosition(CompilationUnitTree unit, Tree tree) { return actual.rawEnd(unit, tree); }
      });
      var factory = new JavaUnitTranslationFactory(fixture.compilation().semantics(), evidence, fixture.compilation().treeInventory(), POLICY);
      var coordinator = factory.create(fixture.compilation().units().getFirst(), fixture.source());

      // Act
      var declarations = coordinator.translateDeclarations();
      coordinator.decideProjection(JavaUnitTranslator.ProjectionDecision.ALLOW);
      coordinator.projectSource();
      var result = coordinator.finalizeSource();
      var contribution = result.contribution().orElseThrow();
      var healthy = contribution.root().children(SyntaxRole.TYPE_DECLARATION).stream()
          .filter(node -> node.value(SyntaxValue.TypeDeclaration.class).name().equals("Healthy")).findFirst().orElseThrow();

      // Assert
      assertTrue(declarations.value().complete());
      assertFalse(fixture.compilation().diagnostics().stream().anyMatch(value -> value.getKind() == Diagnostic.Kind.ERROR));
      assertEquals(List.of("Mark", "Healthy"), contribution.root().children(SyntaxRole.TYPE_DECLARATION).stream()
          .map(node -> node.value(SyntaxValue.TypeDeclaration.class).name()).toList());
      assertEquals(List.of("frontend.missing-source-position"), result.diagnostics().stream().map(value -> value.code()).toList());
      assertTrue(result.declarationTypes().stream().noneMatch(value -> value.ownerTypeKey().contains("Refused")));
      assertTrue(result.memberOrders().stream().noneMatch(value -> value.ownerTypeKey().contains("Refused")));
      assertTrue(result.scopes().stream().noneMatch(value -> value.scope().key().contains("Refused")));
      assertTrue(contribution.semanticEdges().stream().allMatch(edge -> contribution.nodes().stream()
          .anyMatch(node -> node.nodeId().equals(edge.sourceKey()))));
      var refusedBodies = result.bodies().stream().filter(body -> body.methodKey().contains("Refused")).toList();
      assertFalse(refusedBodies.isEmpty());
      assertTrue(refusedBodies.stream().allMatch(body -> body.origin() == MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY
          || body.origin() == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION));
      var method = healthy.children(SyntaxRole.MEMBER).stream().filter(node -> node.syntaxKind() == SyntaxKind.METHOD_DECLARATION)
          .findFirst().orElseThrow();
      var body = result.bodies().stream().filter(value -> value.methodKey().equals(
          method.value(SyntaxValue.ExecutableDeclaration.class).declarationKey())).findFirst().orElseThrow();
      assertEquals(MethodBodyOrigin.SOURCE_AST, body.origin());
      assertSame(method.requiredChild(SyntaxRole.BODY), body.root());
      var fields = healthy.children(SyntaxRole.MEMBER).stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION).toList();
      assertEquals(1, fields.getFirst().children(SyntaxRole.ANNOTATION).size());
      assertTrue(fields.getLast().children(SyntaxRole.ANNOTATION).isEmpty());
      assertTrue(result.sourceAssociations().stream().allMatch(value -> contribution.nodes().stream()
          .anyMatch(node -> node.nodeId().equals(value.nodeKey()))));
      for (var scope : result.scopes()) {
        assertSame(contribution.nodes().stream().filter(node -> node.nodeId().equals(scope.root().nodeId())).findFirst().orElseThrow(), scope.root());
      }
    });
  }

  private static SyntaxNode empty(SourceFile source, int position) {
    return SyntaxNode.source(source.sourceFileKey(), SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, position),
        JavaSourceKind.EMPTY_STATEMENT, SyntaxKind.EMPTY, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(),
        Optional.empty(), new SourceSpan(source.sourceFileKey(), 0, 1));
  }

  private void withCompilation(String text, Observation observation) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), text)));
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      observation.observe(new Fixture(snapshot.sourceFiles().getFirst(), session.analyze(session.parse(), POLICY)));
    }
  }

  @FunctionalInterface
  private interface Observation { void observe(Fixture fixture) throws IOException; }
  private record Fixture(SourceFile source, JavaCompilation compilation) {}
}
