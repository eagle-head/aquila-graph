package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaAnnotationValueAstTest {
  @TempDir Path sourceRoot;

  @Test
  void givenBracedAndUnbracedArrays_whenAnalyzed_thenCardinalityOrderAndSourceFormsRemainDistinct() throws IOException {
    // Arrange
    var source = """
        @interface Numbers { int[] value(); }
        @Numbers({3,1,3,}) class Braced {}
        @Numbers(3) class Unbraced {}
        @Numbers({3}) class Singleton {}
        @Numbers({}) class Empty {}
        """;
    Files.writeString(sourceRoot.resolve("Numbers.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var pairs = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT).toList();
    var values = pairs.stream().map(node -> (AnnotationElementValue.Array) node.value(SyntaxValue.AnnotationArgument.class).value())
        .toList();
    var written = pairs.stream().map(node -> node.requiredChild(SyntaxRole.VALUE)).toList();

    // Assert
    assertEquals(List.of(new AnnotationScalar.IntValue(3), new AnnotationScalar.IntValue(1),
        new AnnotationScalar.IntValue(3)), values.get(0).elements());
    assertEquals(List.of(new AnnotationScalar.IntValue(3)), values.get(1).elements());
    assertEquals(values.get(1), values.get(2));
    assertEquals(List.of(), values.get(3).elements());
    assertEquals(List.of("{3,1,3,}", "3", "{3}", "{}"), written.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of("3", "1", "3"), written.get(0).children(SyntaxRole.VALUE).stream()
        .map(node -> excerpt(source, node)).toList());
    assertEquals(0, written.get(1).children(SyntaxRole.VALUE).size());
    assertEquals(0, written.get(3).children(SyntaxRole.VALUE).size());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenNestedValuesAndAnnotationArrays_whenAnalyzed_thenEachNestedOccurrenceKeepsItsOwnTypeAndArguments()
      throws IOException {
    // Arrange
    var source = """
        @interface Inner { int value(); }
        @interface Other { String value(); }
        @interface Outer { Inner inner(); Inner[] many(); Other other(); }
        @Outer(inner=@Inner(2), many={@Inner(1), @Inner(1)}, other=@Other("x")) class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Outer.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var sample = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    var outer = sample.requiredChild(SyntaxRole.ANNOTATION);
    var pairs = outer.children(SyntaxRole.ANNOTATION_ARGUMENT);
    var inner = pairs.getFirst().requiredChild(SyntaxRole.VALUE);
    var many = pairs.get(1).requiredChild(SyntaxRole.VALUE).children(SyntaxRole.VALUE);
    var other = pairs.get(2).requiredChild(SyntaxRole.VALUE);

    // Assert
    assertEquals(1, sample.children(SyntaxRole.ANNOTATION).size());
    assertEquals(new AnnotationElementValue.Nested("type:Inner"), pairs.getFirst().value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(new AnnotationElementValue.Array(List.of(new AnnotationElementValue.Nested("type:Inner"),
        new AnnotationElementValue.Nested("type:Inner"))), pairs.get(1).value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(new AnnotationElementValue.Nested("type:Other"), pairs.get(2).value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(List.of("@Inner(2)", "@Inner(1)", "@Inner(1)", "@Other(\"x\")"),
        List.of(inner, many.get(0), many.get(1), other).stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(new AnnotationScalar.IntValue(2), new AnnotationScalar.IntValue(1),
        new AnnotationScalar.IntValue(1), new AnnotationScalar.StringValue("x")),
        List.of(inner, many.get(0), many.get(1), other).stream().map(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
            .value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertTrue(List.of(inner, many.get(0), many.get(1), other).stream().allMatch(node ->
        node.value(SyntaxValue.Annotation.class).location() == SyntaxValue.AnnotationLocation.VALUE));
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenArraysOfEveryLegalElementFamily_whenAnalyzed_thenEachRetainsItsAttributedComponentValue() throws IOException {
    // Arrange
    var source = """
        @interface Inner { int value(); }
        @interface Families {
          boolean[] bools(); byte[] bytes(); short[] shorts(); char[] chars(); int[] ints(); long[] longs();
          float[] floats(); double[] doubles(); String[] strings();
          java.lang.annotation.ElementType[] enums(); Class<?>[] classes(); Inner[] nested();
        }
        @Families(bools={true},bytes={1},shorts={2},chars={'x'},ints={3},longs={4L},
          floats={1.5f},doubles={2.5},strings={"ok"},
          enums={java.lang.annotation.ElementType.TYPE},classes={String.class},nested={@Inner(6)})
        class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Families.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var pairs = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT
            && node.value(SyntaxValue.AnnotationArgument.class).selectedElementKey().startsWith("type:Families#"))
        .toList();
    var values = pairs.stream().map(node -> ((AnnotationElementValue.Array) node.value(SyntaxValue.AnnotationArgument.class)
        .value()).elements().getFirst()).toList();

    // Assert
    assertEquals(12, pairs.size(), () -> result.diagnostics().toString());
    assertEquals(List.of(new AnnotationScalar.BooleanValue(true), new AnnotationScalar.ByteValue((byte) 1),
        new AnnotationScalar.ShortValue((short) 2), new AnnotationScalar.CharValue('x'), new AnnotationScalar.IntValue(3),
        new AnnotationScalar.LongValue(4L), new AnnotationScalar.FloatValue(1.5f), new AnnotationScalar.DoubleValue(2.5),
        new AnnotationScalar.StringValue("ok")), values.subList(0, 9));
    assertInstanceOf(AnnotationScalar.EnumConstant.class, values.get(9));
    assertInstanceOf(AnnotationScalar.ClassLiteral.class, values.get(10));
    assertEquals(new AnnotationElementValue.Nested("type:Inner"), values.get(11));
    assertTrue(pairs.stream().allMatch(node -> node.requiredChild(SyntaxRole.VALUE).children(SyntaxRole.VALUE).size() == 1));
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenRepeatedAnnotationsAndWrittenContainer_whenAnalyzed_thenSourceStructuresStayDistinct() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.Repeatable;
        @Repeatable(Tags.class) @interface Tag { int value(); }
        @interface Tags { Tag[] value(); }
        @Tag(1) @Tag(2) class Repeated {}
        @Tags({@Tag(1), @Tag(2)}) class Container {}
        """;
    Files.writeString(sourceRoot.resolve("Tags.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var nodes = result.aggregate().orElseThrow().nodes();
    var repeated = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Repeated")).findFirst().orElseThrow();
    var container = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Container")).findFirst().orElseThrow();
    var containerValue = container.requiredChild(SyntaxRole.ANNOTATION).requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
        .requiredChild(SyntaxRole.VALUE);

    // Assert
    assertEquals(List.of("@Tag(1)", "@Tag(2)"), repeated.children(SyntaxRole.ANNOTATION).stream()
        .map(node -> excerpt(source, node)).toList());
    assertEquals(List.of("@Tags({@Tag(1), @Tag(2)})"), container.children(SyntaxRole.ANNOTATION).stream()
        .map(node -> excerpt(source, node)).toList());
    assertEquals(List.of("@Tag(1)", "@Tag(2)"), containerValue.children(SyntaxRole.VALUE).stream()
        .map(node -> excerpt(source, node)).toList());
    assertEquals(2, ((AnnotationElementValue.Array) container.requiredChild(SyntaxRole.ANNOTATION)
        .requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).value(SyntaxValue.AnnotationArgument.class).value()).elements().size());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenAllScalarCategories_whenAnalyzed_thenOrderedPairsRetainAttributedValuesAndWrittenNames() throws IOException {
    // Arrange
    var source = """
        @interface Scalar {
          boolean z(); byte b(); short s(); char c(); int i(); long l();
          float f(); double d(); String t(); java.lang.annotation.ElementType e(); Class<?> k();
        }
        @Scalar(t="ok", i=Integer.BYTES+1, z=true, b=(byte)1, s=2, c='x',
            l=3L, f=1.5f, d=2.5, e=java.lang.annotation.ElementType.TYPE, k=String[].class)
        class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Scalar.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var sample = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    var annotation = sample.requiredChild(SyntaxRole.ANNOTATION);
    var arguments = annotation.children(SyntaxRole.ANNOTATION_ARGUMENT);

    // Assert
    assertEquals(List.of("t", "i", "z", "b", "s", "c", "l", "f", "d", "e", "k"),
        arguments.stream().map(node -> node.value(SyntaxValue.AnnotationArgument.class).explicitName().orElseThrow()).toList());
    assertEquals(List.of(new AnnotationScalar.StringValue("ok"), new AnnotationScalar.IntValue(5),
        new AnnotationScalar.BooleanValue(true), new AnnotationScalar.ByteValue((byte) 1),
        new AnnotationScalar.ShortValue((short) 2), new AnnotationScalar.CharValue('x'),
        new AnnotationScalar.LongValue(3L), new AnnotationScalar.FloatValue(1.5f),
        new AnnotationScalar.DoubleValue(2.5)),
        arguments.subList(0, 9).stream().map(node -> node.value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertInstanceOf(AnnotationScalar.EnumConstant.class, arguments.get(9).value(SyntaxValue.AnnotationArgument.class).value());
    assertInstanceOf(AnnotationScalar.ClassLiteral.class, arguments.get(10).value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(JavaTypeCategory.SHORT, ((SyntaxType.Resolved) arguments.get(4).type()).identity().category());
    assertEquals(JavaTypeCategory.INT,
        ((SyntaxType.Resolved) arguments.get(4).requiredChild(SyntaxRole.VALUE).type()).identity().category());
    assertEquals(JavaTypeCategory.BYTE,
        ((SyntaxType.Resolved) arguments.get(3).requiredChild(SyntaxRole.VALUE).type()).identity().category());
    assertEquals(new AnnotationScalar.EnumConstant("type:java.lang.annotation.ElementType#enum:TYPE",
        "type:java.lang.annotation.ElementType"), arguments.get(9).value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(SyntaxKind.ANNOTATION_NAME_REFERENCE, arguments.get(9).requiredChild(SyntaxRole.VALUE).syntaxKind());
    assertEquals(SyntaxKind.ANNOTATION_CLASS_LITERAL, arguments.get(10).requiredChild(SyntaxRole.VALUE).syntaxKind());
    assertEquals(SyntaxKind.TYPE_USE, arguments.get(10).requiredChild(SyntaxRole.VALUE)
        .requiredChild(SyntaxRole.TYPE_OPERAND).syntaxKind());
    assertTrue(arguments.stream().allMatch(node -> node.semanticReference().orElseThrow().referenceKind() == ReferenceKind.METHOD));
    assertEquals(List.of("t", "i", "z", "b", "s", "c", "l", "f", "d", "e", "k").stream()
        .map(name -> "type:Scalar#" + name + "()").toList(),
        arguments.stream().map(node -> node.value(SyntaxValue.AnnotationArgument.class).selectedElementKey()).toList());
    assertTrue(arguments.stream().allMatch(node -> excerpt(source, node).startsWith(node.value(SyntaxValue.AnnotationArgument.class)
        .explicitName().orElseThrow() + "=")));
    assertEquals(List.of("t", "i", "z", "b", "s", "c", "l", "f", "d", "e", "k"), arguments.stream()
        .map(node -> excerpt(source, node.requiredChild(SyntaxRole.ANNOTATION_ELEMENT_NAME))).toList());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenPackageAndModuleScalarAnnotations_whenAnalyzed_thenBothOwnersRetainExplicitPairs() throws IOException {
    // Arrange
    var packageDirectory = Files.createDirectory(sourceRoot.resolve("sample"));
    Files.writeString(packageDirectory.resolve("OnPackage.java"),
        "package sample; public @interface OnPackage { int value(); }\n");
    Files.writeString(packageDirectory.resolve("OnModule.java"),
        "package sample; public @interface OnModule { int value(); }\n");
    Files.writeString(packageDirectory.resolve("package-info.java"), "@sample.OnPackage(7) package sample;\n");
    Files.writeString(sourceRoot.resolve("module-info.java"), "@sample.OnModule(8) module sample.module {}\n");

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var packageNode = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.PackageDeclaration
        && node.children(SyntaxRole.ANNOTATION).size() == 1)
        .findFirst().orElseThrow();
    var moduleNode = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ModuleDeclaration)
        .findFirst().orElseThrow();
    var packagePair = packageNode.requiredChild(SyntaxRole.ANNOTATION).requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);
    var modulePair = moduleNode.requiredChild(SyntaxRole.ANNOTATION).requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);

    // Assert
    assertEquals(new AnnotationScalar.IntValue(7), packagePair.value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals("type:sample.OnPackage#value()", packagePair.value(SyntaxValue.AnnotationArgument.class).selectedElementKey());
    assertEquals(new AnnotationScalar.IntValue(8), modulePair.value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals("type:sample.OnModule#value()", modulePair.value(SyntaxValue.AnnotationArgument.class).selectedElementKey());
    assertTrue(packagePair.value(SyntaxValue.AnnotationArgument.class).explicitName().isEmpty());
    assertTrue(modulePair.value(SyntaxValue.AnnotationArgument.class).explicitName().isEmpty());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenShorthandAndConstantExpression_whenAnalyzed_thenSyntaxTreeRetainsOperatorsReferencesAndRanges() throws IOException {
    // Arrange
    var source = """
        @interface NumberValue { int value(); }
        @NumberValue((int)(true ? Integer.BYTES + 2 * 3 : -(1))) class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("NumberValue.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var annotation = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation
        && excerpt(source, node).startsWith("@NumberValue(")).findFirst().orElseThrow();
    var pair = annotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);
    var cast = pair.requiredChild(SyntaxRole.VALUE).requiredChild(SyntaxRole.EXPRESSION);

    // Assert
    assertEquals(SyntaxValue.AnnotationForm.SINGLE_ELEMENT, annotation.value(SyntaxValue.Annotation.class).form());
    assertTrue(pair.value(SyntaxValue.AnnotationArgument.class).explicitName().isEmpty());
    assertEquals("", pair.value(SyntaxValue.Symbol.class).name());
    assertTrue(pair.children(SyntaxRole.ANNOTATION_ELEMENT_NAME).isEmpty());
    assertEquals(new AnnotationScalar.IntValue(10), pair.value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(SyntaxKind.ANNOTATION_CAST, pair.requiredChild(SyntaxRole.VALUE).syntaxKind());
    assertEquals(SyntaxKind.TYPE_USE, pair.requiredChild(SyntaxRole.VALUE).requiredChild(SyntaxRole.TYPE_OPERAND).syntaxKind());
    assertEquals(SyntaxKind.PARENTHESIZED, cast.syntaxKind());
    assertTrue(pair.descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.CONDITIONAL_EXPRESSION));
    assertTrue(pair.descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.BINARY));
    assertTrue(pair.descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNARY));
    assertTrue(pair.descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_NAME_REFERENCE
        && node.semanticReference().orElseThrow().referenceKind() == ReferenceKind.FIELD));
    assertEquals("(int)(true ? Integer.BYTES + 2 * 3 : -(1))", excerpt(source, pair));
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenRepeatableScalarAnnotations_whenAnalyzed_thenEachSourceOccurrenceKeepsItsOwnValue() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.Repeatable;
        @Repeatable(Numbers.class) @interface NumberValue { int value(); }
        @interface Numbers { NumberValue[] value(); }
        @NumberValue(1) @NumberValue(2) class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("NumberValue.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var sample = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    var annotations = sample.children(SyntaxRole.ANNOTATION);

    // Assert
    assertEquals(List.of("@NumberValue(1)", "@NumberValue(2)"), annotations.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(new AnnotationScalar.IntValue(1), new AnnotationScalar.IntValue(2)), annotations.stream()
        .map(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertNotEquals(annotations.getFirst().nodeId(), annotations.getLast().nodeId());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-annotation-attribution")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenCompetingNestedAnnotations_whenAnalyzed_thenRepeatableValuesMatchWrittenOccurrences() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.Repeatable;
        @Repeatable(Tags.class) @interface Tag { int value(); }
        @interface Tags { Tag[] value(); }
        @interface Wrapper { Tag[] value(); }
        @Wrapper({@Tag(10), @Tag(20)}) @Tag(1) @Tag(2) class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Tag.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var sample = result.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    var repeated = sample.children(SyntaxRole.ANNOTATION).stream().filter(node -> excerpt(source, node).startsWith("@Tag("))
        .toList();

    // Assert
    assertEquals(List.of("@Tag(1)", "@Tag(2)"), repeated.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(new AnnotationScalar.IntValue(1), new AnnotationScalar.IntValue(2)), repeated.stream()
        .map(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-annotation-attribution")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenRecordComponentOnlyScalarAnnotation_whenAnalyzed_thenComponentOwnsAttributedPair() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.RECORD_COMPONENT) @interface OnlyComponent { int value(); }
        record Sample(@OnlyComponent(7) int x) {}
        """;
    Files.writeString(sourceRoot.resolve("OnlyComponent.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var component = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION)
        .findFirst().orElseThrow();
    var annotation = component.requiredChild(SyntaxRole.ANNOTATION);
    assertEquals(1, annotation.children(SyntaxRole.ANNOTATION_ARGUMENT).size(), () -> result.diagnostics().toString());
    var pair = annotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);

    // Assert
    assertEquals("@OnlyComponent(7)", excerpt(source, annotation));
    assertEquals(new AnnotationScalar.IntValue(7), pair.value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals("type:OnlyComponent#value()", pair.value(SyntaxValue.AnnotationArgument.class).selectedElementKey());
    assertTrue(aggregate.semanticEdges().stream().anyMatch(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
        && edge.target() instanceof SyntaxSemanticTarget.AnnotationApplication target
        && target.declarationNodeKey().equals(Optional.of(component.nodeId()))));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-annotation-attribution")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenSameNamedRecordParameters_whenAnalyzed_thenEachParameterRetainsItsScalar() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.PARAMETER) @interface OnlyParameter { int value(); }
        record Sample(int x) {
          Sample(@OnlyParameter(7) int x) { this.x = x; }
          void m(@OnlyParameter(8) int x) {}
        }
        """;
    Files.writeString(sourceRoot.resolve("OnlyParameter.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var annotations = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation && excerpt(source, node).startsWith("@OnlyParameter("))
        .toList();

    // Assert
    assertEquals(List.of("@OnlyParameter(7)", "@OnlyParameter(8)"), annotations.stream()
        .map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(new AnnotationScalar.IntValue(7), new AnnotationScalar.IntValue(8)), annotations.stream()
        .map(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).value(SyntaxValue.AnnotationArgument.class).value()).toList(),
        () -> result.diagnostics().toString());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-annotation-attribution")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenUnavailableMirrorAndFaultedPositions_whenProjected_thenNoPairIsPublished() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("A.java"), "@interface A { int value(); }\n@interface B { int value(); }\n"
        + "@A(value=7) class Sample {}\n");
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var source = snapshot.sourceFiles().getFirst();
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var catalog = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(),
        JavaCompilerInvocation.from(configuration), catalog)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var declarations = unit.getTypeDecls().stream().map(ClassTree.class::cast).toList();
      var annotation = declarations.getLast().getModifiers().getAnnotations().getFirst();
      var wrongType = (TypeElement) compilation.semantics().element(TreePath.getPath(unit, declarations.get(1)));
      var written = compilation.treeInventory().annotationArguments(annotation).orElseThrow().getFirst();
      var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
      var projector = new JavaAnnotationValueProjector(compilation.semantics(), parsed.sourceEvidence(),
          compilation.treeInventory());
      var missingName = new JavaAnnotationValueProjector(compilation.semantics(),
          new JavaSourceEvidence(faultedPositions(parsed.sourceEvidence(), source, written.name().orElseThrow(), true)),
          compilation.treeInventory());
      var invalidValue = new JavaAnnotationValueProjector(compilation.semantics(),
          new JavaSourceEvidence(faultedPositions(parsed.sourceEvidence(), source, written.value(), false)),
          compilation.treeInventory());
      var unavailableDiagnostics = new DiagnosticEmissionCollector("source-observation", "unavailableDiagnostics", DiagnosticSubject.None.INSTANCE);
      var missingDiagnostics = new DiagnosticEmissionCollector("source-observation", "missingDiagnostics", DiagnosticSubject.None.INSTANCE);
      var invalidDiagnostics = new DiagnosticEmissionCollector("source-observation", "invalidDiagnostics", DiagnosticSubject.None.INSTANCE);

      // Act
      var unavailable = projector.arguments(unit, source, annotation, wrongType, path, unavailableDiagnostics);
      var missing = missingName.arguments(unit, source, annotation,
          (TypeElement) compilation.semantics().element(compilation.treeInventory().annotationTypePath(annotation).orElseThrow()),
          path, missingDiagnostics);
      var invalid = invalidValue.arguments(unit, source, annotation,
          (TypeElement) compilation.semantics().element(compilation.treeInventory().annotationTypePath(annotation).orElseThrow()),
          path, invalidDiagnostics);

      // Assert
      assertTrue(unavailable.value().failure().isPresent());
      assertTrue(missing.value().failure().isPresent());
      assertTrue(invalid.value().failure().isPresent());
      assertEquals("frontend.unavailable-annotation-attribution", unavailable.diagnostics().getFirst().code());
      assertEquals(source.sourceFileKey(), unavailable.diagnostics().getFirst().origins().getFirst().sourceFileKey());
      assertEquals("frontend.missing-source-position", missing.diagnostics().getFirst().code());
      assertEquals("frontend.invalid-source-range", invalid.diagnostics().getFirst().code());
      assertTrue(missing.diagnostics().getFirst().message().contains("A.java"));
      assertTrue(invalid.diagnostics().getFirst().message().contains("A.java"));
    }
  }

  private static SourcePositions faultedPositions(JavaSourceEvidence base, SourceFile source, Tree target, boolean missing) {
    return new SourcePositions() {
      @Override
      public long getStartPosition(CompilationUnitTree unit, Tree tree) {
        if (tree == target && missing) {
          return Diagnostic.NOPOS;
        }
        return ((JavaSourceEvidence.Position.Available) base.position(unit, source, tree)).span().startOffset();
      }

      @Override
      public long getEndPosition(CompilationUnitTree unit, Tree tree) {
        if (tree == target && !missing) {
          return source.content().length() + 1L;
        }
        return ((JavaSourceEvidence.Position.Available) base.position(unit, source, tree)).span().endOffsetExclusive();
      }
    };
  }

  @Test
  void givenEquivalentShorthandAndNamedValues_whenAnalyzed_thenWrittenStructureAndAggregateDiffer() throws IOException {
    // Arrange
    var declaration = "@interface NumberValue { int value(); }\n";
    var shorthand = declaration + "@NumberValue(7) class Sample {}\n";
    var named = declaration + "@NumberValue(value=7) class Sample {}\n";
    var file = sourceRoot.resolve("NumberValue.java");
    Files.writeString(file, shorthand);

    // Act
    var first = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var repeated = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var shorthandAnnotation = first.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation && excerpt(shorthand, node).startsWith("@NumberValue("))
        .findFirst().orElseThrow();
    Files.writeString(file, named);
    var second = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var namedAnnotation = second.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation && excerpt(named, node).startsWith("@NumberValue("))
        .findFirst().orElseThrow();
    var firstPair = shorthandAnnotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);
    var secondPair = namedAnnotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);

    // Assert
    assertEquals(first.aggregate(), repeated.aggregate());
    assertEquals(first.diagnostics(), repeated.diagnostics());
    assertEquals(new AnnotationScalar.IntValue(7), firstPair.value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(firstPair.value(SyntaxValue.AnnotationArgument.class).value(),
        secondPair.value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(firstPair.value(SyntaxValue.AnnotationArgument.class).selectedElementKey(),
        secondPair.value(SyntaxValue.AnnotationArgument.class).selectedElementKey());
    assertTrue(firstPair.value(SyntaxValue.AnnotationArgument.class).explicitName().isEmpty());
    assertEquals("value", secondPair.value(SyntaxValue.AnnotationArgument.class).explicitName().orElseThrow());
    assertEquals("value", secondPair.value(SyntaxValue.Symbol.class).name());
    assertTrue(firstPair.children(SyntaxRole.ANNOTATION_ELEMENT_NAME).isEmpty());
    assertEquals("value", excerpt(named, secondPair.requiredChild(SyntaxRole.ANNOTATION_ELEMENT_NAME)));
    assertNotEquals(first.aggregate(), second.aggregate());
  }

  @Test
  void givenEveryConstantOperatorFamily_whenAnalyzed_thenEachOperatorAndOperandSurvivesProjection() throws IOException {
    // Arrange
    var expressions = List.of("+1", "-(1)", "~1", "!true", "2*3", "6/2", "5%2", "1+2", "3-1", "1<<2",
        "4>>1", "-1>>>1", "1<2", "2>1", "1<=2", "2>=1", "1==1", "1!=2", "1&2", "1^2", "1|2",
        "true&&false", "true||false");
    var booleanIndices = Set.of(3, 12, 13, 14, 15, 16, 17, 21, 22);
    var source = new StringBuilder("@interface IntValue { int value(); }\n@interface BoolValue { boolean value(); }\n");
    for (var index = 0; index < expressions.size(); index++) {
      source.append('@').append(booleanIndices.contains(index) ? "BoolValue" : "IntValue").append('(')
          .append(expressions.get(index)).append(") class Sample").append(index).append(" {}\n");
    }
    Files.writeString(sourceRoot.resolve("Operators.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var applied = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation
        && node.value(SyntaxValue.Annotation.class).arguments().size() == 1).toList();
    var operators = applied.stream().flatMap(node -> node.descendantsAndSelf().stream())
        .filter(node -> node.value() instanceof SyntaxValue.Operator)
        .map(node -> node.value(SyntaxValue.Operator.class).operator()).collect(Collectors.toSet());

    // Assert
    assertEquals(expressions.size(), applied.size(), () -> result.diagnostics().toString());
    assertEquals(Set.of(SyntaxOperator.UNARY_PLUS, SyntaxOperator.UNARY_MINUS, SyntaxOperator.BITWISE_COMPLEMENT,
        SyntaxOperator.LOGICAL_COMPLEMENT, SyntaxOperator.MULTIPLY, SyntaxOperator.DIVIDE, SyntaxOperator.REMAINDER,
        SyntaxOperator.PLUS, SyntaxOperator.MINUS, SyntaxOperator.LEFT_SHIFT, SyntaxOperator.RIGHT_SHIFT,
        SyntaxOperator.UNSIGNED_RIGHT_SHIFT, SyntaxOperator.LESS_THAN, SyntaxOperator.GREATER_THAN, SyntaxOperator.LESS_THAN_EQUAL,
        SyntaxOperator.GREATER_THAN_EQUAL, SyntaxOperator.EQUAL_TO, SyntaxOperator.NOT_EQUAL_TO, SyntaxOperator.AND,
        SyntaxOperator.XOR, SyntaxOperator.OR, SyntaxOperator.CONDITIONAL_AND, SyntaxOperator.CONDITIONAL_OR), operators);
    assertTrue(applied.stream().allMatch(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
        .children(SyntaxRole.VALUE).size() == 1));
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenNominalPrimitiveArrayAndVoidClassLiterals_whenAnalyzed_thenEachKeepsItsTypeOperand() throws IOException {
    // Arrange
    var source = """
        @interface ClassValue { Class<?> value(); }
        @ClassValue(String.class) class Nominal {}
        @ClassValue(int.class) class Primitive {}
        @ClassValue(String[][].class) class Array {}
        @ClassValue(void.class) class VoidType {}
        """;
    Files.writeString(sourceRoot.resolve("ClassValue.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var annotations = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation
        && node.value(SyntaxValue.Annotation.class).form() == SyntaxValue.AnnotationForm.SINGLE_ELEMENT).toList();
    var operands = new ArrayList<SyntaxNode>();
    for (var annotation : annotations) {
      operands.add(annotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).requiredChild(SyntaxRole.VALUE)
          .requiredChild(SyntaxRole.TYPE_OPERAND));
    }

    // Assert
    assertEquals(4, operands.size(), () -> result.diagnostics().toString());
    assertEquals(List.of("String", "int", "String[][]", "void"), operands.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of("String.class", "int.class", "String[][].class", "void.class"), annotations.stream()
        .map(node -> excerpt(source, node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).requiredChild(SyntaxRole.VALUE))).toList());
    assertTrue(operands.stream().allMatch(node -> node.syntaxKind() == SyntaxKind.TYPE_USE));
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenEscapedStringTextBlockAndSignedZeros_whenAnalyzed_thenValuesStayTypedAndSourceSpellingSurvives() throws IOException {
    // Arrange
    var source = "@interface TextValue { String value(); }\n"
        + "@interface FloatValue { float value(); }\n@interface DoubleValue { double value(); }\n"
        + "@TextValue(\"line\\n\") class Escaped {}\n"
        + "@TextValue(\"\"\"\n    line\n    \"\"\") class Block {}\n"
        + "@FloatValue(-0.0f) class NegativeFloat {}\n@DoubleValue(-0.0d) class NegativeDouble {}\n";
    Files.writeString(sourceRoot.resolve("Values.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var pairs = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT).toList();

    // Assert
    assertEquals(4, pairs.size(), () -> result.diagnostics().toString());
    assertEquals(List.of(new AnnotationScalar.StringValue("line\n"), new AnnotationScalar.StringValue("line\n"),
        new AnnotationScalar.FloatValue(-0.0f), new AnnotationScalar.DoubleValue(-0.0d)),
        pairs.stream().map(node -> node.value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertNotEquals(excerpt(source, pairs.getFirst()), excerpt(source, pairs.get(1)));
    assertNotEquals(new AnnotationScalar.FloatValue(0.0f), pairs.get(2).value(SyntaxValue.AnnotationArgument.class).value());
    assertNotEquals(new AnnotationScalar.DoubleValue(0.0d), pairs.get(3).value(SyntaxValue.AnnotationArgument.class).value());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenNarrowingLimits_whenAnalyzed_thenSourceTypesStayIndependentOfConvertedValues() throws IOException {
    // Arrange
    var declaration = "@interface Narrow { byte b(); short s(); char c(); }\n";
    var file = sourceRoot.resolve("Narrow.java");
    Files.writeString(file, declaration + "@Narrow(b=-128,s=32767,c=65535) class Sample {}\n");

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var pairs = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT).toList();

    // Assert
    assertEquals(List.of(new AnnotationScalar.ByteValue((byte) -128), new AnnotationScalar.ShortValue((short) 32767),
        new AnnotationScalar.CharValue('\uffff')),
        pairs.stream().map(node -> node.value(SyntaxValue.AnnotationArgument.class).value()).toList());
    assertTrue(pairs.stream().allMatch(node -> ((SyntaxType.Resolved) node.requiredChild(SyntaxRole.VALUE).type())
        .identity().category() == JavaTypeCategory.INT));
    assertEquals(List.of(JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.CHAR), pairs.stream()
        .map(node -> ((SyntaxType.Resolved) node.type()).identity().category()).toList());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());

    for (var invalid : List.of("b=128,s=0,c=0", "b=0,s=32768,c=0", "b=0,s=0,c=65536")) {
      Files.writeString(file, declaration + "@Narrow(" + invalid + ") class Sample {}\n");
      var rejected = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
      assertTrue(rejected.aggregate().orElseThrow().nodes().stream()
          .noneMatch(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT), invalid);
      assertTrue(rejected.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")), invalid);
    }
  }

  @Test
  void givenQualifiedConstantsAndClassTypesWithSharedSimpleNames_whenAnalyzed_thenSelectedOwnersRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class First { static class Item {} }
        class Second { static class Item {} }
        @interface NumberValue { int value(); }
        @interface ClassValue { Class<?> value(); }
        @NumberValue(Long.BYTES) class NumberFirst {}
        @NumberValue(Double.BYTES) class NumberSecond {}
        @ClassValue(First.Item.class) class ClassFirst {}
        @ClassValue(Second.Item.class) class ClassSecond {}
        """;
    Files.writeString(sourceRoot.resolve("Owners.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var pairs = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT).toList();
    var firstReference = pairs.get(0).requiredChild(SyntaxRole.VALUE);
    var secondReference = pairs.get(1).requiredChild(SyntaxRole.VALUE);
    var firstType = pairs.get(2).requiredChild(SyntaxRole.VALUE).requiredChild(SyntaxRole.TYPE_OPERAND);
    var secondType = pairs.get(3).requiredChild(SyntaxRole.VALUE).requiredChild(SyntaxRole.TYPE_OPERAND);

    // Assert
    assertEquals(4, pairs.size(), () -> result.diagnostics().toString());
    assertEquals(new AnnotationScalar.IntValue(8), pairs.get(0).value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals(pairs.get(0).value(SyntaxValue.AnnotationArgument.class).value(),
        pairs.get(1).value(SyntaxValue.AnnotationArgument.class).value());
    assertEquals("type:java.lang.Long#field:BYTES", firstReference.semanticReference().orElseThrow().symbolKey().orElseThrow());
    assertEquals("type:java.lang.Double#field:BYTES", secondReference.semanticReference().orElseThrow().symbolKey().orElseThrow());
    assertEquals("type:First.Item", ((JavaTypeStructure.Declared) ((SyntaxType.Resolved) firstType.type())
        .structure().orElseThrow()).declarationKey());
    assertEquals("type:Second.Item", ((JavaTypeStructure.Declared) ((SyntaxType.Resolved) secondType.type())
        .structure().orElseThrow()).declarationKey());
    assertEquals(((SyntaxType.Resolved) firstType.type()).structure().orElseThrow(),
        ((AnnotationScalar.ClassLiteral) pairs.get(2).value(SyntaxValue.AnnotationArgument.class).value()).operandType());
    assertEquals(((SyntaxType.Resolved) secondType.type()).structure().orElseThrow(),
        ((AnnotationScalar.ClassLiteral) pairs.get(3).value(SyntaxValue.AnnotationArgument.class).value()).operandType());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenChangedScalarOperandsSelectionAndPairOrder_whenAnalyzed_thenFactsAndAggregatesDiffer() throws IOException {
    // Arrange
    var declaration = "@interface Pair { int first(); int second(); }\n";
    var forms = List.of("first=1+2,second=3", "first=2+1,second=3", "first=1+2,second=4",
        "first=3,second=1+2", "second=3,first=1+2");
    var file = sourceRoot.resolve("Pair.java");
    var aggregates = new ArrayList<JavaAstAggregate>();
    var pairs = new ArrayList<List<SyntaxNode>>();
    // Act
    for (var form : forms) {
      Files.writeString(file, declaration + "@Pair(" + form + ") class Sample {}\n");
      var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
      var aggregate = result.aggregate().orElseThrow();
      aggregates.add(aggregate);
      pairs.add(aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT).toList());
      assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
    }

    // Assert
    for (var index = 1; index < aggregates.size(); index++) {
      assertNotEquals(aggregates.getFirst(), aggregates.get(index));
    }
    assertEquals(pairs.getFirst().getFirst().value(SyntaxValue.AnnotationArgument.class).value(),
        pairs.get(1).getFirst().value(SyntaxValue.AnnotationArgument.class).value());
    assertNotEquals(pairs.getFirst().getFirst(), pairs.get(1).getFirst());
    assertNotEquals(pairs.getFirst().get(1).value(SyntaxValue.AnnotationArgument.class).value(),
        pairs.get(2).get(1).value(SyntaxValue.AnnotationArgument.class).value());
    assertNotEquals(pairs.getFirst().getFirst().value(SyntaxValue.AnnotationArgument.class).selectedElementKey(),
        pairs.get(4).getFirst().value(SyntaxValue.AnnotationArgument.class).selectedElementKey());
    assertEquals(List.of("second", "first"), pairs.get(4).stream()
        .map(node -> node.value(SyntaxValue.AnnotationArgument.class).explicitName().orElseThrow()).toList());
  }

  @Test
  void givenInvalidExplicitValues_whenAnalyzed_thenCompilerErrorsStayOwnedAndNoScalarPairIsPublished() throws IOException {
    // Arrange
    var invalid = List.of("@A(value=1,value=2)", "@A(other=1)", "@A", "@A(\"bad\")", "@A(null)",
        "@A(Data.mutable)", "@A(Data.supply())", "@A(new Object())", "@A((Data.mutable=1))");
    var sourceFile = sourceRoot.resolve("Invalid.java");

    for (var form : invalid) {
      Files.writeString(sourceFile, "@interface A { int value(); }\n"
          + "class Data { static int mutable; static int supply() { return 1; } }\n"
          + form + " class Sample {}\n");

      // Act
      var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
      var aggregate = result.aggregate().orElseThrow();
      var sample = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
          && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
      var annotation = sample.requiredChild(SyntaxRole.ANNOTATION);

      // Assert
      assertTrue(annotation.children(SyntaxRole.ANNOTATION_ARGUMENT).isEmpty(), () -> form + ": " + result.diagnostics());
      if (!annotation.value(SyntaxValue.Annotation.class).arguments().isEmpty()) {
        var failureCode = annotation.value(SyntaxValue.Annotation.class).argumentFailureCode().orElseThrow();
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(failureCode)), form);
      }
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")
          && diagnostic.origins().stream().anyMatch(origin -> origin.sourceFileKey().equals(annotation.sourceFileKey()))),
          () -> form + ": " + result.diagnostics());
      assertEquals(AnalysisCompletion.PARTIAL, new SourceAnalysisFixture().analyze(result).completion());
    }
  }

  private static String excerpt(String source, SyntaxNode node) {
    var range = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
  }
}
