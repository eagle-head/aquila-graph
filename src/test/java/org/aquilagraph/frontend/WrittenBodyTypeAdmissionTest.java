package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CopiedAstValues.Body;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SourceContext.Partition;
import org.aquilagraph.source.api.SourceOwnerPath.Role;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class WrittenBodyTypeAdmissionTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenKnownCastResult_whenItsWrittenTargetHasAContradictoryKnownType_thenIntrinsicSourceAdmissionRejects() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .map(profile -> DynamicTest.dynamicTest(profile.version(), () -> assertContradictoryPrimitiveTarget(profile, false)));
  }

  @TestFactory
  Stream<DynamicTest> givenKnownLocalType_whenItsWrittenTargetHasAContradictoryKnownType_thenIntrinsicSourceAdmissionRejects() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .map(profile -> DynamicTest.dynamicTest(profile.version(), () -> assertContradictoryPrimitiveTarget(profile, true)));
  }

  private void assertContradictoryPrimitiveTarget(AstProfile profile, boolean local) throws IOException {
    // Arrange
    var source = local ? "class Example { int subject(long input) { int narrowed = (int) input; return narrowed; } }"
        : "class Example { int subject(long input) { return (int) input; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
    var kind = local ? SyntaxKind.LOCAL_DECLARATION : SyntaxKind.TYPE_CAST;
    var role = local ? SyntaxRole.DECLARED_TYPE : SyntaxRole.TYPE_OPERAND;
    var parent = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind).findFirst().orElseThrow();
    var target = parent.requiredChild(role);
    var contradictory = SyntaxNode.source(target.sourceFileKey(), target.structuralPath(), target.sourceKind(), target.syntaxKind(),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"),
            Optional.of(new JavaTypeStructure.Primitive(JavaTypeCategory.LONG))),
        new SyntaxValue.PrimitiveType(JavaTypeCategory.LONG), target.children(), target.semanticReference(), target.origin().orElseThrow());
    var children = parent.children().stream()
        .map(child -> child.role() == role ? new SyntaxChild(child.role(), child.position(), contradictory) : child).toList();

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source(parent.sourceFileKey(), parent.structuralPath(), parent.sourceKind(), parent.syntaxKind(), parent.type(),
            parent.value(), children, parent.semanticReference(), parent.origin().orElseThrow()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertTrue(failure.getMessage().contains("written body type"));
  }

  @TestFactory
  Stream<DynamicTest> givenOriginalCastExpectations_whenOnlyWrittenTargetIsOmitted_thenClosedValueIsIncompleteAndIneligible() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .map(profile -> DynamicTest.dynamicTest(profile.version(), () -> assertMissingTarget(profile, false)));
  }

  @TestFactory
  Stream<DynamicTest> givenWrittenLocalTypeBesideCast_whenOnlyLocalTypeIsOmitted_thenClosedValueIsIncompleteAndIneligible() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .map(profile -> DynamicTest.dynamicTest(profile.version(), () -> assertMissingTarget(profile, true)));
  }

  @TestFactory
  Stream<DynamicTest> givenWrittenConstructedType_whenOnlyTargetIsOmitted_thenClosedValueIsIncompleteAndIneligible() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .map(profile -> DynamicTest.dynamicTest(profile.version(), () -> assertMissingTarget(profile,
            "class Example { Object subject() { return new Object(); } }", SyntaxKind.OBJECT_CREATION, SyntaxRole.TYPE_OPERAND)));
  }

  @TestFactory
  Stream<DynamicTest> givenExplicitInvocationTypeArgument_whenOnlyWrittenArgumentIsOmitted_thenClosedValueIsIncompleteAndIneligible() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .map(profile -> DynamicTest.dynamicTest(profile.version(), () -> assertMissingTarget(profile,
            "class Example { static <T> T identity(T input) { return input; } String subject(String input) { return Example.<String>identity(input); } }",
            SyntaxKind.INVOCATION, SyntaxRole.TYPE_ARGUMENT)));
  }

  @TestFactory
  Stream<DynamicTest> givenRealPrimitiveReferenceAndArrayCasts_whenClosed_thenExactRecursiveTargetsRemainComplete() {
    var declarations = List.of("int subject(long input) { return (int) input; }",
        "String subject(Object input) { return (String) input; }",
        "String[] subject(Object input) { return (String[]) input; }",
        "List<String> subject(Object input) { return (List<String>) input; }",
        "List<? extends Number> subject(Object input) { return (List<? extends Number>) input; }",
        "<T> T subject(Object input) { return (T) input; }");
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .flatMap(profile -> declarations.stream().map(declaration -> DynamicTest.dynamicTest(profile.version() + ": " + declaration,
            () -> assertRetainedTargets(profile, declaration, AstCompletion.COMPLETE, List.of()))));
  }

  @TestFactory
  Stream<DynamicTest> givenExcludedIntersectionCast_whenClosed_thenExactSyntaxRemainsRetainedWithItsCapabilityRefusal() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .map(profile -> DynamicTest.dynamicTest(profile.version(), () -> assertRetainedTargets(profile,
            "Object subject(Object input) { return (java.io.Serializable & CharSequence) input; }", AstCompletion.INCOMPLETE,
            List.of("frontend.unsupported-tree-kind.intersection-type"))));
  }

  private void assertRetainedTargets(AstProfile profile, String declaration, AstCompletion expected, List<String> diagnosticCodes)
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "import java.util.List; class Example { " + declaration + " }")));
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();

    // Act
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var target = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow()
        .requiredChild(SyntaxRole.TYPE_OPERAND);
    var evidence = decoded.coverage().validationEvidence();

    // Assert
    assertEquals(expected, result.completion(), decoded.diagnostics().toString());
    assertEquals(expected, decoded.completion());
    assertEquals(diagnosticCodes, decoded.diagnostics().stream().map(value -> value.code()).toList());
    var targetOwner = decoded.aggregate().writtenSourceAssociation(target.nodeId()).orElseThrow();
    assertEquals(Role.CAST_TYPE, targetOwner.ownerPath().steps().getLast().role());
    assertEquals(0, targetOwner.ownerPath().steps().getLast().ordinal());
    for (var type : target.descendantsAndSelf()) {
      var association = decoded.aggregate().writtenSourceAssociation(type.nodeId()).orElseThrow();
      var context = evidence.writtenInventory().contexts().stream().filter(value -> value.address().partition() == Partition.WRITTEN_TYPE_USE
          && value.address().ownerPath().equals(association.ownerPath())).findFirst().orElseThrow();
      assertEquals(type.origin().stream().toList(), context.origins());
      var outcome = evidence.outcomes().stream().filter(value -> value.address().equals(context.address())).findFirst().orElseThrow();
      assertEquals(Mode.FACTS, outcome.support().mode());
      assertEquals(List.of(type.nodeId()), outcome.support().facts().stream().map(value -> value.identity()).toList());
    }
  }

  private void assertMissingTarget(AstProfile profile, boolean localType) throws IOException {
    // Arrange
    var source = localType ? "class Example { int subject(long input) { int narrowed = (int) input; return narrowed; } }"
        : "class Example { int subject(long input) { return (int) input; } }";
    var kind = localType ? SyntaxKind.LOCAL_DECLARATION : SyntaxKind.TYPE_CAST;
    var role = localType ? SyntaxRole.DECLARED_TYPE : SyntaxRole.TYPE_OPERAND;
    assertMissingTarget(profile, source, kind, role);
  }

  private void assertMissingTarget(AstProfile profile, String source, SyntaxKind kind, SyntaxRole role) throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var original = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var baseline = AstResultAdmission.admit(original, profile, "javac-public/1");
    var changed = AstValueAdmission.admit(omitTypeTarget(original.aggregate().orElseThrow(), kind, role));
    var supplied = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(changed),
        JavaCoverageTestSupport.diagnostics(original, "javac-public/1"));

    // Act
    var admitted = AstResultAdmission.admit(supplied, profile, "javac-public/1");
    var decoded = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, baseline.completion());
    assertEquals(List.of(), original.diagnostics());
    var originalEvidence = original.languageFeatureCoverageResult().validationEvidence();
    var changedEvidence = supplied.languageFeatureCoverageResult().validationEvidence();
    assertEquals(originalEvidence.writtenInventory(), changedEvidence.writtenInventory());
    assertEquals(originalEvidence.attributedExpectations(), changedEvidence.attributedExpectations());
    var changedNodes = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind).toList();
    assertEquals(1, changedNodes.size());
    assertTrue(changedNodes.getFirst().children(role).isEmpty());
    assertEquals(AstCompletion.INCOMPLETE, admitted.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    var retention = new AstRetentionService(ignored -> {
      throw new AssertionError("Incomplete written body types must not acquire a repository");
    });
    assertEquals(PublicationStatus.NOT_ELIGIBLE, retention.retainAst(AstOutcome.available(decoded)).status());
  }

  private static CopiedAstValues omitTypeTarget(JavaAstAggregate aggregate, SyntaxKind kind, SyntaxRole role) {
    var copied = CopiedAstValues.capture(aggregate);
    var omitted = aggregate.nodes().stream().filter(node -> node.syntaxKind() == kind)
        .flatMap(node -> node.requiredChild(role).descendantsAndSelf().stream()).map(SyntaxNode::nodeId)
        .collect(Collectors.toSet());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = copied.sources().stream().map(source -> {
      var root = omit(source.root(), omitted, replacements);
      var nodes = root.descendantsAndSelf();
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(),
          source.semanticEdges().stream().filter(edge -> !omitted.contains(edge.sourceKey())).toList());
    }).toList();
    var bodies = copied.bodies().stream().map(body -> new Body(body.methodKey(), omit(body.root(), omitted, replacements), body.origin(),
        body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(),
        body.attributedOccurrences().stream().filter(occurrence -> !omitted.contains(occurrence.nodeKey())).toList())).toList();
    return new CopiedAstValues(copied.snapshot(), sources, bodies, copied.memberOrders(),
        copied.associations().stream().filter(association -> !omitted.contains(association.nodeKey())).toList(), copied.declarationTypes());
  }

  private static SyntaxNode omit(SyntaxNode node, Set<String> omitted, Map<SyntaxNode, SyntaxNode> replacements) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream().filter(child -> !omitted.contains(child.node().nodeId()))
        .map(child -> new SyntaxChild(child.role(), child.position(), omit(child.node(), omitted, replacements))).toList();
    var replacement = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
            children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(),
            node.value(), children, node.semanticReference());
    replacements.put(node, replacement);
    return replacement;
  }
}
