package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.tools.ToolProvider;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.AnnotationElementValue;
import org.aquilagraph.syntax.AnnotationScalar;
import org.aquilagraph.syntax.JavaTypeStructure;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaAnnotationDefaultAstTest {
  @TempDir Path sourceRoot;

  @Test
  void givenDefaultAndTwoUseSites_whenAnalyzed_thenDefaultBelongsOnlyToItsDeclaration() throws IOException {
    // Arrange
    var source = """
        @interface Choice { int value() default 7; int required(); }
        @Choice(required=1) class Omitted {}
        @Choice(value=7,required=2) class Explicit {}
        """;
    Files.writeString(sourceRoot.resolve("Choice.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var defaults = aggregate.nodes().stream().flatMap(node -> node.children(SyntaxRole.DEFAULT_VALUE).stream()).toList();
    var elements = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable
        && executable.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT).toList();
    var omitted = type(aggregate.nodes(), "Omitted").requiredChild(SyntaxRole.ANNOTATION);
    var explicit = type(aggregate.nodes(), "Explicit").requiredChild(SyntaxRole.ANNOTATION);

    // Assert
    assertEquals(1, defaults.size());
    assertEquals("7", excerpt(source, defaults.getFirst()));
    assertEquals(new AnnotationScalar.IntValue(7), element(aggregate.nodes(), "value").value(SyntaxValue.ExecutableDeclaration.class).defaultValue().orElseThrow());
    assertEquals(1, elements.stream().filter(node -> !node.children(SyntaxRole.DEFAULT_VALUE).isEmpty()).count());
    assertEquals(0, elements.stream().filter(node -> node.value(SyntaxValue.ExecutableDeclaration.class).name().equals("required"))
        .findFirst().orElseThrow().children(SyntaxRole.DEFAULT_VALUE).size());
    assertEquals(List.of("required"), omitted.children(SyntaxRole.ANNOTATION_ARGUMENT).stream()
        .map(node -> node.value(SyntaxValue.AnnotationArgument.class).explicitName().orElseThrow()).toList());
    assertEquals(List.of("value", "required"), explicit.children(SyntaxRole.ANNOTATION_ARGUMENT).stream()
        .map(node -> node.value(SyntaxValue.AnnotationArgument.class).explicitName().orElseThrow()).toList());
    assertEquals(SyntaxKind.ANNOTATION, omitted.syntaxKind());
    assertEquals(SyntaxKind.ANNOTATION, explicit.syntaxKind());
    assertNotEquals(defaults.getFirst().nodeId(), explicit.children(SyntaxRole.ANNOTATION_ARGUMENT).getFirst().nodeId());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenRecursiveAndNonliteralDefaults_whenAnalyzed_thenExactTypedValuesRemainDeclarationOwned() throws IOException {
    // Arrange
    var source = """
        @interface Inner { int value(); }
        @interface Choices {
          int number() default Integer.BYTES;
          java.lang.annotation.ElementType color() default java.lang.annotation.ElementType.TYPE;
          Class<?> kind() default String.class;
          Inner inner() default @Inner(2);
          Inner[] many() default {@Inner(1), @Inner(1)};
          String[] empty() default {};
        }
        @Choices class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Choices.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var owners = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable
        && executable.defaultValue().isPresent()).toList();
    var defaults = owners.stream().map(node -> node.requiredChild(SyntaxRole.DEFAULT_VALUE)).toList();
    var values = owners.stream().map(node -> node.value(SyntaxValue.ExecutableDeclaration.class).defaultValue().orElseThrow()).toList();
    var sample = type(aggregate.nodes(), "Sample").requiredChild(SyntaxRole.ANNOTATION);

    // Assert
    assertEquals(6, defaults.size(), () -> result.diagnostics().toString());
    assertEquals(0, sample.children(SyntaxRole.ANNOTATION_ARGUMENT).size());
    assertEquals(List.of("Integer.BYTES", "java.lang.annotation.ElementType.TYPE", "String.class", "@Inner(2)",
        "{@Inner(1), @Inner(1)}", "{}"), defaults.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(new AnnotationScalar.IntValue(4), values.getFirst());
    assertEquals(new AnnotationScalar.EnumConstant("type:java.lang.annotation.ElementType#enum:TYPE",
        "type:java.lang.annotation.ElementType"), values.get(1));
    assertEquals(new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of()),
        assertInstanceOf(AnnotationScalar.ClassLiteral.class, values.get(2))
            .operandType());
    assertEquals(new AnnotationElementValue.Nested("type:Inner"), values.get(3));
    assertEquals(new AnnotationElementValue.Array(List.of(new AnnotationElementValue.Nested("type:Inner"),
        new AnnotationElementValue.Nested("type:Inner"))), values.get(4));
    assertEquals(new AnnotationElementValue.Array(List.of()), values.get(5));
    assertEquals(List.of("@Inner(1)", "@Inner(1)"), defaults.get(4).children(SyntaxRole.VALUE).stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(SyntaxKind.ANNOTATION_NAME_REFERENCE, SyntaxKind.ANNOTATION_NAME_REFERENCE,
        SyntaxKind.ANNOTATION_CLASS_LITERAL, SyntaxKind.ANNOTATION, SyntaxKind.ANNOTATION_ARRAY, SyntaxKind.ANNOTATION_ARRAY),
        defaults.stream().map(SyntaxNode::syntaxKind).toList());
    for (var owner : owners) {
      var expression = owner.requiredChild(SyntaxRole.DEFAULT_VALUE);
      assertEquals(owner.structuralPath().child(SyntaxRole.DEFAULT_VALUE, 0), expression.structuralPath());
      assertEquals(1, aggregate.nodes().stream().filter(node -> node.origin().equals(expression.origin())).count());
    }
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenArrayDefaultForms_whenAnalyzed_thenDirectExpressionsPreserveWrittenBraces() throws IOException {
    // Arrange
    var source = "@interface Forms { int[] single() default 1; int[] braced() default {1}; int[] empty() default {}; }";
    Files.writeString(sourceRoot.resolve("Forms.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var nodes = result.aggregate().orElseThrow().nodes();
    var single = element(nodes, "single");
    var braced = element(nodes, "braced");
    var empty = element(nodes, "empty");
    var expressions = List.of(single, braced, empty).stream()
        .map(node -> node.requiredChild(SyntaxRole.DEFAULT_VALUE)).toList();

    // Assert
    assertEquals(new AnnotationElementValue.Array(List.of(new AnnotationScalar.IntValue(1))),
        single.value(SyntaxValue.ExecutableDeclaration.class).defaultValue().orElseThrow());
    assertEquals(single.value(SyntaxValue.ExecutableDeclaration.class).defaultValue(),
        braced.value(SyntaxValue.ExecutableDeclaration.class).defaultValue());
    assertEquals(new AnnotationElementValue.Array(List.of()),
        empty.value(SyntaxValue.ExecutableDeclaration.class).defaultValue().orElseThrow());
    assertEquals(List.of(SyntaxKind.LITERAL, SyntaxKind.ANNOTATION_ARRAY, SyntaxKind.ANNOTATION_ARRAY),
        expressions.stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of("1", "{1}", "{}"), expressions.stream().map(node -> excerpt(source, node)).toList());
    assertTrue(expressions.getFirst().children().isEmpty());
    assertEquals(List.of("1"), expressions.get(1).children(SyntaxRole.VALUE).stream()
        .map(node -> excerpt(source, node)).toList());
    assertTrue(expressions.get(2).children().isEmpty());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenInvalidAnnotationDefaults_whenAnalyzed_thenCompilerErrorsPreventCompletedAnalysis() throws IOException {
    // Arrange
    var sourceFile = sourceRoot.resolve("Invalid.java");
    var invalidDefaults = List.of("int value() default null;", "int value() default \"x\";",
        "int[][] value() default {};", "Missing value() default @Missing;",
        "Inner value() default @Inner;", "B value() default @B;");

    for (var declaration : invalidDefaults) {
      var support = declaration.startsWith("Inner ") ? "@interface Inner { int required(); }\n"
          : declaration.startsWith("B ") ? "@interface B { Invalid value(); }\n" : "";
      Files.writeString(sourceFile, support + "@interface Invalid { " + declaration + " }\n");

      // Act
      var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

      // Assert
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
          () -> declaration + ": " + result.diagnostics());
      assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(result).completion());
      assertTrue(result.aggregate().orElseThrow().nodes().stream().noneMatch(node ->
          !node.children(SyntaxRole.DEFAULT_VALUE).isEmpty()), () -> declaration + ": " + result.diagnostics());
    }
  }

  @Test
  void givenMissingAttributedDefault_whenProjected_thenSourceDiagnosticPreventsPublication() throws IOException {
    // Arrange
    var sourceText = "@interface Missing { int value() default 7; }";
    Files.writeString(sourceRoot.resolve("Missing.java"), sourceText);
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var source = snapshot.sourceFiles().getFirst();
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    try (var session = CompilerSession.open(ToolProvider.getSystemJavaCompiler(),
        JavaCompilerInvocation.from(configuration), new SnapshotSourceCatalog(snapshot))) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed);
      var unit = compilation.units().getFirst();
      var method = (MethodTree) ((ClassTree) unit.getTypeDecls().getFirst()).getMembers().stream()
          .filter(member -> member instanceof MethodTree).findFirst().orElseThrow();
      var actual = (ExecutableElement) compilation.semantics().element(TreePath.getPath(unit, method));
      var missing = (ExecutableElement) Proxy.newProxyInstance(ExecutableElement.class.getClassLoader(),
          new Class<?>[] {ExecutableElement.class}, (proxy, invocation, arguments) -> {
            if (invocation.getName().equals("getDefaultValue")) {
              return null;
            }
            return invocation.invoke(actual, arguments);
          });
      var projector = new JavaAnnotationValueProjector(compilation.semantics(), parsed.sourceEvidence(),
          compilation.treeInventory());
      var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
          .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DEFAULT_VALUE, 0);
      var diagnostics = new ArrayList<AnalysisDiagnostic>();
      var callerDiagnostics = new ArrayList<AnalysisDiagnostic>();
      var callerChildren = new ArrayList<SyntaxChild>();
      var translator = new JavaAstContributionTranslator(compilation.semantics(), parsed.sourceEvidence(),
          compilation.treeInventory());

      // Act
      var projected = projector.defaultValue(unit, source, method, missing, path, diagnostics);
      var attached = translator.appendDefault(unit, source, method, missing,
          SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0),
          callerChildren, callerDiagnostics);

      // Assert
      assertTrue(projected.isEmpty());
      assertTrue(attached.isEmpty());
      assertTrue(callerChildren.isEmpty());
      assertEquals(1, diagnostics.size());
      assertEquals(1, callerDiagnostics.size());
      assertEquals("frontend.unavailable-annotation-default", diagnostics.getFirst().code());
      assertEquals("frontend.unavailable-annotation-default", callerDiagnostics.getFirst().code());
      assertEquals(List.of(new SourceSpan(source.sourceFileKey(), sourceText.indexOf('7'), sourceText.indexOf('7') + 1)),
          diagnostics.getFirst().origins());
      assertEquals(diagnostics.getFirst().origins(), callerDiagnostics.getFirst().origins());
    }
  }

  @Test
  void givenMovedDefaultAndReorderedArrayItems_whenReconstructed_thenSourceOwnershipIsRejected() throws IOException {
    // Arrange
    var source = "@interface Ranges { int one() default 7; int[] many() default {1, 2}; int later(); }";
    Files.writeString(sourceRoot.resolve("Ranges.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var nodes = result.aggregate().orElseThrow().nodes();
    var one = element(nodes, "one");
    var many = element(nodes, "many");
    var originalExpression = one.requiredChild(SyntaxRole.DEFAULT_VALUE);
    var outside = new SourceSpan(originalExpression.sourceFileKey(), source.indexOf("later"), source.indexOf("later") + 1);
    var movedExpression = copy(originalExpression, originalExpression.children(), outside);
    var movedChildren = one.children().stream().map(child -> child.role() == SyntaxRole.DEFAULT_VALUE
        ? new SyntaxChild(SyntaxRole.DEFAULT_VALUE, 0, movedExpression) : child).toList();
    var array = many.requiredChild(SyntaxRole.DEFAULT_VALUE);
    var items = array.children(SyntaxRole.VALUE);

    // Act
    var outsideOwner = assertThrows(IllegalArgumentException.class, () -> copy(one, movedChildren,
        one.origin().orElseThrow()));
    var reversed = assertThrows(IllegalArgumentException.class, () -> copy(array,
        List.of(new SyntaxChild(SyntaxRole.VALUE, 0, items.get(1)),
            new SyntaxChild(SyntaxRole.VALUE, 1, items.get(0))),
        array.origin().orElseThrow()));
    var movedItem = copy(items.getFirst(), items.getFirst().children(), outside);
    var outsideArray = assertThrows(IllegalArgumentException.class, () -> copy(array,
        List.of(new SyntaxChild(SyntaxRole.VALUE, 0, movedItem),
            new SyntaxChild(SyntaxRole.VALUE, 1, items.get(1))),
        array.origin().orElseThrow()));

    // Assert
    assertTrue(outsideOwner.getMessage().contains("ranges must be contained"));
    assertTrue(reversed.getMessage().contains("ranges must be contained and ordered"));
    assertTrue(outsideArray.getMessage().contains("ranges must be contained"));
  }

  @Test
  void givenRepeatedAndChangedDefaults_whenAnalyzed_thenRecursiveFactsAndOwnershipDetermineEquality() throws IOException {
    // Arrange
    var sourceFile = sourceRoot.resolve("Stable.java");
    var original = "@interface Inner { int value(); }\n@interface Stable { Inner[] first() default {@Inner(1), @Inner(2)}; Inner[] second(); }";
    Files.writeString(sourceFile, original);
    // Act
    var first = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var repeated = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    Files.writeString(sourceFile, original.replace("@Inner(2)", "@Inner(3)"));
    var changedValue = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    Files.writeString(sourceFile, original.replace("first() default {@Inner(1), @Inner(2)}; Inner[] second()",
        "first(); Inner[] second() default {@Inner(1), @Inner(2)}"));
    var changedOwner = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertEquals(first.aggregate().orElseThrow(), repeated.aggregate().orElseThrow());
    assertEquals(first.aggregate().orElseThrow().nodeTuples(), repeated.aggregate().orElseThrow().nodeTuples());
    assertEquals(first.aggregate().orElseThrow().rangeTuples(), repeated.aggregate().orElseThrow().rangeTuples());
    assertEquals(first.aggregate().orElseThrow().structuralEdges(), repeated.aggregate().orElseThrow().structuralEdges());
    assertEquals(first.aggregate().orElseThrow().semanticEdges(), repeated.aggregate().orElseThrow().semanticEdges());
    assertEquals(first.diagnostics(), repeated.diagnostics());
    assertNotEquals(first.aggregate().orElseThrow(), changedValue.aggregate().orElseThrow());
    assertNotEquals(first.aggregate().orElseThrow(), changedOwner.aggregate().orElseThrow());
    assertEquals(1, element(first.aggregate().orElseThrow().nodes(), "first").children(SyntaxRole.DEFAULT_VALUE).size());
    assertEquals(0, element(changedOwner.aggregate().orElseThrow().nodes(), "first").children(SyntaxRole.DEFAULT_VALUE).size());
    assertEquals(1, element(changedOwner.aggregate().orElseThrow().nodes(), "second").children(SyntaxRole.DEFAULT_VALUE).size());
  }

  @Test
  void givenOrdinaryMethodBodyArray_whenAnalyzed_thenBodySyntaxRemainsDiagnosed() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Body.java"), "class Body { int[] values() { return new int[] {1,2}; } }");

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")
        && diagnostic.message().contains("NEW_ARRAY") && diagnostic.origins().stream().anyMatch(origin ->
            origin.length() > 0)),
        () -> result.diagnostics().toString());
    assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
  }

  private static SyntaxNode type(List<SyntaxNode> nodes, String name) {
    return nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals(name)).findFirst().orElseThrow();
  }

  private static SyntaxNode element(List<SyntaxNode> nodes, String name) {
    return nodes.stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable
        && executable.name().equals(name)).findFirst().orElseThrow();
  }

  private static SyntaxNode copy(SyntaxNode original, List<SyntaxChild> children, SourceSpan origin) {
    return SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(),
        original.type(), original.value(), children, original.semanticReference(), origin);
  }

  private static String excerpt(String source, SyntaxNode node) {
    var span = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }
}
