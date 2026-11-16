package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication.FieldDeclaratorApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation.Written;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedAnnotationAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenAnnotatedSharedFieldsAndLocals_whenProducedAndReadmitted_thenEachWrittenDeclaratorHasItsExactDestination()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Both {}
        class Sample { @Both int first, second; void run() { @Both int localFirst, localSecond; } }
        """;

    // Act
    var ast = produce(source);
    var readmitted = AstValueAdmission.admit(SourceValueCapture.capture(ast.aggregate()));

    // Assert
    assertEquals(ast.aggregate(), readmitted);
    for (var kind : List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.LOCAL_DECLARATION)) {
      var declarations = readmitted.nodes().stream().filter(node -> node.syntaxKind() == kind).toList();
      assertEquals(2, declarations.size());
      var first = declarations.getFirst();
      var second = declarations.getLast();
      var annotation = first.requiredChild(SyntaxRole.ANNOTATION);
      var expected = new AnnotationApplication(first.nodeId(), Optional.of(first.nodeId()),
          Optional.of(new Written(first.requiredChild(SyntaxRole.DECLARED_TYPE).nodeId())), List.of(),
          List.of(new FieldDeclaratorApplication(second.nodeId(), true,
              Optional.of(new Written(second.requiredChild(SyntaxRole.DECLARED_TYPE).nodeId())))));
      assertEquals(expected, application(readmitted, annotation));
      assertTrue(second.children(SyntaxRole.ANNOTATION).isEmpty());
    }
  }

  @Test
  void givenPlainSharedFields_whenCopiedHeaderIdentityModifiersOrOrderDisagree_thenDirectAdmissionRejectsEveryVariant()
      throws IOException {
    // Arrange
    var ast = produce("class Sample { int first, second; }");
    var copied = SourceValueCapture.capture(ast.aggregate());
    var fields = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION).toList();
    var second = fields.getLast();
    var facts = second.value(SyntaxValue.VariableDeclaration.class);
    var reversed = facts.sharedDeclaratorKeys().reversed();
    var changedIdentity = field(facts, facts.writtenModifiers(), reversed);
    var changedModifiers = field(facts, Set.of(JavaDeclarationModifier.PUBLIC), facts.sharedDeclaratorKeys());
    var firstRange = fields.getFirst().origin().orElseThrow();
    var changes = List.<UnaryOperator<SyntaxNode>>of(
        node -> copy(node, changedIdentity, node.origin().orElseThrow()),
        node -> copy(node, changedModifiers, node.origin().orElseThrow()),
        node -> copy(node, node.value(), new SourceSpan(node.sourceFileKey(), firstRange.startOffset(), firstRange.endOffsetExclusive())));
    assertTrue(ast.aggregate().nodes().stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.ANNOTATION));
    assertEquals(ast.aggregate(), AstValueAdmission.admit(copied));

    for (var change : changes) {
      var altered = rewrite(copied, second.nodeId(), change);

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(altered));

      // Assert
      assertTrue(failure.getMessage().contains("Shared field declarations disagree with their source header"), failure::getMessage);
    }
  }

  @Test
  void givenPlainSharedLocals_whenCopiedModifiersOrOrderDisagree_thenDirectAdmissionRejectsWithoutAnnotations()
      throws IOException {
    // Arrange
    var ast = produce("class Sample { void run() { int first, second; } }");
    var copied = SourceValueCapture.capture(ast.aggregate());
    var locals = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
    var second = locals.getLast();
    var facts = second.value(SyntaxValue.LocalDeclaration.class);
    var changedModifiers = new SyntaxValue.LocalDeclaration(facts.name(), facts.writtenType(), List.of("final"), facts.bindingEvidence());
    var firstRange = locals.getFirst().origin().orElseThrow();
    var changes = List.<UnaryOperator<SyntaxNode>>of(
        node -> copy(node, changedModifiers, node.origin().orElseThrow()),
        node -> copy(node, node.value(), new SourceSpan(node.sourceFileKey(), firstRange.startOffset(), firstRange.endOffsetExclusive())));
    assertTrue(ast.aggregate().nodes().stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.ANNOTATION));
    assertEquals(ast.aggregate(), AstValueAdmission.admit(copied));

    for (var change : changes) {
      var altered = rewrite(copied, second.nodeId(), change);

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(altered));

      // Assert
      assertTrue(failure.getMessage().contains("Shared local declarations disagree with their written header"), failure::getMessage);
    }
  }

  @Test
  void givenEqualRangeArrayDimensions_whenOneApplicationTargetsItsNeighbor_thenDirectAdmissionRejectsTheDistinctPosition()
      throws IOException {
    // Arrange
    var ast = produce("""
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use { int value(); }
        class Sample { String @Use(1) [] @Use(2) [] first, second; }
        """);
    var aggregate = ast.aggregate();
    var physical = aggregate.nodes().stream().filter(node -> !node.children(SyntaxRole.ANNOTATION).isEmpty()
        && node.syntaxKind() == SyntaxKind.TYPE_USE).toList();
    assertEquals(2, physical.size());
    assertEquals(physical.getFirst().origin(), physical.getLast().origin());
    assertNotEquals(physical.getFirst().structuralPath(), physical.getLast().structuralPath());
    var annotation = physical.getFirst().requiredChild(SyntaxRole.ANNOTATION);
    var original = application(aggregate, annotation);
    var neighbor = physical.getLast();
    var wrong = new AnnotationApplication(original.ownerNodeKey(), original.declarationNodeKey(),
        Optional.of(new Written(neighbor.nodeId())), original.propagatedCounterparts(), original.fieldDeclarators());
    var altered = replaceApplication(SourceValueCapture.capture(aggregate), annotation, wrong);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(altered));

    // Assert
    assertTrue(failure.getMessage().contains("does not match its exact source location"), failure::getMessage);
    assertTrue(failure.getMessage().contains(annotation.nodeId()), failure::getMessage);
  }

  @Test
  void givenNestedRecordComponentType_whenReadmittedOrOneCounterpartIsOmitted_thenExactRecursivePropagationIsRequired()
      throws IOException {
    // Arrange
    var ast = produce("""
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use {}
        record Sample(java.util.List<? extends @Use String[]> value) {}
        """);
    var copied = SourceValueCapture.capture(ast.aggregate());
    var component = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION)
        .findFirst().orElseThrow();
    var owner = component.requiredChild(SyntaxRole.DECLARED_TYPE).descendantsAndSelf().stream()
        .filter(node -> !node.children(SyntaxRole.ANNOTATION).isEmpty()).findFirst().orElseThrow();
    var annotation = owner.requiredChild(SyntaxRole.ANNOTATION);
    var original = application(ast.aggregate(), annotation);
    var incomplete = new AnnotationApplication(original.ownerNodeKey(), original.declarationNodeKey(), original.typeLocation(),
        original.propagatedCounterparts().subList(0, 2), original.fieldDeclarators());
    var altered = replaceApplication(copied, annotation, incomplete);

    // Act
    var admitted = AstValueAdmission.admit(copied);
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(altered));

    // Assert
    var application = application(admitted, annotation);
    assertEquals(owner.nodeId(), application.ownerNodeKey());
    assertEquals(Optional.of(new Written(owner.nodeId())), application.typeLocation());
    assertTrue(application.declarationNodeKey().isEmpty());
    assertTrue(application.fieldDeclarators().isEmpty());
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
        application.propagatedCounterparts().stream().map(value -> value.reference().referenceKind()).toList());
    var counterparts = component.value(SyntaxValue.RecordComponentDeclaration.class).counterparts();
    assertEquals(List.of(counterparts.backingFieldKey(), counterparts.accessorKey(), counterparts.canonicalParameterKey()),
        application.propagatedCounterparts().stream().map(value -> value.reference().symbolKey().orElseThrow()).toList());
    application.propagatedCounterparts().forEach(value -> {
      assertFalse(value.declarationApplicable());
      assertEquals(Optional.of(new Written(owner.nodeId())), value.typeLocation());
    });
    assertTrue(failure.getMessage().contains("does not match its exact source location"), failure::getMessage);
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Sample.java")), source)));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED);
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }

  private static AnnotationApplication application(JavaAstAggregate aggregate, SyntaxNode annotation) {
    var edges = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).toList();
    assertEquals(1, edges.size());
    return assertInstanceOf(AnnotationApplication.class, edges.getFirst().target());
  }

  private static SyntaxValue.VariableDeclaration field(SyntaxValue.VariableDeclaration original,
      Set<JavaDeclarationModifier> modifiers, List<String> keys) {
    return new SyntaxValue.VariableDeclaration(original.name(), original.ownerKey(), original.declarationKey(), original.kind(),
        modifiers, original.effectiveModifiers(), original.declaredType(), original.annotations(), original.type(), original.initializer(),
        original.argumentList(), original.arguments(), original.constantBody(), keys);
  }

  private static SyntaxNode copy(SyntaxNode node, SyntaxValue value, SourceSpan origin) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value,
        node.children(), node.semanticReference(), origin);
  }

  private static SyntaxNode rewrite(SyntaxNode node, String key, UnaryOperator<SyntaxNode> replacement) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        rewrite(child.node(), key, replacement))).toList();
    var copied = SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(),
        node.value(), children, node.semanticReference(), node.origin().orElseThrow());
    return node.nodeId().equals(key) ? replacement.apply(copied) : copied;
  }

  private static CopiedAstValues rewrite(CopiedAstValues copied, String key, UnaryOperator<SyntaxNode> replacement) {
    var sources = copied.sources().stream().map(source -> {
      var root = rewrite(source.root(), key, replacement);
      var nodes = root.descendantsAndSelf();
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), source.semanticEdges());
    }).toList();
    var nodes = sources.stream().flatMap(source -> source.nodes().stream()).collect(Collectors.toMap(SyntaxNode::nodeId, node -> node));
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(),
        body.origin() == MethodBodyOrigin.SOURCE_AST ? nodes.get(body.root().nodeId()) : body.root(),
        body.origin(), body.uses(), body.invocations(), body.staticAccesses(),
        body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var scopes = copied.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(),
        nodes.getOrDefault(scope.root().nodeId(), scope.root()), scope.uses(), scope.invocations(), scope.staticAccesses(),
        scope.concatenations(), scope.attributedOccurrences())).toList();
    var associations = copied.associations().stream().map(association -> new WrittenSourceAssociation(association.nodeKey(),
        association.sourceFileKey(), association.ownerPath(), association.sourceKind(), nodes.get(association.nodeKey()).origin(),
        association.normalizedExpression())).toList();
    return new CopiedAstValues(copied.snapshot(), sources, bodies, copied.memberOrders(), associations,
        copied.declarationTypes(), scopes);
  }

  private static CopiedAstValues replaceApplication(CopiedAstValues copied, SyntaxNode annotation, AnnotationApplication application) {
    var sources = copied.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(), source.root(),
        source.nodes(), source.structuralEdges(), source.semanticEdges().stream().map(edge -> edge.sourceKey().equals(annotation.nodeId())
            && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
                ? new SyntaxSemanticEdge(annotation.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET, application) : edge).toList())).toList();
    return new CopiedAstValues(copied.snapshot(), sources, copied.bodies(), copied.memberOrders(), copied.associations(),
        copied.declarationTypes(), copied.scopes());
  }
}
