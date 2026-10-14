package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclarationFidelityAstTest {
  @TempDir Path directory;

  @Test
  void givenSharedAnnotatedFieldsAndRecordComponentsAmongEmptyMembers_whenProducedAndDecoded_thenSingleWrittenOccurrencesKeepEveryTarget()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Dual { int value() default 7; }
        @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
        @interface Component {}
        class Example {
          ; @Dual int first = 1, second = 2; ;
          record Nested(@Component String value) { ; }
        }
        """;
    // Act
    var ast = retained(source);
    var dual = annotation(ast, source, "@Dual");
    var component = annotation(ast, source, "@Component");
    var application = application(ast, component);
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertTrue(dual.children(SyntaxRole.ANNOTATION_ARGUMENT).isEmpty());
    assertEquals(1, ast.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(dual.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).count());
    assertTrue(application.fieldDeclarators().isEmpty());
    assertTrue(application.declarationNodeKey().isPresent());
    assertTrue(application.typeLocation().isPresent());
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
        application.propagatedCounterparts().stream().map(value -> value.reference().referenceKind()).toList());
    var dualApplication = application(ast, dual);
    assertEquals(1, dualApplication.fieldDeclarators().size());
    assertTrue(dualApplication.fieldDeclarators().getFirst().typeLocation().isPresent());
    assertEquals(3, ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.EMPTY).count());
  }

  @Test
  void givenFlexibleConstructorProloguesAndBothRecordConstructorForms_whenProducedAndDecoded_thenOrderedBodiesAndHeaderOriginsRemain()
      throws IOException {
    // Arrange
    var source = """
        class Parent { Parent(int value) {} }
        class Example extends Parent {
          ; Example() { int value = 1; this(value); }
          ; Example(int value) { int checked = value; super(checked); }
          record Compact(int value) { ; Compact { if (value < 0) throw new IllegalArgumentException(); } }
          record Ordinary(int value) { ; Ordinary(int value) { this.value = value; } }
        }
        """;
    // Act
    var ast = retained(source);
    var declarations = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration)
        .toList();
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    for (var constructor : declarations.stream().filter(node -> node.value(SyntaxValue.ExecutableDeclaration.class)
        .ownerKey().equals("type:Example")).toList()) {
      var statements = constructor.requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT);
      assertEquals(List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.EXPRESSION_STATEMENT),
          statements.stream().map(node -> node.syntaxKind()).toList());
      var delegation = statements.getLast().requiredChild(SyntaxRole.EXPRESSION);
      var form = delegation.value(SyntaxValue.Invocation.class).form();
      assertTrue(form == InvocationForm.THIS_CONSTRUCTOR || form == InvocationForm.SUPER_CONSTRUCTOR);
      assertTrue(ast.body(constructor.value(SyntaxValue.ExecutableDeclaration.class).declarationKey()).orElseThrow()
          .invocationEvidence(delegation.nodeId()).isPresent());
    }
    var compact = declarations.stream().filter(node -> node.value(SyntaxValue.ExecutableDeclaration.class).kind()
        == SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR).findFirst().orElseThrow();
    assertTrue(compact.children(SyntaxRole.PARAMETER).isEmpty());
    var ordinary = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value
        && value.name().equals("Ordinary")).findFirst().orElseThrow().children(SyntaxRole.MEMBER).stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.METHOD_DECLARATION).findFirst().orElseThrow();
    assertEquals("value", ordinary.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.ParameterDeclaration.class).name());
    assertTrue(declarations.stream().allMatch(node -> node.origin().isPresent()));
  }

  @Test
  void givenRepeatedAnnotationsAndExplicitVersusDefaultValues_whenProducedAndDecoded_thenWrittenPairsStayDistinctFromEffectiveDefaults()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.Repeatable;
        @Repeatable(Labels.class) @interface Label { int value() default 7; }
        @interface Labels { Label[] value(); }
        @Label @Label(value=8) class Example { ; }
        """;
    // Act
    var ast = retained(source);
    var labels = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
        && node.semanticReference().orElseThrow().symbolKey().orElseThrow().equals("type:Label")).toList();
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertEquals(2, labels.size());
    assertEquals(List.of(0, 1), labels.stream().map(node -> node.children(SyntaxRole.ANNOTATION_ARGUMENT).size()).toList());
    assertEquals("8", labels.getLast().requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).requiredChild(SyntaxRole.VALUE)
        .value(SyntaxValue.Literal.class).text());
    assertEquals(1, ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration)
        .flatMap(node -> node.children(SyntaxRole.DEFAULT_VALUE).stream()).count());
  }

  private AstResult retained(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_DECLARATIONS);
    assertTrue(outcome.result().isPresent(), outcome.diagnostics().toString());
    var ast = outcome.result().orElseThrow();
    return AstCodec.decode(AstCodec.encode(ast), ast.identity());
  }

  private static SyntaxNode annotation(AstResult ast, String source, String text) {
    var matches = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> source.substring(Math.toIntExact(node.origin().orElseThrow().startOffset()),
            Math.toIntExact(node.origin().orElseThrow().endOffsetExclusive())).equals(text)).toList();
    assertEquals(1, matches.size());
    return matches.getFirst();
  }

  private static SyntaxSemanticTarget.AnnotationApplication application(AstResult ast, SyntaxNode annotation) {
    return ast.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET)
        .map(edge -> (SyntaxSemanticTarget.AnnotationApplication) edge.target()).findFirst().orElseThrow();
  }
}
