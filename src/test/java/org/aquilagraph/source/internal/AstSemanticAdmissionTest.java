package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;




import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AstSemanticAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenContradictorySelectedEdges_whenAdmittingOrDecodingCopiedValues_thenEverySelectedAccessIsRejected() throws IOException {
    // Arrange
    var ast = fixture();
    var selectedKinds = List.of(SyntaxKind.INVOCATION, SyntaxKind.OBJECT_CREATION, SyntaxKind.FIELD_ACCESS, SyntaxKind.VARIABLE_ACCESS);

    for (var kind : selectedKinds) {
      var node = ast.aggregate().nodes().stream().filter(value -> value.syntaxKind() == kind).findFirst().orElseThrow();
      var selected = node.requiredReference();
      var conflicting = new SemanticReference(selected.referenceKind(), selected.status(), Optional.of("different-selected-declaration"),
          selected.typeName(), selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
      var edge = new SyntaxSemanticEdge(node.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
          new SyntaxSemanticTarget.Declaration(conflicting));
      var supplied = withEdge(ast, edge);

      // Act and assert
      var direct = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(supplied), kind.name());
      assertTrue(direct.getMessage().contains("selected target"), direct.toString());
      var decoded = assertThrows(IllegalArgumentException.class, () -> decode(supplied), kind.name());
      assertTrue(decoded.getMessage().contains("selected target"), decoded.toString());
    }
  }

  @Test
  void givenOwnedNondeclarationTarget_whenAdmittingOrDecoding_thenSemanticTargetCategoryIsRejected() throws IOException {
    // Arrange
    var ast = fixture();
    var literal = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).findFirst().orElseThrow();
    var edge = new SyntaxSemanticEdge(ast.aggregate().roots().getFirst().nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence(literal.nodeId()));
    var supplied = withEdge(ast, edge);

    // Act and assert
    var direct = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(supplied));
    assertTrue(direct.getMessage().contains("declaration occurrence"), direct.toString());
    var decoded = assertThrows(IllegalArgumentException.class, () -> decode(supplied));
    assertTrue(decoded.getMessage().contains("declaration occurrence"), decoded.toString());
  }

  @Test
  void givenDeclarationTargetForAnnotationRelation_whenConstructing_thenTypedRelationRejectsWrongVariant() throws IOException {
    // Arrange
    var ast = fixture();
    var selected = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .findFirst().orElseThrow();

    // Act and assert
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxSemanticEdge(selected.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
            new SyntaxSemanticTarget.Declaration(selected.requiredReference())));
    assertTrue(failure.getMessage().contains("target variant"));
  }

  @Test
  void givenAuxiliaryOwnedDeclarationReference_whenAdmittingAndDecoding_thenSeparateSemanticRelationSurvives() throws IOException {
    // Arrange
    var ast = fixture();
    var declaration = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION)
        .findFirst().orElseThrow();
    var edge = new SyntaxSemanticEdge(ast.aggregate().roots().getFirst().nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence(declaration.nodeId()));
    var supplied = withEdge(ast, edge);

    // Act
    var direct = AstValueAdmission.admit(supplied);
    var decoded = decode(supplied);

    // Assert
    assertTrue(direct.semanticEdges().contains(edge));
    assertEquals(direct, decoded);
    assertEquals(ast.aggregate().structuralEdges(), direct.structuralEdges());
  }

  private AstResult fixture() throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { int field; int called() { return 1; } int value(int x) { return called() + field + x + new Example().called(); } }")));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED);
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }

  private static CopiedAstValues withEdge(AstResult ast, SyntaxSemanticEdge edge) {
    var original = SourceValueCapture.capture(ast.aggregate());
    var source = original.sources().getFirst();
    var edges = new ArrayList<>(source.semanticEdges());
    edges.add(edge);
    var supplied = new SyntaxSourceContribution(source.sourceFileKey(), source.root(), source.nodes(), source.structuralEdges(), edges);
    return new CopiedAstValues(original.snapshot(), List.of(supplied), original.bodies(), original.memberOrders(), original.associations(),
        original.declarationTypes(), original.scopes());
  }

  private static Object decode(CopiedAstValues values) {
    var writer = new CanonicalValueWriter("retained-source-values/1");
    writer.text("source-aggregate");
    writer.count(1);
    writer.text("copiedValues");
    new RetainedValueCodec(false).write(writer, values);
    return new RetainedValueCodec(false).read(new CanonicalValueReader(writer.toByteArray(), "retained-source-values/1"));
  }
}
