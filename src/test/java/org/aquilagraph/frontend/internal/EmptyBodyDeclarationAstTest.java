package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SourceContext;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class EmptyBodyDeclarationAstTest {
  @TempDir Path directory;

  @Test
  void givenInterleavedEmptyClassAndInterfaceDeclarations_whenProducedAndDecoded_thenExactMemberOrderAndRangesRemain() throws IOException {
    // Arrange
    var source = "class Example { ; int first = 1; ; void run() {} ; interface Inner { ; int value = 2; ; void call(); ; } ; }";

    // Act
    var ast = produce(source, AstProfile.JAVA_SOURCE_DECLARATIONS);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var type = retained.aggregate().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
    var members = type.children(SyntaxRole.MEMBER);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(List.of(JavaSourceKind.EMPTY_STATEMENT, JavaSourceKind.VARIABLE, JavaSourceKind.EMPTY_STATEMENT,
        JavaSourceKind.METHOD, JavaSourceKind.EMPTY_STATEMENT, JavaSourceKind.INTERFACE, JavaSourceKind.EMPTY_STATEMENT),
        members.stream().map(node -> node.sourceKind()).toList());
    var inner = members.get(5);
    assertEquals(List.of(JavaSourceKind.EMPTY_STATEMENT, JavaSourceKind.VARIABLE, JavaSourceKind.EMPTY_STATEMENT,
        JavaSourceKind.METHOD, JavaSourceKind.EMPTY_STATEMENT), inner.children(SyntaxRole.MEMBER).stream().map(node -> node.sourceKind()).toList());
    var empty = retained.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.EMPTY_STATEMENT).toList();
    assertEquals(7, empty.size());
    for (var node : empty) {
      var range = node.origin().orElseThrow();
      assertEquals(";", source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())));
      assertTrue(node.children().isEmpty());
      assertTrue(node.semanticReference().isEmpty());
      assertTrue(retained.aggregate().writtenSourceAssociation(node.nodeId()).isPresent());
      assertTrue(retained.aggregate().node(node.nodeId()).isPresent());
    }
    assertEquals(2, retained.program().types().size());
    assertEquals(2, retained.program().types().stream().flatMap(value -> value.methods().stream())
        .filter(method -> method.sourceDeclared()).count());
  }

  @TestFactory
  Stream<DynamicTest> givenEmptyDeclarationsInEveryTypeBody_whenProducedAndDecoded_thenOnlyWrittenBodySemicolonsBecomeMembers() {
    // Arrange
    var sources = List.of("class Example { ; ; }", "interface Example { ; ; }", "@interface Example { ; ; }",
        "record Example(int value) { ; ; }", "enum Example { FIRST; ; ; }",
        "class Example { void run() { class Local { ; ; } } }",
        "class Example { Object value = new Object() { ; ; }; }",
        "enum Example { FIRST { ; ; }; }");
    // Act
    var tests = sources.stream().map(source -> DynamicTest.dynamicTest(source, () -> {
      var ast = produce(source, AstProfile.JAVA_SOURCE_DECLARATIONS);
      var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
      // Assert
      assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
      var empty = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.EMPTY).toList();
      assertEquals(2, empty.size());
      assertEquals(2, retained.coverage().validationEvidence().writtenInventory().contexts().stream()
          .filter(context -> context.address().partition() == SourceContext.Partition.EMPTY_BODY_DECLARATION).count());
      assertTrue(empty.stream().allMatch(node -> node.structuralPath().steps().getLast().role() == SyntaxRole.MEMBER));
      assertEquals(ast.aggregate().nodes(), retained.aggregate().nodes());
    }));
    // Assert
    return tests;
  }

  @Test
  void givenCommentsLiteralsUnicodeAndExecutableSemicolons_whenProduced_thenExactBodyOccurrencesStaySeparate() throws IOException {
    // Arrange
    var source = "class Example { /* ; */ \\u003b String text = \";\"; void run() { ; } ; }";
    // Act
    var ast = produce(source, AstProfile.JAVA_SOURCE_DECLARATIONS);
    var members = ast.aggregate().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION).children(SyntaxRole.MEMBER);
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertEquals(List.of(SyntaxKind.EMPTY, SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION, SyntaxKind.EMPTY),
        members.stream().map(node -> node.syntaxKind()).toList());
    assertEquals("\\u003b", source.substring(Math.toIntExact(members.getFirst().origin().orElseThrow().startOffset()),
        Math.toIntExact(members.getFirst().origin().orElseThrow().endOffsetExclusive())));
    assertEquals(SyntaxRole.STATEMENT, members.get(2).requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT).getFirst()
        .structuralPath().steps().getLast().role());
    assertEquals(2, ast.coverage().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.EMPTY_BODY_DECLARATION).count());
  }

  @Test
  void givenCompactUnitSemicolonsOutsideWrittenTypeDeclarations_whenProduced_thenTheUnitEnvelopeGapRemainsTruthful() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "; void main() {} ;")));
    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_DECLARATIONS);
    // Assert
    assertTrue(outcome.result().isEmpty() || outcome.result().orElseThrow().completion() != AstCompletion.COMPLETE);
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-class-member")),
        outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        outcome.diagnostics().toString());
  }

  @Test
  void givenAnInvalidAnnotationTargetBesideValidEmptyMembers_whenProduced_thenActualCompilerErrorsPreventCompletion() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.METHOD) @interface Mark {}
        class Example { ; @Mark int value; }
        """)));
    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_DECLARATIONS);
    // Assert
    assertTrue(outcome.result().isEmpty() || outcome.result().orElseThrow().completion() != AstCompletion.COMPLETE);
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.") && diagnostic.isError()),
        outcome.diagnostics().toString());
  }

  @Test
  void givenOlderEnumProfileWithBodySemicolons_whenProducedAndDecoded_thenItsOriginalIncompleteQualificationRemains() throws IOException {
    // Arrange
    var source = "enum Example { FIRST { ; }; }";
    // Act
    var ast = produce(source, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, retained.completion());
    assertEquals(AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, retained.profile());
    assertTrue(retained.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-class-member")));
    assertTrue(retained.aggregate().nodes().stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.EMPTY));
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
    assertTrue(outcome.result().isPresent(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }
}
