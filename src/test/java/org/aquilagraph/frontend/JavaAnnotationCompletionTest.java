package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.tools.ToolProvider;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.syntax.AnnotationScalar;
import org.aquilagraph.syntax.JavaAstAggregate;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.MethodBodyOrigin;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxSemanticEdge;
import org.aquilagraph.syntax.SyntaxSemanticRelation;
import org.aquilagraph.syntax.SyntaxSemanticTarget;
import org.aquilagraph.syntax.SyntaxSourceContribution;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaAnnotationCompletionTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String SIMPLE = "@interface A { int value() default 1; } @A(2) class Sample {}";
  @TempDir Path sourceRoot;

  @Test
  void givenCompleteAnnotationFormsAndValueFamilies_whenPublicAnalysisRuns_thenSourceFactsPermitCompletion() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), """
        import java.lang.annotation.*;
        @Retention(RetentionPolicy.SOURCE) @interface Marker {}
        @Retention(RetentionPolicy.CLASS) @interface Inner { int value(); }
        @Retention(RetentionPolicy.RUNTIME) @interface Values {
          int value() default 1;
          Inner nested() default @Inner(3);
          Inner[] many() default {@Inner(4), @Inner(5)};
          Class<?> kind() default int[].class;
          ElementType target() default ElementType.TYPE;
        }
        @Marker class First {}
        @Marker() @Values(2) class Second {}
        @Values(value=(int)(1+2),nested=@Inner(value=8),many={@Inner(6),@Inner(7)},kind=String.class,target=ElementType.METHOD)
        class Sample {}
        """);

    // Act
    var input = analyze();
    var run = new AnalysisEngine().analyze(input);
    var annotations = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).toList();

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, run.completion(), () -> input.diagnostics().toString());
    assertEquals(List.of(SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.EMPTY_NORMAL,
        SyntaxValue.AnnotationForm.SINGLE_ELEMENT, SyntaxValue.AnnotationForm.NAMED_NORMAL), annotations.stream()
        .map(node -> node.value(SyntaxValue.Annotation.class).form()).distinct().sorted().toList());
    assertTrue(input.diagnostics().isEmpty());
    assertEquals(5, input.aggregate().orElseThrow().nodes().stream().flatMap(node -> node.children(SyntaxRole.DEFAULT_VALUE).stream()).count());
    assertTrue(annotations.stream().allMatch(node -> node.semanticReference().orElseThrow().symbolKey().isPresent()));
  }

  @Test
  void givenEmptyAndDefaultedDeclarationSurfaces_whenObserved_thenFourExactPropertyManifestsIncludeEmptyLists() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), "@interface A { int value() default 1; } class Sample {}");

    // Act
    var input = analyze();
    var properties = input.languageFeatureCoverageResult().observations().stream()
        .filter(item -> item.classification().feature().category() == LanguageFeatureCategory.DECLARATION_PROPERTY).toList();

    // Assert
    assertEquals(List.of(JavaDeclarationPropertyCoverage.TYPE_ELEMENT_ANNOTATION_MIRRORS,
        JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_ANNOTATION_MIRRORS, JavaDeclarationPropertyCoverage.METHOD_DEFAULT_VALUE,
        JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_DEFAULT_VALUE, JavaDeclarationPropertyCoverage.TYPE_ELEMENT_ANNOTATION_MIRRORS),
        properties.stream().map(item -> item.classification().feature().identifier()).toList());
    assertTrue(properties.stream().allMatch(item -> item.origins().size() == 1 && item.classification().policy() == LanguageFeaturePolicy.REPRESENTED
        && item.diagnostic().isEmpty()));
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(input).completion());
  }

  @Test
  void givenAnnotationAndItsEntireCoverageBundleRemoved_whenReconciled_thenIndependentCaptureStillBlocksCompletion() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), SIMPLE);
    var original = analyze();
    withCompilation(SIMPLE, fixture -> {
      var annotation = application(fixture.aggregate());
      var changedRoot = rewrite(fixture.aggregate().roots().getFirst(), node -> {
        if (node.syntaxKind() != SyntaxKind.TYPE_DECLARATION || node.children(SyntaxRole.ANNOTATION).isEmpty()) {
          return node;
        }
        var facts = node.value(SyntaxValue.TypeDeclaration.class);
        var shape = facts.shape();
        var value = new SyntaxValue.TypeDeclaration(facts.name(), facts.declarationKey(), facts.writtenModifiers(), facts.effectiveModifiers(),
            new SyntaxValue.TypeDeclaration.Shape(0, shape.typeParameters(), shape.superclass(), shape.interfaces(),
                shape.permittedSubclasses(), shape.recordComponents(), shape.members()));
        return copy(node, value, node.children().stream().filter(child -> child.role() != SyntaxRole.ANNOTATION).toList(), node.semanticReference());
      });
      var damaged = seal(fixture, changedRoot);
      var range = annotation.origin().orElseThrow();
      var remaining = original.languageFeatureCoverageResult().observations().stream().filter(item -> item.origins().stream()
          .noneMatch(span -> span.startOffset() >= range.startOffset() && span.endOffsetExclusive() <= range.endOffsetExclusive())).toList();
      var removedBundle = JavaLanguageFeatureCoverage.result(original.languageFeatureCoverage(), remaining.stream()
          .map(item -> new LanguageFeatureOccurrence(item.sourceFileKey(), item.classification(), item.origins())).toList(), remaining, List.of());

      // Act
      var diagnostics = reconcile(fixture, damaged);
      var input = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), Optional.of(damaged),
          diagnostics, removedBundle);

      // Assert
      assertFalse(removedBundle.observations().stream().anyMatch(item -> item.classification().feature().identifier().equals("ANNOTATION")));
      assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(input).completion());
      assertEquals(List.of("frontend.unrepresented-source-annotation"), diagnostics.stream().map(AnalysisDiagnostic::code).toList());
      assertEquals(List.of(annotation.origin().orElseThrow()), diagnostics.getFirst().origins());
      assertNotEquals(fixture.aggregate(), damaged);
    });
  }

  @Test
  void givenWrittenDefaultAndItsPresenceValueRemoved_whenReconciled_thenIndependentDefaultObligationRemains() throws IOException {
    // Arrange
    withCompilation("@interface A { int value() default 1; }", fixture -> {
      var expression = fixture.aggregate().nodes().stream().flatMap(node -> node.children(SyntaxRole.DEFAULT_VALUE).stream()).findFirst().orElseThrow();
      var changedRoot = rewrite(fixture.aggregate().roots().getFirst(), node -> {
        if (!(node.value() instanceof SyntaxValue.ExecutableDeclaration facts) || facts.defaultValue().isEmpty()) {
          return node;
        }
        var value = new SyntaxValue.ExecutableDeclaration(facts.name(), facts.ownerKey(), facts.declarationKey(), facts.kind(),
            facts.writtenModifiers(), facts.effectiveModifiers(), facts.shape(),
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(facts.thrownTypes(), Optional.empty()));
        return copy(node, value, node.children().stream().filter(child -> child.role() != SyntaxRole.DEFAULT_VALUE).toList(), node.semanticReference());
      });
      var damaged = seal(fixture, changedRoot);

      // Act
      var diagnostics = reconcile(fixture, damaged);

      // Assert
      assertEquals(List.of("frontend.unrepresented-annotation-default"), diagnostics.stream().map(AnalysisDiagnostic::code).toList());
      assertEquals(List.of(expression.origin().orElseThrow()), diagnostics.getFirst().origins());
    });
  }

  @Test
  void givenSameSourceWithChangedScalarFact_whenReconciled_thenEqualCountsCannotHideSemanticDisagreement() throws IOException {
    // Arrange
    withCompilation(SIMPLE, fixture -> {
      var changedRoot = rewrite(fixture.aggregate().roots().getFirst(), node -> node.value() instanceof SyntaxValue.AnnotationArgument facts
          ? copy(node, new SyntaxValue.AnnotationArgument(facts.explicitName(), facts.selectedElementKey(), new AnnotationScalar.IntValue(9)),
              node.children(), node.semanticReference()) : node);
      var damaged = seal(fixture, changedRoot);

      // Act
      var diagnostics = reconcile(fixture, damaged);

      // Assert
      assertEquals(fixture.aggregate().nodes().size(), damaged.nodes().size());
      assertEquals(fixture.aggregate().rangeTuples(), damaged.rangeTuples());
      assertNotEquals(fixture.aggregate(), damaged);
      assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("frontend.inconsistent-annotation-source-fact")
          && item.message().contains("selected annotation element, type or value")));
    });
  }

  @Test
  void givenUnwrittenAnnotationWithPlausibleRangeAndTarget_whenReconciled_thenReverseInventoryCheckRejectsIt() throws IOException {
    // Arrange
    withCompilation(SIMPLE, fixture -> {
      var original = application(fixture.aggregate());
      var sample = fixture.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration type
          && type.name().equals("Sample")).findFirst().orElseThrow();
      var span = original.origin().orElseThrow();
      var fake = SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION,
          SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.MARKER, List.of(),
              original.value(SyntaxValue.Annotation.class).location(), original.value(SyntaxValue.Annotation.class).applicableTargets()),
          List.of(), original.semanticReference(), new SourceSpan(span.sourceFileKey(), sample.origin().orElseThrow().endOffsetExclusive() - 1,
              sample.origin().orElseThrow().endOffsetExclusive()));
      var root = rewrite(fixture.aggregate().roots().getFirst(), node -> node.nodeId().equals(original.nodeId()) ? fake : node);
      var damaged = seal(fixture, root);

      // Act
      var diagnostics = reconcile(fixture, damaged);

      // Assert
      assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("frontend.unrepresented-source-annotation")));
      assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("frontend.unexpected-annotation-source-fact")
          && item.origins().equals(List.of(fake.origin().orElseThrow()))));
    });
  }

  @Test
  void givenMissingPairNestedValueOrReferences_whenConstructed_thenRequiredShapeAndSemanticFactsRejectTheOmissions() throws IOException {
    // Arrange
    withCompilation("@interface Inner { int value(); } @interface A { Inner value(); } @A(@Inner(2)) class Sample {}", fixture -> {
      var annotation = application(fixture.aggregate());
      var pair = annotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);
      var nested = pair.requiredChild(SyntaxRole.VALUE);

      // Act
      var failures = List.of(
          assertThrows(IllegalArgumentException.class, () -> copy(annotation, annotation.value(), List.of(), annotation.semanticReference())),
          assertThrows(IllegalArgumentException.class, () -> copy(pair, pair.value(), List.of(), pair.semanticReference())),
          assertThrows(IllegalArgumentException.class, () -> copy(annotation, annotation.value(), annotation.children(), Optional.empty())),
          assertThrows(IllegalArgumentException.class, () -> copy(pair, pair.value(), pair.children(), Optional.empty())),
          assertThrows(IllegalArgumentException.class, () -> copy(nested, nested.value(), nested.children(), Optional.empty())));

      // Assert
      assertTrue(failures.stream().allMatch(failure -> !failure.getMessage().isBlank()));
    });
  }

  @Test
  void givenMissingDuplicateOrWrongTargetAndDuplicatePhysicalFacts_whenSealed_thenAggregateRejectsEachFault() throws IOException {
    // Arrange
    withCompilation(SIMPLE, fixture -> {
      var aggregate = fixture.aggregate();
      var root = aggregate.roots().getFirst();
      var nodes = aggregate.nodes();
      var target = aggregate.semanticEdges().stream().filter(edge -> edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).findFirst().orElseThrow();
      var noTarget = aggregate.semanticEdges().stream().filter(edge -> edge != target).toList();
      var duplicateEdges = new ArrayList<>(aggregate.semanticEdges());
      duplicateEdges.add(target);
      var duplicateNodes = new ArrayList<>(nodes);
      duplicateNodes.add(application(aggregate));
      var otherType = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration type && type.name().equals("A")).findFirst().orElseThrow();
      var wrongTarget = new SyntaxSemanticEdge(target.sourceKey(), target.relation(),
          new SyntaxSemanticTarget.AnnotationApplication(otherType.nodeId(), Optional.of(otherType.nodeId()), Optional.empty(), List.of(), List.of()));
      var wrongEdges = aggregate.semanticEdges().stream().map(edge -> edge == target ? wrongTarget : edge).toList();

      // Act
      var failures = List.of(
          assertThrows(IllegalArgumentException.class, () -> sealContribution(fixture.snapshot(), root, nodes, noTarget)),
          assertThrows(IllegalArgumentException.class, () -> sealContribution(fixture.snapshot(), root, nodes, duplicateEdges)),
          assertThrows(IllegalArgumentException.class, () -> sealContribution(fixture.snapshot(), root, duplicateNodes, aggregate.semanticEdges())),
          assertThrows(IllegalArgumentException.class, () -> sealContribution(fixture.snapshot(), root, nodes, wrongEdges)),
          assertThrows(IllegalArgumentException.class, () -> JavaAstAggregate.builder(fixture.snapshot()).add(new SyntaxSourceContribution(
              "foreign-source", root, nodes, aggregate.structuralEdges(), aggregate.semanticEdges()))));

      // Assert
      assertTrue(failures.stream().allMatch(failure -> !failure.getMessage().isBlank()));
    });
  }

  @Test
  void givenInvalidCapturedPositions_whenReconciled_thenInvalidRangeIsDistinctAndNeverFabricated() throws IOException {
    // Arrange
    withCompilation(SIMPLE, fixture -> {
      var captured = fixture.inventory().annotations().getFirst();
      var tree = captured.tree();
      var invalid = new JavaAnnotationSourceInventory.WrittenTree(tree.path(), tree.sourceKind(), tree.syntaxKind(), Optional.empty(),
          new JavaSourceEvidence.Position.Invalid(10, 5), tree.value(), tree.children());
      var inventory = new JavaAnnotationSourceInventory(List.of(new JavaAnnotationSourceInventory.Occurrence(captured.source(), invalid,
          captured.ownerRange(), captured.ancestors(), captured.ordinal())), fixture.inventory().declarations());

      // Act
      var diagnostics = new JavaAnnotationCompleteness(inventory, fixture.compilation().semantics(), List.of()).diagnostics(Optional.empty());

      // Assert
      assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("frontend.invalid-source-range")
          && item.message().contains("Sample.java") && item.origins().isEmpty()));
      assertFalse(diagnostics.stream().anyMatch(item -> item.code().equals("frontend.unavailable-source-annotation-range")));
    });
  }

  @Test
  void givenSameCapturedAnnotatedInput_whenAnalyzedTwice_thenFullOrderedFactsCoverageAndDiagnosticsAreEqual() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), "@interface Inner { int value(); } @interface A { Inner[] value(); } @A({@Inner(1),@Inner(2)}) class Sample {}");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = new JavaSourceLoader().discover(sourceRoot);

    // Act
    var first = frontend.analyze(snapshot, CONFIGURATION);
    var second = frontend.analyze(snapshot, CONFIGURATION);
    var left = first.aggregate().orElseThrow();
    var right = second.aggregate().orElseThrow();

    // Assert
    assertEquals(first.environmentSnapshot(), second.environmentSnapshot());
    assertEquals(left, right);
    assertEquals(left.orderedNodeKeys(), right.orderedNodeKeys());
    assertEquals(left.nodes().stream().map(SyntaxNode::value).toList(), right.nodes().stream().map(SyntaxNode::value).toList());
    assertEquals(left.nodes().stream().map(SyntaxNode::type).toList(), right.nodes().stream().map(SyntaxNode::type).toList());
    assertEquals(left.rangeTuples(), right.rangeTuples());
    assertEquals(left.structuralEdges(), right.structuralEdges());
    assertEquals(left.semanticEdges(), right.semanticEdges());
    assertEquals(first.languageFeatureCoverageResult(), second.languageFeatureCoverageResult());
    assertEquals(first.diagnostics(), second.diagnostics());
  }

  @Test
  void givenIndependentSourceAdded_whenAnalyzed_thenExistingAnnotationFactsAndDiagnosticIdentitiesStayStable() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), SIMPLE);
    var first = analyze();
    var key = first.snapshot().sourceFiles().getFirst().sourceFileKey();
    Files.writeString(sourceRoot.resolve("Independent.java"), "class Independent {}");

    // Act
    var second = analyze();

    // Assert
    assertEquals(first.aggregate().orElseThrow().nodes(), second.aggregate().orElseThrow().nodes().stream().filter(node -> node.sourceFileKey().equals(key)).toList());
    assertEquals(first.languageFeatureCoverageResult().observations(), second.languageFeatureCoverageResult().observations().stream().filter(item -> item.sourceFileKey().equals(key)).toList());
    assertEquals(first.diagnostics(), second.diagnostics());
  }

  private SyntaxAnalysisInput analyze() throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CONFIGURATION);
  }

  @Test
  void givenEqualSpanDimensionsWithSwappedAnnotationSubtrees_whenReconciled_thenCapturedAncestorTopologyRejectsRetargeting() throws IOException {
    // Arrange
    withCompilation("import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface A { int value(); } class Sample { String @A(1) [] @A(2) [] field; }", fixture -> {
      var annotations = fixture.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
          && node.value(SyntaxValue.Annotation.class).location() == SyntaxValue.AnnotationLocation.TYPE_USE).toList();
      assertEquals(2, annotations.size());
      var first = annotations.getFirst();
      var second = annotations.getLast();
      var root = rewrite(fixture.aggregate().roots().getFirst(), node -> node.nodeId().equals(first.nodeId())
          ? relocate(second, node.structuralPath()) : node.nodeId().equals(second.nodeId()) ? relocate(first, node.structuralPath()) : node);
      var damaged = seal(fixture, root);

      // Act
      var diagnostics = reconcile(fixture, damaged);

      // Assert
      assertEquals(fixture.aggregate().nodes().size(), damaged.nodes().size());
      assertEquals(fixture.aggregate().semanticEdges().size(), damaged.semanticEdges().size());
      assertNotEquals(fixture.aggregate(), damaged);
      assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("frontend.inconsistent-annotation-source-owner")));
    });
  }

  @Test
  void givenResolvedMarkerChangedToUnresolvedWithItsOutcomeRemoved_whenReconciled_thenMissingBlockingOutcomeIsDetected() throws IOException {
    // Arrange
    withCompilation("@interface A {} @A class Sample {}", fixture -> {
      var annotation = application(fixture.aggregate());
      var facts = annotation.value(SyntaxValue.Annotation.class);
      var changed = new SyntaxValue.Annotation(facts.form(), facts.arguments(), SyntaxValue.AnnotationLocation.UNRESOLVED);
      var root = rewrite(fixture.aggregate().roots().getFirst(), node -> node.nodeId().equals(annotation.nodeId())
          ? copy(node, changed, node.children(), node.semanticReference()) : node);
      var damaged = seal(fixture, root);

      // Act
      var diagnostics = reconcile(fixture, damaged);

      // Assert
      assertTrue(diagnostics.stream().anyMatch(item -> item.code().equals("frontend.inconsistent-annotation-source-fact")
          && item.message().contains("missing blocking annotation resolution outcome")));
      assertNotEquals(fixture.aggregate(), damaged);
    });
  }

  @Test
  void givenNestedValueChangedUnderSameCapturedSnapshot_whenReconciled_thenFullFactsAndValueOracleDistinguishIt() throws IOException {
    // Arrange
    withCompilation("@interface Inner { int value(); } @interface A { Inner value(); } @A(@Inner(2)) class Sample {}", fixture -> {
      var root = rewrite(fixture.aggregate().roots().getFirst(), node -> node.value() instanceof SyntaxValue.AnnotationArgument facts
          && facts.value() instanceof AnnotationScalar.IntValue
              ? copy(node, new SyntaxValue.AnnotationArgument(facts.explicitName(), facts.selectedElementKey(), new AnnotationScalar.IntValue(9)),
                  node.children(), node.semanticReference()) : node);
      var damaged = seal(fixture, root);

      // Act
      var diagnostics = reconcile(fixture, damaged);

      // Assert
      assertEquals(fixture.aggregate().snapshot(), damaged.snapshot());
      assertEquals(fixture.aggregate().rangeTuples(), damaged.rangeTuples());
      assertNotEquals(fixture.aggregate(), damaged);
      assertTrue(diagnostics.stream().anyMatch(item -> item.message().contains("selected annotation element, type or value")));
    });
  }

  private static SyntaxNode relocate(SyntaxNode node, SyntaxStructuralPath path) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        relocate(child.node(), path.child(child.role(), child.position())))).toList();
    return SyntaxNode.source(node.sourceFileKey(), path, node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children,
        node.semanticReference(), node.origin().orElseThrow());
  }

  @Test
  void givenCapturedDeclarationsWithoutRanges_whenReconciled_thenEverySurfaceKeepsDistinctSourceContext() throws IOException {
    // Arrange
    withCompilation("@interface A { int first(); int second(); } class Sample {}", fixture -> {
      var declarations = fixture.inventory().declarations().stream().map(item -> new JavaAnnotationSourceInventory.Declaration(item.source(),
          item.path(), Optional.empty(), item.properties(), item.defaultTree(), item.ordinal())).toList();
      var inventory = new JavaAnnotationSourceInventory(List.of(), declarations);

      // Act
      var diagnostics = new JavaAnnotationCompleteness(inventory, fixture.compilation().semantics(), List.of()).diagnostics(Optional.empty());

      // Assert
      assertEquals(4, diagnostics.size());
      assertEquals(4, diagnostics.stream().map(AnalysisDiagnostic::diagnosticKey).distinct().count());
      assertTrue(diagnostics.stream().allMatch(item -> item.code().equals("frontend.unrepresented-annotation-declaration")
          && item.message().contains("Sample.java") && item.origins().isEmpty()));
    });
  }

  @Test
  void givenUnknownAnnotationAndIndependentSourceAdded_whenAnalyzed_thenExistingBlockingDiagnosticsKeepTheirIdentities() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), "@Missing class Sample {}");
    var first = analyze();
    assertFalse(first.diagnostics().isEmpty());
    Files.writeString(sourceRoot.resolve("Independent.java"), "class Independent {}");

    // Act
    var second = analyze();

    // Assert
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(second).completion());
  }

  @Test
  void givenAnnotationFreePackageSourceWithAnExtraFact_whenReconciled_thenCapturedSourceOwnsTheBlockingOutcome() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), "package sample; class Sample {}");
    withCompilation("package-info.java", "package sample;", fixture -> {
      var original = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CONFIGURATION);
      var damaged = addUnwrittenAnnotation(fixture, "package-info.java", JavaSourceKind.PACKAGE, ElementType.PACKAGE);

      // Act
      var diagnostics = reconcile(fixture, damaged);
      var input = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), Optional.of(damaged),
          diagnostics, original.languageFeatureCoverageResult());

      // Assert
      assertEquals(List.of(), fixture.inventory().annotations());
      assertTrue(fixture.inventory().declarations().stream().noneMatch(declaration -> declaration.source().sourceFileKey()
          .equals(application(damaged).sourceFileKey())));
      assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(original).completion());
      assertEquals(List.of("frontend.unexpected-annotation-source-fact"), diagnostics.stream().map(AnalysisDiagnostic::code).toList());
      assertTrue(diagnostics.getFirst().message().contains("package-info.java"));
      assertEquals(application(damaged).origin().stream().toList(), diagnostics.getFirst().origins());
      assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(input).completion());
    });
  }

  @Test
  void givenAnnotationFreeModuleSourceWithAnExtraFact_whenReconciled_thenCapturedSourceOwnsTheBlockingOutcome() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Sample.java"), "package sample; class Sample {}");
    withCompilation("module-info.java", "module sample.module {}", fixture -> {
      var original = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CONFIGURATION);
      var damaged = addUnwrittenAnnotation(fixture, "module-info.java", JavaSourceKind.MODULE, ElementType.MODULE);

      // Act
      var diagnostics = reconcile(fixture, damaged);
      var input = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), Optional.of(damaged),
          diagnostics, original.languageFeatureCoverageResult());

      // Assert
      assertEquals(List.of(), fixture.inventory().annotations());
      assertTrue(fixture.inventory().declarations().stream().noneMatch(declaration -> declaration.source().sourceFileKey()
          .equals(application(damaged).sourceFileKey())));
      assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(original).completion());
      assertEquals(List.of("frontend.unexpected-annotation-source-fact"), diagnostics.stream().map(AnalysisDiagnostic::code).toList());
      assertTrue(diagnostics.getFirst().message().contains("module-info.java"));
      assertEquals(application(damaged).origin().stream().toList(), diagnostics.getFirst().origins());
      assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(input).completion());
    });
  }

  @Test
  void givenDualAnnotationWithItsDeclarationApplicationOmitted_whenApplicationsAreRecomputed_thenConstructionRejectsTheLoss() throws IOException {
    // Arrange
    withCompilation("""
        import java.lang.annotation.*;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        class Sample { @Both int value; }
        """, fixture -> {
      var written = fixture.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation annotation
          && annotation.location() == SyntaxValue.AnnotationLocation.DUAL).findFirst().orElseThrow();
      var facts = written.value(SyntaxValue.Annotation.class);
      var altered = new SyntaxValue.Annotation(facts.form(), facts.arguments(), SyntaxValue.AnnotationLocation.TYPE_USE,
          facts.applicableTargets(), facts.closestTypeAdmissible());
      var root = rewrite(fixture.aggregate().roots().getFirst(), node -> node.nodeId().equals(written.nodeId())
          ? copy(node, altered, node.children(), node.semanticReference()) : node);

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> seal(fixture, root));

      // Assert
      assertEquals(fixture.aggregate().nodes().size(), root.descendantsAndSelf().size());
      assertEquals("An annotation cannot omit its applicable source declaration", failure.getMessage());
    });
  }

  private static JavaAstAggregate addUnwrittenAnnotation(Fixture fixture, String sourcePath, JavaSourceKind ownerKind, ElementType target) {
    var source = fixture.snapshot().sourceFiles().stream().filter(file -> file.canonicalRelativePath().equals(sourcePath)).findFirst().orElseThrow();
    var owner = fixture.aggregate().nodes().stream()
        .filter(node -> node.sourceKind() == ownerKind && node.sourceFileKey().equals(source.sourceFileKey())).findFirst().orElseThrow();
    var ownerRange = owner.origin().orElseThrow();
    var annotation = SyntaxNode.source(owner.sourceFileKey(), owner.structuralPath().child(SyntaxRole.ANNOTATION, 0),
        JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.MARKER, List.of(), SyntaxValue.AnnotationLocation.DECLARATION, Set.of(target)),
        List.of(), Optional.of(new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED,
            Optional.of("type:Unwritten"), Optional.empty(), List.of(), List.of())),
        new SourceSpan(owner.sourceFileKey(), ownerRange.startOffset(), ownerRange.startOffset() + 1));
    var sourceRoot = fixture.aggregate().roots().stream().filter(node -> node.sourceFileKey().equals(owner.sourceFileKey()))
        .findFirst().orElseThrow();
    var root = rewrite(sourceRoot, node -> node.nodeId().equals(owner.nodeId())
        ? copy(node, node.value(), List.of(new SyntaxChild(SyntaxRole.ANNOTATION, 0, annotation)), node.semanticReference()) : node);
    return seal(fixture, root);
  }

  private void withCompilation(String source, Consumer<Fixture> assertion) throws IOException {
    withCompilation("Sample.java", source, assertion);
  }

  private void withCompilation(String filename, String source, Consumer<Fixture> assertion) throws IOException {
    Files.writeString(sourceRoot.resolve(filename), source);
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSession.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var parsed = session.parse();
      var inventory = JavaAnnotationSourceInventory.capture(parsed, sources);
      var compilation = session.analyze(parsed);
      var translation = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(), compilation.treeInventory(),
          JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION)).translate(snapshot, compilation.units());
      var fixture = new Fixture(snapshot, parsed, inventory, compilation, translation.value().aggregate().orElseThrow());
      assertEquals(List.of(), translation.diagnostics());
      assertEquals(List.of(), reconcile(fixture, fixture.aggregate()));
      assertion.accept(fixture);
    }
  }

  private static List<AnalysisDiagnostic> reconcile(Fixture fixture, JavaAstAggregate aggregate) {
    return new JavaAnnotationCompleteness(fixture.inventory(), fixture.compilation().semantics(), List.of()).diagnostics(Optional.of(aggregate));
  }

  private static SyntaxNode application(JavaAstAggregate aggregate) {
    return aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
        && node.value(SyntaxValue.Annotation.class).location() != SyntaxValue.AnnotationLocation.VALUE).findFirst().orElseThrow();
  }

  private static SyntaxNode rewrite(SyntaxNode node, UnaryOperator<SyntaxNode> replacement) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), rewrite(child.node(), replacement))).toList();
    return replacement.apply(copy(node, node.value(), children, node.semanticReference()));
  }

  private static SyntaxNode copy(SyntaxNode node, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> reference) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children,
        reference, node.origin().orElseThrow());
  }

  private static JavaAstAggregate seal(Fixture fixture, SyntaxNode root) {
    var roots = fixture.aggregate().roots().stream().map(original -> original.sourceFileKey().equals(root.sourceFileKey())
        ? root : original).toList();
    var nodes = roots.stream().flatMap(sourceRoot -> sourceRoot.descendantsAndSelf().stream()).toList();
    var keys = nodes.stream().map(SyntaxNode::nodeId).toList();
    var edges = new ArrayList<>(fixture.aggregate().semanticEdges().stream()
        .filter(edge -> edge.relation() != SyntaxSemanticRelation.ANNOTATED_TARGET && keys.contains(edge.sourceKey())).toList());
    edges.addAll(nodes.stream().filter(node -> !fixture.aggregate().orderedNodeKeys().contains(node.nodeId()))
        .flatMap(node -> node.semanticEdges().stream()).toList());
    nodes.forEach(parent -> parent.children(SyntaxRole.ANNOTATION).stream()
        .filter(annotation -> annotation.value(SyntaxValue.Annotation.class).location() != SyntaxValue.AnnotationLocation.UNRESOLVED)
        .forEach(annotation -> edges.add(new SyntaxSemanticEdge(annotation.nodeId(),
        SyntaxSemanticRelation.ANNOTATED_TARGET, SyntaxSemanticTarget.AnnotationApplication.forAttachment(parent, annotation)))));
    var builder = JavaAstAggregate.builder(fixture.snapshot());
    for (var sourceRoot : roots) {
      var ownedNodes = sourceRoot.descendantsAndSelf();
      var ownedKeys = ownedNodes.stream().map(SyntaxNode::nodeId).toList();
      builder.add(new SyntaxSourceContribution(sourceRoot.sourceFileKey(), sourceRoot, ownedNodes,
          ownedNodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(),
          edges.stream().filter(edge -> ownedKeys.contains(edge.sourceKey())).toList()));
    }
    for (var key : fixture.aggregate().methodBodyKeys()) {
      var original = fixture.aggregate().methodSyntax(key).orElseThrow().root();
      var origin = fixture.aggregate().methodBodyOrigin(key).orElseThrow();
      var body = origin == MethodBodyOrigin.SOURCE_AST
          ? nodes.stream().filter(node -> node.nodeId().equals(original.nodeId())).findFirst().orElseThrow() : original;
      builder.addMethodBody(key, body, origin);
    }
    return builder.seal();
  }

  private static JavaAstAggregate sealContribution(InputSnapshot snapshot, SyntaxNode root, List<SyntaxNode> nodes, List<SyntaxSemanticEdge> edges) {
    return JavaAstAggregate.builder(snapshot).add(new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
        nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), edges)).seal();
  }

  private record Fixture(InputSnapshot snapshot, ParsedJavaSource parsed, JavaAnnotationSourceInventory inventory,
      JavaCompilation compilation, JavaAstAggregate aggregate) {
  }
}
