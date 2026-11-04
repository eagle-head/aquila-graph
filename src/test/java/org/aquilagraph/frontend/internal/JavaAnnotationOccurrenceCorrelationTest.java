package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import javax.lang.model.element.TypeElement;
import javax.tools.ToolProvider;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class JavaAnnotationOccurrenceCorrelationTest {
  private static final String DECLARATIONS = """
      import java.lang.annotation.Repeatable;
      @Repeatable(Tags.class) @interface Tag { int value(); }
      @Repeatable(Groups.class) @interface Tags { Tag[] value(); }
      @interface Groups { Tags[] value(); }
      """;

  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenInvalidRepeatableMixtures_whenProduced_thenCompilerErrorsReturnAndValidNeighborSurvives() {
    // Arrange
    var cases = List.of("@Tag(1) @Tag(2) @Tags({@Tag(3)})", "@Tags({@Tag(1)}) @Tags({@Tag(2)}) @Groups({@Tags({@Tag(3)})})");
    // Act
    var observations = cases.stream().map(annotations -> DynamicTest.dynamicTest(
        "given" + (annotations.startsWith("@Tag(") ? "RepeatedBaseWithContainer" : "RepeatedContainersWithGroup")
            + "_whenProduced_thenCompilerErrorsReturnAndValidNeighborSurvives", () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(directory, List.of(
              SourceFile.fromContent(Path.of("Invalid.java"), DECLARATIONS + annotations + " class Invalid {}"),
              SourceFile.fromContent(Path.of("Neighbor.java"), "@Tag(41) class Neighbor {}")));
          var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS);
          var frontend = new JavaFrontend(new JavaSourceLoader());

          // Act
          var outcome = frontend.produce(request);
          var following = frontend.produce(new AstProductionRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(directory,
              List.of(SourceFile.fromContent(Path.of("Valid.java"), DECLARATIONS + "@Tag(43) class Following {}")))),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS));

          // Assert
          assertFalse(outcome.result().map(result -> result.completion() == AstCompletion.COMPLETE).orElse(false));
          var errors = outcome.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err.")).toList();
          assertEquals(List.of("compiler.err.invalid.repeatable.annotation.repeated.and.container.present"),
              errors.stream().map(diagnostic -> diagnostic.code()).toList(), outcome.diagnostics().toString());
          assertEquals(AnalysisOutcomeKind.COMPILER_ERROR, errors.getFirst().outcomeKind());
          assertEquals(DiagnosticStage.FRONTEND, errors.getFirst().stage());
          assertEquals(DiagnosticSeverity.ERROR, errors.getFirst().severity());
          assertEquals(snapshot.sourceFiles().getFirst().sourceFileKey(), errors.getFirst().origins().getFirst().sourceFileKey());
          var result = outcome.result().orElseThrow();
          var retained = AstCodec.decode(AstCodec.encode(result), result.identity());
          assertEquals(result.diagnostics(), retained.diagnostics());
          var neighbor = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
              && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Neighbor")).findFirst().orElseThrow();
          assertEquals("41", neighbor.requiredChild(SyntaxRole.ANNOTATION).requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
              .requiredChild(SyntaxRole.VALUE).value(SyntaxValue.Literal.class).text());
          assertEquals(AstCompletion.COMPLETE, following.completion(), following.diagnostics().toString());
          assertTrue(following.diagnostics().isEmpty());
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenValuedRecordPropagationTargets_whenProduced_thenIndependentCounterpartChecksPreserveCompleteValues() {
    // Arrange
    var cases = List.of(new RecordCase("FIELD", ""), new RecordCase("METHOD", ""), new RecordCase("PARAMETER", ""),
        new RecordCase("METHOD", "public int value() { return value; }"),
        new RecordCase("PARAMETER", "Sample(int value) { this.value = value; }"));
    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest("given" + value.target() + (value.body().isEmpty() ? "Derived" : "Explicit")
        + "RecordCounterpart_whenProduced_thenIndependentCounterpartChecksPreserveCompleteValues", () -> {
          // Arrange
          var source = "import java.lang.annotation.*; @Target(ElementType." + value.target()
              + ") @interface Mark { int value(); } record Sample(@Mark(23) int value) { " + value.body() + " }";
          var request = new AstProductionRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(directory,
              List.of(SourceFile.fromContent(Path.of("Sample.java"), source)))),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS);

          // Act
          var outcome = new JavaFrontend(new JavaSourceLoader()).produce(request);

          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          var values = outcome.result().orElseThrow().aggregate().nodes().stream()
              .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_ARGUMENT)
              .filter(node -> node.value(SyntaxValue.AnnotationArgument.class).selectedElementKey().startsWith("type:Mark#"))
              .map(node -> node.value(SyntaxValue.AnnotationArgument.class).value()).toList();
          assertEquals(List.of(new AnnotationScalar.IntValue(23)), values);
        }));

    // Assert
    return observations;
  }

  private record RecordCase(String target, String body) {
  }

  @Test
  void givenRepeatedBaseAndContainerOnDifferentCreatedDimensions_whenProduced_thenEachPhysicalGroupRemainsIndependent() {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @Repeatable(Tags.class) @interface Tag { int value(); }
        @Target(ElementType.TYPE_USE) @interface Tags { Tag[] value(); }
        class Sample { int[][] values = new int @Tag(1) @Tag(1)[2] @Tags({@Tag(3)})[4]; }
        """;
    var request = new AstProductionRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), source)))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS);

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(request);
    var retained = AstCodec.decode(AstCodec.encode(outcome.result().orElseThrow()), outcome.result().orElseThrow().identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var array = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_CREATION).findFirst().orElseThrow();
    assertEquals(List.of(2, 1), array.value(SyntaxValue.ArrayCreation.class).dimensionAnnotationCounts());
    assertEquals(3, array.children(SyntaxRole.ANNOTATION).size());
    assertTrue(array.children(SyntaxRole.ANNOTATION).stream()
        .allMatch(node -> node.value(SyntaxValue.Annotation.class).argumentFailureCode().isEmpty()));
  }

  @Test
  void givenRepeatedEqualValuesAndSingleBaseBesideContainer_whenProducedTwice_thenPhysicalOccurrencesAndValuesRemainDistinct() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Valid.java"), DECLARATIONS + """
        @Tag(7) @Tag(7) @Tag(9) class Repeated {}
        @Tag(11) @Tags({@Tag(13), @Tag(13)}) class Mixed {}
        """)));
    var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.produce(request).result().orElseThrow();
    var repeatedResult = frontend.produce(request).result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(result.identity(), repeatedResult.identity());
    assertEquals(result.aggregate(), retained.aggregate());
    for (var name : List.of("Repeated", "Mixed")) {
      var owner = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
          && node.value(SyntaxValue.TypeDeclaration.class).name().equals(name)).findFirst().orElseThrow();
      var annotations = owner.children(SyntaxRole.ANNOTATION);
      assertEquals(name.equals("Repeated") ? 3 : 2, annotations.size());
      assertEquals(annotations.size(), annotations.stream().map(node -> node.nodeId()).distinct().count());
      assertTrue(annotations.stream().allMatch(node -> node.value(SyntaxValue.Annotation.class).argumentFailureCode().isEmpty()));
      if (name.equals("Repeated")) {
        assertEquals(List.of(new AnnotationScalar.IntValue(7), new AnnotationScalar.IntValue(7), new AnnotationScalar.IntValue(9)),
            annotations.stream().map(node -> node.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
                .value(SyntaxValue.AnnotationArgument.class).value()).toList());
        assertEquals(annotations.stream().map(node -> node.origin().orElseThrow().startOffset()).sorted().toList(),
            annotations.stream().map(node -> node.origin().orElseThrow().startOffset()).toList());
      }
    }
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
  }

  @Test
  void givenInvalidRepeatableSource_whenExplicitRetentionIsRequested_thenPublicationIsRefusedBeforeRepositoryAcquisition() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Invalid.java"),
        DECLARATIONS + "@Tag(1) @Tag(2) @Tags({@Tag(3)}) class Invalid {}")));
    var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS);
    var service = AnalysisCapabilities.astRetention(ignored -> {
      throw new AssertionError("Incomplete annotation input must not acquire a repository");
    });

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(request);
    var publication = service.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
  }

  @Test
  void givenInvalidRepeatableRecordComponent_whenProduced_thenAdmittedPartialUnitRetainsCompilerAndCorrelationErrors() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Records.java"),
        DECLARATIONS + "record Sample(@Tag(1) @Tag(2) @Tags({@Tag(3)}) int value) {}")));
    var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_UNITS);

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(request);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var result = outcome.result().orElseThrow();
    assertEquals(1, result.aggregate().roots().size());
    assertEquals(Optional.of(snapshot), outcome.capturedInput());
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code()
        .equals("compiler.err.invalid.repeatable.annotation.repeated.and.container.present")
        && diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR));
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code()
        .equals("frontend.unavailable-record-annotation-propagation")));
    assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
  }

  @Test
  void givenAmbiguousOccurrenceAndUnrelatedEmission_whenArgumentsAreProjected_thenFailureRetainsItsOwnCreatingEvent() throws Exception {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Invalid.java"),
        "@interface Mark { int value(); } @Mark(1) @Mark(2) class Invalid {}")));
    var catalog = new SnapshotSourceCatalog(snapshot);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), catalog)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS));
      var unit = compilation.units().getFirst();
      var owner = (ClassTree) unit.getTypeDecls().getLast();
      var annotation = owner.getModifiers().getAnnotations().getFirst();
      var annotationType = (TypeElement) compilation.semantics().element(new TreePath(
          compilation.treeInventory().annotationPath(annotation).orElseThrow(), annotation.getAnnotationType()));
      var collector = new DiagnosticEmissionCollector("annotation-observation", "written-owner");
      var unrelated = collector.emit(FrontendDiagnosticFactory.otherError("neighbor", "frontend.neighbor-evidence",
          "Independent neighbor evidence", Optional.empty()));
      var projector = new JavaAnnotationValueProjector(compilation.semantics(), parsed.sourceEvidence(), compilation.treeInventory());

      // Act
      var projected = projector.arguments(unit, snapshot.sourceFiles().getFirst(), annotation, annotationType,
          SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0), collector);
      collector.include(projected.emissions());

      // Assert
      var failure = projected.value().failure().orElseThrow();
      assertFalse(projected.value().value().isPresent());
      assertEquals("frontend.unavailable-annotation-correlation", failure.diagnostic().code());
      assertEquals(AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE, failure.diagnostic().outcomeKind());
      assertEquals(List.of(failure), projected.emissions().events());
      assertSame(failure, collector.snapshot().events().getLast());
      assertSame(unrelated, collector.snapshot().events().getFirst());
    }
  }
}
