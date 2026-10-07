package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalBindingAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenPureLegacyBindings_whenDeclarationAndAllAccessesSelectAForeignExecutable_thenAdmissionRejectsThem() throws IOException {
    assertForeignLegacyOwnerRejected(true);
  }

  @Test
  void givenMixedLegacyBindings_whenDeclarationAndAllAccessesSelectAForeignExecutable_thenAdmissionRejectsThem() throws IOException {
    assertForeignLegacyOwnerRejected(false);
  }

  private void assertForeignLegacyOwnerRejected(boolean pureLegacy) throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { int value=1; int total=value; return value+total; } }");
    var originalKey = key(named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst());
    var supplied = copy(original, node -> {
      var copied = node.value() instanceof SyntaxValue.LocalDeclaration local && (pureLegacy || local.name().equals("value"))
          ? legacy(node, local.name()) : node;
      return copied.semanticReference().flatMap(SemanticReference::symbolKey).filter(originalKey::equals).isPresent()
          ? select(copied, "type:Foreign#subject():local_variable:0:1") : copied;
    });

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("declared executable owner"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenOrdinaryLocal_whenContextClaimsCatchAttachment_thenPublicAdmissionRejectsIt() throws IOException {
    assertContextContradictionRejected("class Example { int subject() { int value=1; return value; } }", LocalBindingEvidence.Context.LOCAL);
  }

  @Test
  void givenCatchParameter_whenContextClaimsOrdinaryLocalAttachment_thenPublicAdmissionRejectsIt() throws IOException {
    assertContextContradictionRejected(
        "class Example { int subject() { try { return 1; } catch (RuntimeException value) { return value.hashCode(); } } }",
        LocalBindingEvidence.Context.CATCH);
  }

  private void assertContextContradictionRejected(String source, LocalBindingEvidence.Context expected) throws IOException {
    // Arrange
    var original = produce(source);
    var declaration = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var evidence = declaration.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow();
    var contradictory = expected == LocalBindingEvidence.Context.LOCAL ? LocalBindingEvidence.Context.CATCH : LocalBindingEvidence.Context.LOCAL;
    var supplied = copy(original, node -> {
      if (!node.nodeId().equals(declaration.nodeId())) {
        return node;
      }
      var local = node.value(SyntaxValue.LocalDeclaration.class);
      return replace(node, new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(),
          Optional.of(new LocalBindingEvidence(evidence.declarationKey(), evidence.typeForm(), contradictory,
              evidence.initialized(), evidence.annotations(), evidence.sharedAnnotationKeys()))));
    });

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var positive = AstValueAdmission.admit(CopiedAstValues.capture(original.aggregate()));

    // Assert
    assertTrue(failure.getMessage().contains("context must agree with its source attachment"), failure.toString());
    assertEquals(expected, evidence.context());
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(original.aggregate(), positive);
  }

  @Test
  void givenUnsupportedPatternAndOrdinaryBindings_whenCopiedAdmissionRuns_thenUnsupportedSourceRemainsIncomplete() throws IOException {
    // Arrange
    var original = unsupportedPattern();

    // Act
    var admitted = AstValueAdmission.admit(CopiedAstValues.capture(original.aggregate()));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, original.completion());
    assertEquals(original.aggregate(), admitted);
    assertTrue(original.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
  }

  @Test
  void givenUnsupportedPatternAndOrdinaryBindings_whenCopiedAccessSelectsAForeignOwner_thenUnsupportedStateCannotWaiveOwnership()
      throws IOException {
    // Arrange
    var original = unsupportedPattern();
    var access = named(original, "text", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? select(node, "type:Foreign#subject():binding_variable:0:1") : node);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("owned named binding"));
    assertEquals(AstCompletion.INCOMPLETE, original.completion());
  }

  @Test
  void givenSameNamedSiblingBindings_whenAccessSelectsTheOtherScope_thenPublicAdmissionRejectsIt() throws IOException {
    // Arrange
    var original = siblingBindings();
    var bindings = named(original, "value", SyntaxKind.LOCAL_DECLARATION);
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, key(bindings.getLast())) : node);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("lexical scope"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(original.aggregate(), AstValueAdmission.admit(CopiedAstValues.capture(original.aggregate())));
  }

  @Test
  void givenOneLegacyLocalAndExpandedBindings_whenAccessSelectsAForeignOwner_thenPublicAdmissionStillRejectsIt() throws IOException {
    // Arrange
    var original = siblingBindings();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? select(node, "type:Foreign#subject():local_variable:0:1") : legacy(node, "total"));

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("owned named binding"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenLegalNestedReferences_whenLegacyAndExpandedValuesAreMixed_thenBothBindingIdentitiesRemainAdmitted() throws IOException {
    // Arrange
    var original = siblingBindings();
    var supplied = copy(original, node -> legacy(node, "total"));

    // Act
    var admitted = AstValueAdmission.admit(supplied);

    // Assert
    assertEquals(original.aggregate().nodes().size(), admitted.nodes().size());
    assertEquals(named(original, "value", SyntaxKind.VARIABLE_ACCESS).stream().map(SyntaxNode::semanticReference).toList(),
        admitted.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
            .filter(node -> node.value(SyntaxValue.SourceSymbol.class).name().equals("value")).map(SyntaxNode::semanticReference).toList());
    assertTrue(admitted.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals("total"))
        .allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isEmpty()));
  }

  @Test
  void givenPureLegacyNamedBindings_whenReadmitted_thenRetainedReferencesRemainValid() throws IOException {
    // Arrange
    var original = siblingBindings();
    var supplied = copy(original, node -> node.value() instanceof SyntaxValue.LocalDeclaration local ? legacy(node, local.name()) : node);

    // Act
    var admitted = AstValueAdmission.admit(supplied);

    // Assert
    assertEquals(original.aggregate().semanticEdges(), admitted.semanticEdges());
    assertTrue(admitted.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration)
        .allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isEmpty()));
  }

  @Test
  void givenEarlierOccurrenceAndLaterBinding_whenCopiedAccessSelectsTheLaterDeclaration_thenAdmissionRejectsDeclarationOrder()
      throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { int first=1; int second=first; int later=2; return second; } }");
    var access = named(original, "first", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var declaration = named(original, "later", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? replace(select(node, key(declaration)), new SyntaxValue.SourceSymbol("later")) : node);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("precede its binding declaration"), failure.toString());
  }

  @Test
  void givenSelfInitializerWithinDeclarationScope_whenCompilerReportsDefiniteAssignmentError_thenCopiedScopeAdmissionPreservesIt()
      throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { int value=value; return value; } }");

    // Act
    var admitted = AstValueAdmission.admit(CopiedAstValues.capture(original.aggregate()));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, original.completion());
    assertEquals(original.aggregate(), admitted);
    assertTrue(original.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("compiler.err.var.might.not.have.been.initialized")));
  }

  private AstResult siblingBindings() throws IOException {
    return produce("class Example { int subject() { int total=0; { int value=1; total=value; } { int value=2; total=value; } return total; } }");
  }

  private AstResult unsupportedPattern() throws IOException {
    return produce("class Example { int subject(Object input) { int total=0; return switch(input) { case String text when !text.isEmpty() -> total+1; default -> total; }; } }");
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
  }

  private static List<SyntaxNode> named(AstResult result, String name, SyntaxKind kind) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind)
        .filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local ? local.name().equals(name)
            : node.value(SyntaxValue.SourceSymbol.class).name().equals(name)).toList();
  }

  private static String key(SyntaxNode declaration) {
    return declaration.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
  }

  private static SyntaxNode legacy(SyntaxNode node, String name) {
    if (node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals(name)) {
      return replace(node, new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers()));
    }
    return node;
  }

  private static SyntaxNode replace(SyntaxNode node, SyntaxValue value) {
    return rebuild(node, value, node.children(), node.semanticReference());
  }

  private static SyntaxNode select(SyntaxNode node, String bindingKey) {
    var reference = node.semanticReference().orElseThrow();
    return rebuild(node, node.value(), node.children(), Optional.of(new SemanticReference(reference.referenceKind(), reference.status(),
        Optional.of(bindingKey), reference.typeName(), reference.typeAlternativeNames(), reference.assignableToTypeNames())));
  }

  private static CopiedAstValues copy(AstResult result, UnaryOperator<SyntaxNode> change) {
    var original = CopiedAstValues.capture(result.aggregate());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = original.sources().stream().map(source -> {
      var root = copy(source.root(), change, replacements);
      var nodes = root.descendantsAndSelf();
      var edges = new ArrayList<>(source.semanticEdges().stream()
          .filter(edge -> nodes.stream().noneMatch(node -> node.nodeId().equals(edge.sourceKey())
              && (node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS || node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)))
          .toList());
      nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS || node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
          .forEach(node -> edges.addAll(node.semanticEdges()));
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), edges);
    }).toList();
    var bodies = original.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(),
        copy(body.root(), change, replacements), body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(),
        body.constructors(), body.attributedOccurrences())).toList();
    return new CopiedAstValues(original.snapshot(), sources, bodies, original.memberOrders(), original.associations(), original.declarationTypes());
  }

  private static SyntaxNode copy(SyntaxNode node, UnaryOperator<SyntaxNode> change, Map<SyntaxNode, SyntaxNode> replacements) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        copy(child.node(), change, replacements))).toList();
    var copied = change.apply(rebuild(node, node.value(), children, node.semanticReference()));
    replacements.put(node, copied);
    return copied;
  }

  private static SyntaxNode rebuild(SyntaxNode node, SyntaxValue value, List<SyntaxChild> children, Optional<SemanticReference> reference) {
    return node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children,
            reference, node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value,
            children, reference);
  }
}
