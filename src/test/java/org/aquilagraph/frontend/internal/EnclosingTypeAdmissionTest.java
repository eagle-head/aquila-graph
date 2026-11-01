package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CopiedAstValues.Body;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class EnclosingTypeAdmissionTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenContradictoryOwnerArguments_whenReadmitted_thenAvailableBoundsRejectTheView() {
    // Arrange
    var bounds = List.of("Outer<Integer>", "Child<Integer>", "Outer<Integer> & Runnable");

    // Act
    var cases = bounds.stream().map(bound -> DynamicTest.dynamicTest("given" + bound + "_whenOwnerArgumentsConflict_thenReadmissionRejects", () -> {
      // Arrange
      var original = produce("class Outer<A> { class Box { Box(int value) {} } } class Child<A> extends Outer<A> {} "
          + "class Probe { <O extends " + bound + "> Object make(O outer) { return outer.new Box(1); } }");
      var changed = replace(original, type -> {
        var owner = (JavaTypeStructure.Declared) type.enclosingOwnerType().orElseThrow();
        var wrong = new JavaTypeStructure.Declared(owner.declarationKey(), owner.lexicalOwnerKey(), owner.enclosingType(),
            List.of(new JavaTypeStructure.Declared("different-argument", Optional.empty(), List.of())));
        return new JavaTypeStructure.Declared(type.declarationKey(), type.lexicalOwnerKey(), type.enclosingType(), type.arguments(),
            Optional.of(wrong));
      });

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(changed));

      // Assert
      assertEquals("An enclosing owner view conflicts with available source bounds", failure.getMessage());
    }));

    // Assert
    return cases;
  }

  @Test
  void givenLocalOwnerWithConflictingArgument_whenReadmitted_thenAvailableLocalBoundsRejectTheView() throws IOException {
    // Arrange
    var original = produce("class Probe { void make() { class Outer<A> { class Box { Box(int value) {} } } "
        + "class Child<A> extends Outer<A> {} class Uses { <O extends Child<Integer>> Object read(O outer) { return outer.new Box(1); } } } }");
    var changed = replace(original, type -> {
      var owner = (JavaTypeStructure.LocalDeclared) type.enclosingOwnerType().orElseThrow();
      var wrong = new JavaTypeStructure.LocalDeclared(owner.declarationKey(), owner.lexicalOwnerKey(), owner.kind(), owner.enclosingType(),
          List.of(new JavaTypeStructure.Declared("different-argument", Optional.empty(), List.of())));
      return new JavaTypeStructure.Declared(type.declarationKey(), type.lexicalOwnerKey(), type.enclosingType(), type.arguments(), Optional.of(wrong));
    });

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(changed));

    // Assert
    assertEquals("An enclosing owner view conflicts with available source bounds", failure.getMessage());
  }

  @Test
  void givenSelfReferencingCapturedBound_whenReadmitted_thenOwnerLookupTerminatesWithRejection() throws IOException {
    // Arrange
    var original = produce("class Outer<A> { class Box { Box(int value) {} } } "
        + "class Probe { <O extends Outer<Integer>> Object make(O outer) { return outer.new Box(1); } }");
    var cycle = new JavaTypeStructure.Captured(0, new JavaTypeStructure.CaptureReference(0), Optional.empty());
    var changed = replace(original, type -> new JavaTypeStructure.Declared(type.declarationKey(), type.lexicalOwnerKey(),
        Optional.of(cycle), type.arguments(), type.enclosingOwnerType()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(changed));

    // Assert
    assertEquals("An enclosing owner view conflicts with available source bounds", failure.getMessage());
  }

  @Test
  void givenUnrelatedSourceBound_whenReadmitted_thenTheContradictoryOwnerIsRejected() throws IOException {
    // Arrange
    var original = produce("class Outer<A> { class Box { Box(int value) {} } } class Other {} "
        + "class Probe { <O extends Outer<Integer>, P extends Other> Object make(O outer, P other) { return outer.new Box(1); } }");
    var other = original.nodes().stream().map(SyntaxNode::value).filter(SyntaxValue.TypeParameterDeclaration.class::isInstance)
        .map(SyntaxValue.TypeParameterDeclaration.class::cast).filter(value -> value.name().equals("P")).findFirst().orElseThrow();
    var changed = replace(original, type -> new JavaTypeStructure.Declared(type.declarationKey(), type.lexicalOwnerKey(),
        Optional.of(new JavaTypeStructure.Variable(other.declarationKey())), type.arguments(), type.enclosingOwnerType()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(changed));

    // Assert
    assertEquals("An enclosing owner view conflicts with available source bounds", failure.getMessage());
  }

  @Test
  void givenAnotherEnclosingVariable_whenReadmitted_thenItsWrittenQualifierRejectsTheReplacement() throws IOException {
    // Arrange
    var original = produce("class Outer<A> { class Box { Box(int value) {} } } "
        + "class Probe { <O extends Outer<Integer>, P extends Outer<Integer>> Object make(O outer, P other) { return outer.new Box(1); } }");
    var other = original.nodes().stream().map(SyntaxNode::value).filter(SyntaxValue.TypeParameterDeclaration.class::isInstance)
        .map(SyntaxValue.TypeParameterDeclaration.class::cast).filter(value -> value.name().equals("P")).findFirst().orElseThrow();
    var changed = replace(original, type -> new JavaTypeStructure.Declared(type.declarationKey(), type.lexicalOwnerKey(),
        Optional.of(new JavaTypeStructure.Variable(other.declarationKey())), type.arguments(), type.enclosingOwnerType()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(changed));

    // Assert
    assertEquals("An indirect enclosing instance must match its written qualifier type", failure.getMessage());
  }

  private JavaAstAggregate produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Probe.java"), source)));
    var outcome = JavaFrontends.standard().produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow().aggregate();
  }

  private static CopiedAstValues replace(JavaAstAggregate aggregate, UnaryOperator<JavaTypeStructure.Declared> change) {
    var copied = SourceValueCapture.capture(aggregate);
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = copied.sources().stream().map(source -> {
      var root = replace(source.root(), change, replacements);
      var nodes = root.descendantsAndSelf();
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), source.semanticEdges());
    }).toList();
    var bodies = copied.bodies().stream().map(body -> new Body(body.methodKey(), replace(body.root(), change, replacements), body.origin(),
        body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    return new CopiedAstValues(copied.snapshot(), sources, bodies, copied.memberOrders(), copied.associations(), copied.declarationTypes(), copied.scopes());
  }

  private static SyntaxNode replace(SyntaxNode node, UnaryOperator<JavaTypeStructure.Declared> change, Map<SyntaxNode, SyntaxNode> replacements) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replace(child.node(), change, replacements))).toList();
    var type = node.type();
    if (type instanceof SyntaxType.Resolved resolved && resolved.structure().orElse(null) instanceof JavaTypeStructure.Declared declared
        && declared.enclosingOwnerType().isPresent()) {
      type = new SyntaxType.Resolved(resolved.identity(), change.apply(declared));
    }
    var replacement = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children,
            node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference());
    replacements.put(node, replacement);
    return replacement;
  }
}
