package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CoverageActualFacts;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class CoverageNavigationContractTest {
  private static final String PACKAGE_ANNOTATIONS = "CompilationUnitTree::CompilationUnitTree#getPackageAnnotations()";
  private static final String ARRAY_ANNOTATIONS = "NewArrayTree::NewArrayTree#getAnnotations()";
  private static final String DIMENSION_ANNOTATIONS = "NewArrayTree::NewArrayTree#getDimAnnotations()";
  private static final String INITIALIZER = "VariableTree::VariableTree#getInitializer()";
  private static final String CREATION_TYPE_ARGUMENTS = "NewClassTree::NewClassTree#getTypeArguments()";
  private static final String REFERENCE_TYPE_ARGUMENTS = "MemberReferenceTree::MemberReferenceTree#getTypeArguments()";
  private static final String ACCESSOR_SHAPES = """
      enum Example {
        PLAIN, POPULATED(2) {};
        int field;
        Example() {}
        Example(int value) {}
        static void execute() {}
        Object construct() { return new Object(); }
        Runnable reference() { return Example::execute; }
      }
      """;
  private static final String PACKAGE_SOURCE = "@sample.First @sample.Second(/* value */ 8) package sample;";
  private static final String EXAMPLE_SOURCE = """
      package sample;
      import java.lang.annotation.*;
      @Target(ElementType.PACKAGE) @interface First {}
      @Target(ElementType.PACKAGE) @interface Second { int value(); }
      @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
      class Example {
        int supported() { return 7; }
        int[] subject(int n) {
          int[][][] sized = new int @Mark(2)[n] [n] @Mark(2)[n];
          return new int @Mark(5)[]{1, 1};
        }
      }
      """;

  @TempDir Path directory;

  @Test
  void givenWrittenPackageAndArrayAnnotations_whenConsumed_thenOrderAndSpellingSurviveRetentionAndCfgRefusal() throws IOException {
    // Arrange
    var snapshot = snapshot();
    var packageAnnotations = items(item("ANNOTATION:0", "@sample.First"), item("ANNOTATION:1", "@sample.Second(/* value */ 8)"));
    var arrayAnnotations = items(item("TYPE_ANNOTATION:0", "@Mark(5)"));
    var dimensionAnnotations = items(item("dimension:0", "1"), item("dimension:0/TYPE_ANNOTATION:0", "@Mark(2)"),
        item("dimension:1", "0"), item("dimension:2", "1"), item("dimension:2/TYPE_ANNOTATION:0", "@Mark(2)"));

    // Act
    var original = produce(snapshot);
    var bytes = AstCodec.encode(original);
    var retained = AstCodec.decode(bytes, original.identity());
    var keys = List.of("supported", "subject").stream().map(name -> retained.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(AstOutcome.available(retained),
        new CfgRequest(retained.identity(), keys, CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(List.of(), original.qualification().reasons());
    assertEquals(original.qualification(), retained.qualification());
    assertEquals(original.coverage(), retained.coverage());
    assertEquals(AstCodec.materialize(original), AstCodec.materialize(retained));
    assertProperty(retained.coverage(), PACKAGE_ANNOTATIONS, packageAnnotations);
    assertProperty(retained.coverage(), ARRAY_ANNOTATIONS, arrayAnnotations);
    assertProperty(retained.coverage(), DIMENSION_ANNOTATIONS, dimensionAnnotations);
    var array = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_CREATION
        && node.children(SyntaxRole.ANNOTATION).size() == 2).findFirst().orElseThrow();
    var repeated = array.children(SyntaxRole.ANNOTATION);
    assertEquals(List.of("@Mark(2)", "@Mark(2)"), repeated.stream().map(node -> text(node, EXAMPLE_SOURCE)).toList());
    assertNotEquals(repeated.getFirst().nodeId(), repeated.getLast().nodeId());
    assertTrue(repeated.getFirst().origin().orElseThrow().endOffsetExclusive() < repeated.getLast().origin().orElseThrow().startOffset());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
        run.representation().cfg().orElseThrow().methods().stream().map(method -> method.state()).toList());
    var consumed = run.representation().ast().result().orElseThrow();
    assertEquals(retained.identity(), consumed.identity());
    assertArrayEquals(bytes, AstCodec.encode(consumed));
  }

  @Test
  void givenFrozenPackageAnnotationExpectations_whenCopiedSpanChanges_thenMatchingAndReadmissionRefuseIt() throws IOException {
    // Arrange
    var original = produce(snapshot());
    var input = SourceValueCapture.capture(original);
    var selected = property(input.languageFeatureCoverageResult(), PACKAGE_ANNOTATIONS);
    var context = input.languageFeatureCoverageResult().validationEvidence().writtenInventory().contexts().stream()
        .filter(candidate -> candidate.address().equals(selected.address())).findFirst().orElseThrow();
    var expected = items(item("ANNOTATION:0", "@sample.First"), item("ANNOTATION:1", "@sample.Second(/* value */ 8)"));
    var changed = extendFirstPackageAnnotation(original.aggregate(), selected.address().sourceFileKey());
    var originalFacts = CoverageActualFacts.capture(original.program(), input.aggregate());
    var changedFacts = CoverageActualFacts.capture(original.program(), Optional.of(changed));
    var originalOwners = originalFacts.ownedBy(context);
    var changedOwners = changedFacts.ownedBy(context);

    // Act
    var originalMatch = CoverageActualFacts.supportsProperty(context, expected, originalOwners, originalFacts);
    var changedMatch = CoverageActualFacts.supportsProperty(context, expected, changedOwners, changedFacts);
    var failure = assertThrows(IllegalArgumentException.class, () -> JavaCoverageTestSupport.capture(input, original.profile()).withFacts(original.program(), Optional.of(changed)));

    // Assert
    var changedRoot = changed.roots().stream().filter(root -> root.sourceFileKey().equals(selected.address().sourceFileKey()))
        .findFirst().orElseThrow();
    var changedAnnotation = changedRoot.requiredChild(SyntaxRole.PACKAGE_DECLARATION).children(SyntaxRole.ANNOTATION).getFirst();
    assertEquals("@sample.First ", text(changedAnnotation, PACKAGE_SOURCE));
    assertEquals(expected, context.writtenValue().orElseThrow());
    assertEquals(originalOwners, changedOwners);
    assertTrue(originalMatch);
    assertFalse(changedMatch);
    assertTrue(failure.getMessage().startsWith("Tree support must match the exact retained kind and captured owner topology"));
  }

  @TestFactory
  Stream<DynamicTest> givenUnchangedActualAnnotations_whenBothClaimsUseWrongSpelling_thenCopiedAdmissionRejectsSupport() {
    return Stream.of(PACKAGE_ANNOTATIONS, ARRAY_ANNOTATIONS, DIMENSION_ANNOTATIONS).map(accessor -> DynamicTest.dynamicTest(
        "givenUnchangedActualAnnotations_whenWrongSpellingIsClaimedFor" + accessor + "_thenCopiedAdmissionRejectsSupport", () -> {
          // Arrange
          var original = produce(snapshot());
          var input = SourceValueCapture.capture(original);
          var selected = property(input.languageFeatureCoverageResult(), accessor);
          var changedItems = new ArrayList<>(selected.encounteredValue().orElseThrow().items());
          var item = changedItems.getLast();
          changedItems.set(changedItems.size() - 1, item(item.identity(), item.value() + " "));
          var coverage = withChangedWrittenProperty(input, selected.address(), new CoveragePropertyValue(State.ITEMS, changedItems),
              selected.support());

          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(),
              input.environmentSnapshot(), input.program(), input.aggregate(), input.diagnostics(), coverage));

          // Assert
          assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenActualAccessorCardinality_whenCopiedClaimsUseAnotherShape_thenReadmissionRejectsSupport() {
    return Stream.of(new NegativeClaim("PlainEnumConstant", INITIALIZER, State.EMPTY, State.ABSENT),
        new NegativeClaim("PopulatedEnumConstant", INITIALIZER, State.ITEMS, State.ABSENT),
        new NegativeClaim("CreationWithoutTypeArguments", CREATION_TYPE_ARGUMENTS, State.EMPTY, State.ABSENT),
        new NegativeClaim("MemberReferenceWithoutTypeArguments", REFERENCE_TYPE_ARGUMENTS, State.ABSENT, State.EMPTY))
        .map(claim -> DynamicTest.dynamicTest("given" + claim.owner() + "_whenOtherCardinalityIsClaimed_thenReadmissionRejectsSupport", () -> {
          // Arrange
          var captured = InputSnapshot.fromSources(capturedAbsolute(directory),
              List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), ACCESSOR_SHAPES)));
          var original = produce(captured);
          var input = SourceValueCapture.capture(original);
          var selected = property(input.languageFeatureCoverageResult(), claim.accessor(), claim.actual());
          var changed = new CoveragePropertyValue(claim.supplied(), List.of());
          var negative = new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.NEGATIVE, List.of(), Optional.empty(),
              Optional.empty(), Optional.of(changed));
          var coverage = withChangedWrittenProperty(input, selected.address(), changed, negative);

          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(),
              input.environmentSnapshot(), input.program(), input.aggregate(), input.diagnostics(), coverage));

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          if (claim.actual() == State.ITEMS) {
            assertEquals(List.of("2", "POPULATED(2) {}"), selected.encounteredValue().orElseThrow().items().stream().map(Item::value).toList());
          }
          assertEquals("Claimed negative coverage support requires an actual existing owner with absent or empty accessor state",
              failure.getMessage());
        }));
  }

  private InputSnapshot snapshot() {
    return InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("sample", "Example.java")), EXAMPLE_SOURCE),
        SourceFile.fromContent(capturedPath(Path.of("sample", "package-info.java")), PACKAGE_SOURCE)));
  }

  private static AstResult produce(InputSnapshot snapshot) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES).result().orElseThrow();
  }

  private static FeatureContextOutcome property(LanguageFeatureCoverageResult coverage, String accessor) {
    return property(coverage, accessor, State.ITEMS);
  }

  private static FeatureContextOutcome property(LanguageFeatureCoverageResult coverage, String accessor, State state) {
    var supported = coverage.validationEvidence().outcomes().stream()
        .filter(outcome -> accessor.equals(outcome.address().role()) && outcome.encounteredValue().filter(value -> value.state() == state)
            .isPresent())
        .toList();
    assertEquals(1, supported.size(), accessor);
    return supported.getFirst();
  }

  private static void assertProperty(LanguageFeatureCoverageResult coverage, String accessor, CoveragePropertyValue expected) {
    var outcome = property(coverage, accessor);
    var context = coverage.validationEvidence().writtenInventory().contexts().stream()
        .filter(candidate -> candidate.address().equals(outcome.address())).findFirst().orElseThrow();
    assertEquals(expected, context.writtenValue().orElseThrow(), accessor);
    assertEquals(expected, outcome.encounteredValue().orElseThrow(), accessor);
  }

  private static Item item(String identity, String value) {
    return new Item(identity, value, Provenance.WRITTEN);
  }

  private static CoveragePropertyValue items(Item... items) {
    return new CoveragePropertyValue(State.ITEMS, List.of(items));
  }

  private static String text(SyntaxNode node, String source) {
    var span = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }

  private static LanguageFeatureCoverageResult withChangedWrittenProperty(SyntaxAnalysisInput input, SourceContext.Address address,
      CoveragePropertyValue changedValue, FeatureContextOutcome.Support support) {
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var original = evidence.writtenInventory();
    var contexts = original.contexts().stream().map(context -> context.address().equals(address)
        ? new SourceContext(address, context.feature(), context.origins(), Optional.of(changedValue), false) : context).toList();
    var written = new SourceCoverageInventory(input.snapshot(), original.release(), original.contractIdentity(), contexts,
        original.writtenExpressions());
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.address().equals(address)
        ? new FeatureContextOutcome(address, outcome.occurrence(), outcome.observation(), Optional.of(changedValue),
            support, outcome.emissions())
        : outcome).toList();
    return CoverageAdmission.fromEvidence(input.languageFeatureCoverage(), written,
        new AttributedCoverageExpectations(written, evidence.attributedExpectations().projections()), outcomes,
        evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private static JavaAstAggregate extendFirstPackageAnnotation(JavaAstAggregate aggregate, String sourceKey) {
    var captured = SourceValueCapture.capture(aggregate);
    var root = aggregate.roots().stream().filter(node -> node.sourceFileKey().equals(sourceKey)).findFirst().orElseThrow();
    var declaration = root.requiredChild(SyntaxRole.PACKAGE_DECLARATION);
    var annotation = declaration.children(SyntaxRole.ANNOTATION).getFirst();
    var span = annotation.origin().orElseThrow();
    var changedSpan = new SourceSpan(sourceKey, span.startOffset(), span.endOffsetExclusive() + 1);
    var changedAnnotation = copy(annotation, annotation.children(), changedSpan);
    var changedDeclaration = copy(declaration, replaceChild(declaration, changedAnnotation), declaration.origin().orElseThrow());
    var changedRoot = copy(root, replaceChild(root, changedDeclaration), root.origin().orElseThrow());
    var sources = captured.sources().stream().map(source -> source.sourceFileKey().equals(sourceKey)
        ? new SyntaxSourceContribution(sourceKey, changedRoot, changedRoot.descendantsAndSelf(), source.structuralEdges(),
            source.semanticEdges())
        : source).toList();
    var associations = captured.associations().stream().map(association -> association.nodeKey().equals(annotation.nodeId())
        ? new WrittenSourceAssociation(association.nodeKey(), sourceKey, association.ownerPath(), association.sourceKind(),
            Optional.of(changedSpan))
        : association).toList();
    return AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, captured.bodies(), captured.memberOrders(),
        associations, captured.declarationTypes(), captured.scopes()));
  }

  private static List<SyntaxChild> replaceChild(SyntaxNode parent, SyntaxNode replacement) {
    return parent.children().stream().map(child -> child.node().nodeId().equals(replacement.nodeId())
        ? new SyntaxChild(child.role(), child.position(), replacement) : child).toList();
  }

  private static SyntaxNode copy(SyntaxNode node, List<SyntaxChild> children, SourceSpan origin) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
        children, node.semanticReference(), origin);
  }

  private record NegativeClaim(String owner, String accessor, State actual, State supplied) {
  }
}
