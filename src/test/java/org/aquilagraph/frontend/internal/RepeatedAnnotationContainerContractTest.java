package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class RepeatedAnnotationContainerContractTest {
  private static final String DECLARATIONS = """
      import java.lang.annotation.Repeatable;
      @Repeatable(Tags.class) @interface Tag { int value(); }
      @Repeatable(Groups.class) @interface Tags { Tag[] value(); }
      @interface Groups { Tags[] value(); }
      """;

  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenExplicitRepeatedContainersAndLegalMixtures_whenProducedAndDecoded_thenOrderedOccurrencesAndNestedValuesRemainExact() {
    // Arrange
    var cases = List.of(
        new ContainerCase("DistinctRepeatedContainers", List.of(tags(11, 13), tags(17))),
        new ContainerCase("ReversedRepeatedContainers", List.of(tags(17), tags(11, 13))),
        new ContainerCase("EqualRepeatedContainers", List.of(tags(7, 7), tags(7, 7))),
        new ContainerCase("EmptyAndPopulatedContainers", List.of(tags(), tags(19))),
        new ContainerCase("SingleBaseBesideContainer", List.of(tag(11), tags(13, 13))),
        new ContainerCase("ContainerBeforeSingleBase", List.of(tags(13, 13), tag(11))),
        new ContainerCase("SingleContainerBesideGroup", List.of(tags(23), container("Groups", tags(29)))),
        new ContainerCase("ExplicitNestedGroup", List.of(container("Groups", tags(31, 37), tags(41)))));

    // Act
    var observations = cases.stream().map(variant -> DynamicTest.dynamicTest(
        "given" + variant.name() + "_whenProducedAndDecoded_thenOrderedOccurrencesAndNestedValuesRemainExact", () -> {
          // Arrange
          var source = DECLARATIONS + variant.annotations().stream().map(ExpectedAnnotation::spelling).collect(Collectors.joining(" "))
              + " class Subject {}";
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Subject.java")), source)));

          // Act
          var outcome = JavaFrontends.standard().produce(request(snapshot));
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
          for (var result : List.of(original, decoded)) {
            assertEquals(AstCompletion.COMPLETE, result.completion());
            assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, result.profile());
            assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
            assertAnnotations(result.aggregate(), source, declaration(result, "Subject"), variant.annotations());
          }
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidRepeatedBaseContainerMixtures_whenProducedAndDecoded_thenSpecificCompilerErrorAndValidNeighborRemainExact() {
    // Arrange
    var cases = List.of(
        new ContainerCase("RepeatedBaseWithContainer", List.of(tag(1), tag(2), tags(3))),
        new ContainerCase("RepeatedContainersWithGroup", List.of(tags(1), tags(2), container("Groups", tags(3)))));

    // Act
    var observations = cases.stream().map(variant -> DynamicTest.dynamicTest(
        "given" + variant.name() + "_whenProducedAndDecoded_thenSpecificCompilerErrorAndValidNeighborRemainExact", () -> {
          // Arrange
          var invalid = DECLARATIONS + variant.annotations().stream().map(ExpectedAnnotation::spelling).collect(Collectors.joining(" "))
              + " class Invalid {}";
          var neighborSource = "@Tag(43) class Neighbor {}";
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Invalid.java")), invalid),
              SourceFile.fromContent(capturedPath(Path.of("Neighbor.java")), neighborSource)));

          // Act
          var outcome = JavaFrontends.standard().produce(request(snapshot));
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertFalse(outcome.completion() == AstCompletion.COMPLETE);
          for (var result : List.of(original, decoded)) {
            assertEquals(AstCompletion.INCOMPLETE, result.completion());
            var errors = result.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err.")).toList();
            assertEquals(List.of("compiler.err.invalid.repeatable.annotation.repeated.and.container.present"),
                errors.stream().map(diagnostic -> diagnostic.code()).toList(), result.diagnostics().toString());
            var error = errors.getFirst();
            assertEquals(AnalysisOutcomeKind.COMPILER_ERROR, error.outcomeKind());
            assertEquals(DiagnosticSeverity.ERROR, error.severity());
            assertEquals(DiagnosticStage.FRONTEND, error.stage());
            assertEquals(1, error.origins().size());
            var origin = error.origins().getFirst();
            assertEquals(snapshot.sourceFiles().getFirst().sourceFileKey(), origin.sourceFileKey());
            var errorSpelling = variant.annotations().getLast().spelling();
            var start = invalid.indexOf(errorSpelling, DECLARATIONS.length());
            assertEquals(new SourceSpan(origin.sourceFileKey(), start, start + errorSpelling.length()), origin);
            assertAnnotations(result.aggregate(), neighborSource, declaration(result, "Neighbor"), List.of(tag(43)));
          }
        }));

    // Assert
    return observations;
  }

  private static AstProductionRequest request(InputSnapshot snapshot) {
    return new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static SyntaxNode declaration(AstResult result, String name) {
    var matches = result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value
        && value.name().equals(name)).toList();
    assertEquals(1, matches.size(), name);
    return matches.getFirst();
  }

  private static void assertAnnotations(JavaAstAggregate aggregate, String source, SyntaxNode owner, List<ExpectedAnnotation> expected) {
    var actual = owner.children(SyntaxRole.ANNOTATION);
    assertEquals(expected.size(), actual.size());
    assertEquals(expected.size(), actual.stream().map(SyntaxNode::nodeId).distinct().count());
    var cursor = Math.toIntExact(owner.origin().orElseThrow().startOffset());
    for (var index = 0; index < expected.size(); index++) {
      var annotation = actual.get(index);
      var value = expected.get(index);
      var start = source.indexOf(value.spelling(), cursor);
      assertAnnotation(aggregate, source, annotation, value, start);
      assertEquals(owner.structuralPath().child(SyntaxRole.ANNOTATION, index), annotation.structuralPath());
      var applications = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
          && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).toList();
      assertEquals(1, applications.size());
      assertEquals(new AnnotationApplication(owner.nodeId(), Optional.of(owner.nodeId()), Optional.empty(), List.of(), List.of()),
          applications.getFirst().target());
      cursor = start + value.spelling().length();
    }
  }

  private static void assertAnnotation(JavaAstAggregate aggregate, String source, SyntaxNode annotation, ExpectedAnnotation expected, int start) {
    assertEquals(SyntaxKind.ANNOTATION, annotation.syntaxKind());
    assertEquals(typeKey(expected.type()), annotation.requiredReference().symbolKey().orElseThrow());
    assertEquals(new SourceSpan(annotation.sourceFileKey(), start, start + expected.spelling().length()), annotation.origin().orElseThrow());
    assertEquals(1, aggregate.nodes().stream().filter(node -> node.nodeId().equals(annotation.nodeId())).count());
    var arguments = annotation.children(SyntaxRole.ANNOTATION_ARGUMENT);
    assertEquals(1, arguments.size());
    var argument = arguments.getFirst();
    assertEquals(annotation.structuralPath().child(SyntaxRole.ANNOTATION_ARGUMENT, 0), argument.structuralPath());
    assertEquals(typeKey(expected.type()) + "#value()", argument.value(SyntaxValue.AnnotationArgument.class).selectedElementKey());
    var expression = argument.requiredChild(SyntaxRole.VALUE);
    assertEquals(argument.structuralPath().child(SyntaxRole.VALUE, 0), expression.structuralPath());
    assertTrue(annotation.value(SyntaxValue.Annotation.class).argumentFailureCode().isEmpty());
    if (expected.scalar() != null) {
      assertEquals(new AnnotationScalar.IntValue(expected.scalar()), argument.value(SyntaxValue.AnnotationArgument.class).value());
      assertEquals(expected.scalar().toString(), expression.value(SyntaxValue.Literal.class).text());
      return;
    }
    var items = expression.children(SyntaxRole.VALUE);
    assertEquals(expected.elements().size(), items.size());
    assertEquals(new AnnotationElementValue.Array(expected.elements().stream()
        .<AnnotationElementValue>map(item -> new AnnotationElementValue.Nested(typeKey(item.type()))).toList()),
        argument.value(SyntaxValue.AnnotationArgument.class).value());
    var cursor = start + expected.type().length() + 3;
    for (var index = 0; index < items.size(); index++) {
      var child = items.get(index);
      var item = expected.elements().get(index);
      var itemStart = source.indexOf(item.spelling(), cursor);
      assertEquals(expression.structuralPath().child(SyntaxRole.VALUE, index), child.structuralPath());
      assertEquals(SyntaxValue.AnnotationLocation.VALUE, child.value(SyntaxValue.Annotation.class).location());
      assertTrue(aggregate.semanticEdges().stream().noneMatch(edge -> edge.sourceKey().equals(child.nodeId())
          && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET));
      assertAnnotation(aggregate, source, child, item, itemStart);
      cursor = itemStart + item.spelling().length();
    }
  }

  private static String typeKey(String name) {
    return new JavaTypeDeclarationIdentity(Optional.empty(), name).declarationKey();
  }

  private static ExpectedAnnotation tag(int value) {
    return new ExpectedAnnotation("Tag", "@Tag(" + value + ")", List.of(), value);
  }

  private static ExpectedAnnotation tags(int... values) {
    return container("Tags", Arrays.stream(values).mapToObj(RepeatedAnnotationContainerContractTest::tag).toArray(ExpectedAnnotation[]::new));
  }

  private static ExpectedAnnotation container(String type, ExpectedAnnotation... elements) {
    return new ExpectedAnnotation(type, "@" + type + "({" + Arrays.stream(elements).map(ExpectedAnnotation::spelling)
        .collect(Collectors.joining(", ")) + "})", List.of(elements), null);
  }

  private record ContainerCase(String name, List<ExpectedAnnotation> annotations) {}
  private record ExpectedAnnotation(String type, String spelling, List<ExpectedAnnotation> elements, Integer scalar) {}
}
