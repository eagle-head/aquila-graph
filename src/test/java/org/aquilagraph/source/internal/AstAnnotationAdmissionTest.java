package org.aquilagraph.source.internal;

import static org.aquilagraph.source.internal.RetainedSourceFixture.payloadWithAggregate;
import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AstAnnotationAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenMissingDuplicateAndExtraApplications_whenCopiedOrDecoded_thenEachApplicationInventoryViolationIsRejected() throws IOException {
    // Arrange
    var result = completeFixture();
    var annotation = field(result, "first").requiredChild(SyntaxRole.ANNOTATION);
    var application = application(result, annotation);
    var neighbor = field(result, "second");
    var extra = applicationEdge(annotation.nodeId(), neighbor.nodeId());
    var variants = List.of(
        new InvalidApplications("missing", replaceEdges(result, annotation.sourceFileKey(), edges ->
            edges.stream().filter(edge -> !edge.equals(application)).toList()), "exactly one typed application"),
        new InvalidApplications("duplicate", replaceEdges(result, annotation.sourceFileKey(), edges -> append(edges, application)),
            "Duplicate AST semantic edge tuple"),
        new InvalidApplications("extra", replaceEdges(result, annotation.sourceFileKey(), edges -> append(edges, extra)),
            "exactly one typed application"));

    // Act
    var failures = variants.stream().map(variant -> rejected(result, variant.values())).toList();

    // Assert
    for (var index = 0; index < variants.size(); index++) {
      assertRejection(failures.get(index), variants.get(index).diagnostic(), variants.get(index).name());
    }
    assertEquals(result.aggregate(), AstValueAdmission.admit(SourceValueCapture.capture(result.aggregate())));
    assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
  }

  @Test
  void givenNeighboringAndForeignApplicationOwners_whenCopiedOrDecoded_thenExactOwnershipIsRejected() throws IOException {
    // Arrange
    var result = completeFixture();
    var annotation = field(result, "first").requiredChild(SyntaxRole.ANNOTATION);
    var original = application(result, annotation);
    var neighbor = field(result, "second");
    var foreign = result.aggregate().roots().stream().filter(root -> !root.sourceFileKey().equals(annotation.sourceFileKey()))
        .findFirst().orElseThrow();
    var variants = List.of(neighbor, foreign).stream().map(owner -> replaceEdges(result, annotation.sourceFileKey(), edges ->
        edges.stream().map(edge -> edge.equals(original) ? applicationEdge(annotation.nodeId(), owner.nodeId()) : edge).toList())).toList();

    // Act
    var failures = variants.stream().map(values -> rejected(result, values)).toList();

    // Assert
    assertEquals(2, failures.size());
    failures.forEach(failure -> assertRejection(failure, "does not match its exact source location", annotation.nodeId()));
    assertEquals(result.aggregate(), AstValueAdmission.admit(SourceValueCapture.capture(result.aggregate())));
  }

  @Test
  void givenNonannotationAndValueSources_whenApplicationEdgesAreSupplied_thenEverySubmittedSourceIsRejected() throws IOException {
    // Arrange
    var result = completeFixture();
    var declaration = field(result, "first");
    var nested = result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation value
        && value.location() == SyntaxValue.AnnotationLocation.VALUE).findFirst().orElseThrow();
    var variants = List.of(declaration, nested).stream().map(node -> replaceEdges(result, node.sourceFileKey(),
        edges -> append(edges, applicationEdge(node.nodeId(), declaration.nodeId())))).toList();

    // Act
    var failures = variants.stream().map(values -> rejected(result, values)).toList();

    // Assert
    assertEquals(2, failures.size());
    failures.forEach(failure -> assertRejection(failure, "annotation target relation has a nonannotation source", "ineligible source"));
    assertEquals(SyntaxValue.AnnotationLocation.VALUE, nested.value(SyntaxValue.Annotation.class).location());
    assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
  }

  @Test
  void givenUnownedApplicationSource_whenCopiedOrDecoded_thenSubmittedEdgeCannotEscapeInventory() throws IOException {
    // Arrange
    var result = completeFixture();
    var declaration = field(result, "first");
    var malformed = replaceEdges(result, declaration.sourceFileKey(),
        edges -> append(edges, applicationEdge("unowned-annotation", declaration.nodeId())));

    // Act
    var failure = rejected(result, malformed);

    // Assert
    assertRejection(failure, "semantic source unowned-annotation", "unowned source");
    assertEquals(result.aggregate(), AstValueAdmission.admit(SourceValueCapture.capture(result.aggregate())));
  }

  @Test
  void givenEligibleAnnotationAtAnInitializerRole_whenCopiedOrDecoded_thenTheWrongAttachmentRoleIsRejected() throws IOException {
    // Arrange
    var result = completeFixture();
    var declaration = field(result, "first");
    var annotation = declaration.requiredChild(SyntaxRole.ANNOTATION);
    var misplaced = SyntaxNode.source(annotation.sourceFileKey(), declaration.structuralPath().child(SyntaxRole.INITIALIZER, 0),
        annotation.sourceKind(), annotation.syntaxKind(), annotation.type(), annotation.value(), annotation.children(),
        annotation.semanticReference(), annotation.origin().orElseThrow());
    var malformed = annotationAsInitializer(SourceValueCapture.capture(result.aggregate()), declaration, annotation, misplaced);

    // Act
    var failure = rejected(result, malformed);
    var copiedControl = AstValueAdmission.admit(SourceValueCapture.capture(result.aggregate()));
    var decodedControl = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(ResolutionStatus.RESOLVED, misplaced.semanticReference().orElseThrow().status());
    assertEquals(SyntaxValue.AnnotationLocation.DECLARATION, misplaced.value(SyntaxValue.Annotation.class).location());
    assertEquals(SyntaxRole.INITIALIZER, misplaced.structuralPath().steps().getLast().role());
    assertEquals("AST annotation application does not match its exact source location: " + misplaced.nodeId(),
        failure.direct().getMessage());
    assertEquals(failure.direct().getMessage(), failure.decoded().getCause().getMessage());
    assertEquals(result.aggregate(), copiedControl);
    assertEquals(result.aggregate(), decodedControl.aggregate());
  }

  @Test
  void givenUnresolvedAnnotationLocation_whenAnApplicationIsSupplied_thenCopiedAndRetainedValuesRejectIt() throws IOException {
    // Arrange
    var result = produce("""
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Use {}
        class Example { @Use void call() {} }
        """);
    var annotation = result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation value
        && value.location() == SyntaxValue.AnnotationLocation.UNRESOLVED).findFirst().orElseThrow();
    var parent = result.aggregate().nodes().stream().filter(node -> node.children(SyntaxRole.ANNOTATION).contains(annotation))
        .findFirst().orElseThrow();
    var malformed = replaceEdges(result, annotation.sourceFileKey(),
        edges -> append(edges, applicationEdge(annotation.nodeId(), parent.nodeId())));

    // Act
    var failure = rejected(result, malformed);
    var healthy = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertRejection(failure, "annotation target relation has a nonannotation source", annotation.nodeId());
    assertEquals(AstCompletion.INCOMPLETE, healthy.completion());
    assertTrue(healthy.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertEquals(result.aggregate(), healthy.aggregate());
    assertTrue(healthy.aggregate().semanticEdges().stream().noneMatch(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET));
  }

  @Test
  void givenPermutedEquivalentApplicationEdges_whenCopiedAndDecoded_thenCanonicalOrderAndQualifiedIdentityRemainExact() throws IOException {
    // Arrange
    var result = completeFixture();
    var original = SourceValueCapture.capture(result.aggregate());
    var sources = original.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(), source.root(),
        source.nodes(), source.structuralEdges(), source.semanticEdges().reversed())).toList();
    var reversed = withSources(original, sources);
    var payload = payloadWithAggregate(result, reversed);

    // Act
    var copied = AstValueAdmission.admit(reversed);
    var decoded = AstCodec.decode(payload, result.identity());

    // Assert
    assertEquals(result.aggregate().semanticEdges(), copied.semanticEdges());
    assertEquals(result.aggregate(), copied);
    assertEquals(result.aggregate(), decoded.aggregate());
    assertEquals(result.identity(), decoded.identity());
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
    var annotation = field(result, "first").requiredChild(SyntaxRole.ANNOTATION);
    var target = assertInstanceOf(AnnotationApplication.class, application(decoded, annotation).target());
    assertEquals(Optional.of(field(result, "first").nodeId()), target.declarationNodeKey());
  }

  private AstResult completeFixture() throws IOException {
    var result = produce("""
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.FIELD) @interface Mark {}
        @Target(ElementType.FIELD) @interface Outer { Inner value(); }
        @interface Inner {}
        class Example { @Mark String first; @Mark String second; @Outer(@Inner) int number; }
        """);
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    return result;
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("Example.java")), source),
        SourceFile.fromContent(capturedPath(Path.of("Other.java")), "class Other {}")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
  }

  private static SyntaxNode field(AstResult result, String name) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION
        && node.value(SyntaxValue.VariableDeclaration.class).name().equals(name)).findFirst().orElseThrow();
  }

  private static SyntaxSemanticEdge application(AstResult result, SyntaxNode annotation) {
    return result.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).findFirst().orElseThrow();
  }

  private static SyntaxSemanticEdge applicationEdge(String source, String owner) {
    return new SyntaxSemanticEdge(source, SyntaxSemanticRelation.ANNOTATED_TARGET,
        new AnnotationApplication(owner, Optional.of(owner), Optional.empty(), List.of(), List.of()));
  }

  private static CopiedAstValues replaceEdges(AstResult result, String sourceKey, UnaryOperator<List<SyntaxSemanticEdge>> change) {
    var original = SourceValueCapture.capture(result.aggregate());
    var sources = original.sources().stream().map(source -> source.sourceFileKey().equals(sourceKey)
        ? new SyntaxSourceContribution(source.sourceFileKey(), source.root(), source.nodes(), source.structuralEdges(),
            change.apply(source.semanticEdges())) : source).toList();
    return withSources(original, sources);
  }

  private static CopiedAstValues withSources(CopiedAstValues original, List<SyntaxSourceContribution> sources) {
    return new CopiedAstValues(original.snapshot(), sources, original.bodies(), original.memberOrders(), original.associations(),
        original.declarationTypes(), original.scopes());
  }

  private static CopiedAstValues annotationAsInitializer(CopiedAstValues original, SyntaxNode declaration, SyntaxNode annotation,
      SyntaxNode misplaced) {
    var value = declaration.value(SyntaxValue.VariableDeclaration.class);
    var header = new SyntaxValue.VariableDeclaration(value.name(), value.ownerKey(), value.declarationKey(), value.kind(),
        value.writtenModifiers(), value.effectiveModifiers(), value.declaredType(), 0, value.type(), true,
        value.argumentList(), value.arguments(), value.constantBody(), value.sharedDeclaratorKeys());
    var children = declaration.children().stream().map(child -> child.node() == annotation
        ? new SyntaxChild(SyntaxRole.INITIALIZER, 0, misplaced) : child).toList();
    var replacement = SyntaxNode.source(declaration.sourceFileKey(), declaration.structuralPath(), declaration.sourceKind(),
        declaration.syntaxKind(), declaration.type(), header, children, declaration.semanticReference(), declaration.origin().orElseThrow());
    var sources = original.sources().stream().map(source -> {
      var root = replaceNode(source.root(), declaration, replacement);
      var nodes = root.descendantsAndSelf();
      var edges = source.semanticEdges().stream().map(edge -> edge.sourceKey().equals(annotation.nodeId())
          ? new SyntaxSemanticEdge(misplaced.nodeId(), edge.relation(), edge.target()) : edge).toList();
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), edges);
    }).toList();
    var associations = original.associations().stream().map(association -> association.nodeKey().equals(annotation.nodeId())
        ? new WrittenSourceAssociation(misplaced.nodeId(), association.sourceFileKey(), association.ownerPath(), association.sourceKind(),
            association.origin(), association.normalizedExpression()) : association).toList();
    var scopes = original.scopes().stream().map(scope -> scope.root() == declaration
        ? new CopiedAstValues.Scope(scope.scope(), replacement, scope.uses(), scope.invocations(), scope.staticAccesses(),
            scope.concatenations(), scope.attributedOccurrences().stream().map(occurrence -> occurrence.nodeKey().equals(annotation.nodeId())
                ? new AttributedBodyOccurrence(occurrence.scopeKey(), misplaced.nodeId(), occurrence.sourceFileKey(), occurrence.sourceKind(),
                    occurrence.compilerPath()) : occurrence).toList()) : scope).toList();
    return new CopiedAstValues(original.snapshot(), sources, original.bodies(), original.memberOrders(), associations,
        original.declarationTypes(), scopes);
  }

  private static SyntaxNode replaceNode(SyntaxNode node, SyntaxNode original, SyntaxNode replacement) {
    if (node == original) {
      return replacement;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        replaceNode(child.node(), original, replacement))).toList();
    return children.equals(node.children()) ? node
        : SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
            children, node.semanticReference(), node.origin().orElseThrow());
  }

  private static List<SyntaxSemanticEdge> append(List<SyntaxSemanticEdge> edges, SyntaxSemanticEdge edge) {
    var result = new ArrayList<>(edges);
    result.add(edge);
    return result;
  }

  private static Rejection rejected(AstResult result, CopiedAstValues values) {
    var payload = payloadWithAggregate(result, values);
    var direct = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(values));
    var decoded = assertThrows(AstContractException.class, () -> AstCodec.decode(payload, result.identity()));
    return new Rejection(direct, decoded);
  }

  private static void assertRejection(Rejection failure, String diagnostic, String context) {
    assertTrue(failure.direct().getMessage().contains(diagnostic), context + ": " + failure.direct());
    assertEquals(failure.direct().getMessage(), failure.decoded().getCause().getMessage(), context);
  }

  private record InvalidApplications(String name, CopiedAstValues values, String diagnostic) {}

  private record Rejection(AstContractException direct, AstContractException decoded) {}
}
