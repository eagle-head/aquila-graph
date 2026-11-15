package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class EmptyBodyDeclarationAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenMissingEmptyMemberAndAnAdjustedShape_whenReadmitted_thenIndependentSourceEvidenceRejectsTheOmission() throws IOException {
    // Arrange
    var original = produce();
    var values = SourceValueCapture.capture(original.aggregate());
    var root = values.sources().getFirst().root();
    var type = root.requiredChild(SyntaxRole.TYPE_DECLARATION);
    var declaration = type.value(SyntaxValue.TypeDeclaration.class);
    var shape = declaration.shape();
    var changedValue = new SyntaxValue.TypeDeclaration(declaration.name(), declaration.declarationKey(), declaration.writtenModifiers(),
        declaration.effectiveModifiers(), new SyntaxValue.TypeDeclaration.Shape(shape.annotations(), shape.typeParameters(), shape.superclass(),
            shape.interfaces(), shape.permittedSubclasses(), shape.recordComponents(), 1));
    var changedType = copy(type, changedValue, List.of(type.children().getFirst()));
    var changedRoot = copy(root, root.value(), List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, changedType)));
    var removed = type.children(SyntaxRole.MEMBER).getLast().nodeId();
    var changed = withRoot(values, changedRoot, values.associations().stream().filter(value -> !value.nodeKey().equals(removed)).toList());
    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> readmit(original, changed));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(error.getMessage().contains("support") || error.getMessage().contains("source"), error.toString());
  }

  @Test
  void givenReorderedEmptyMemberRanges_whenTheContainingTypeIsConstructed_thenIntrinsicMemberOrderingRejectsTheContradiction()
      throws IOException {
    // Arrange
    var original = produce();
    var values = SourceValueCapture.capture(original.aggregate());
    var root = values.sources().getFirst().root();
    var type = root.requiredChild(SyntaxRole.TYPE_DECLARATION);
    var members = type.children(SyntaxRole.MEMBER);
    var first = members.getFirst();
    var second = members.getLast();
    var changedFirst = SyntaxNode.source(first.sourceFileKey(), first.structuralPath(), first.sourceKind(), first.syntaxKind(), first.type(),
        first.value(), List.of(), Optional.empty(), second.origin().orElseThrow());
    var changedSecond = SyntaxNode.source(second.sourceFileKey(), second.structuralPath(), second.sourceKind(), second.syntaxKind(), second.type(),
        second.value(), List.of(), Optional.empty(), first.origin().orElseThrow());
    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> copy(type, type.value(),
        List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, changedFirst), new SyntaxChild(SyntaxRole.MEMBER, 1, changedSecond))));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(error.getMessage().contains("order"), error.toString());
  }

  @Test
  void givenForeignEmptyDeclarationOwner_whenReadmitted_thenSourceOwnershipCannotBeReassigned() throws IOException {
    // Arrange
    var original = produce();
    var values = SourceValueCapture.capture(original.aggregate());
    var associations = values.associations().stream().map(value -> value.ownerPath().lastStep()
        .filter(step -> step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION).isEmpty() ? value
            : new WrittenSourceAssociation(value.nodeKey(), value.sourceFileKey(),
                SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 99).child(SourceOwnerPath.Role.EMPTY_DECLARATION, 0),
                value.sourceKind(), value.origin())).toList();
    var changed = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), associations,
        values.declarationTypes(), values.scopes());
    // Act
    var error = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(changed));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(error.getMessage().contains("support") || error.getMessage().contains("owner") || error.getMessage().contains("source"),
        error.toString());
  }

  @Test
  void givenEnumInitializationProfileFacts_whenRetained_thenProfileIdentityAndValuesRemainExact() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "enum Example { FIRST(1) { int value() { return 2; } }; Example(int value) {} }")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION).result().orElseThrow();
    // Act
    var bytes = AstCodec.encode(original);
    var retained = AstCodec.decode(bytes, original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(original.identity(), retained.identity());
    assertEquals(AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, retained.profile());
    assertEquals(original.aggregate(), retained.aggregate());
    assertArrayEquals(bytes, AstCodec.encode(retained));
  }

  @TestFactory
  Stream<DynamicTest> givenContradictoryEmptyDeclarationOrdinals_whenCopiedValuesAreAdmitted_thenExactOccurrenceTopologyIsRequired() {
    // Arrange
    var contradictoryOrdinals = List.of(List.of(1, 1), List.of(1, 0), List.of(0, 99));
    return contradictoryOrdinals.stream().map(ordinals -> DynamicTest.dynamicTest(ordinals.toString(), () -> {
      var original = produce();
      var values = SourceValueCapture.capture(original.aggregate());
      var associations = values.associations().stream().map(value -> {
        var step = value.ownerPath().lastStep();
        if (step.filter(last -> last.role() == SourceOwnerPath.Role.EMPTY_DECLARATION).isEmpty()) {
          return value;
        }
        var changedPath = value.ownerPath().parent().orElseThrow()
            .child(SourceOwnerPath.Role.EMPTY_DECLARATION, ordinals.get(step.orElseThrow().ordinal()));
        return new WrittenSourceAssociation(value.nodeKey(), value.sourceFileKey(), changedPath, value.sourceKind(), value.origin());
      }).toList();
      var changed = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), associations,
          values.declarationTypes(), values.scopes());
      // Act
      var error = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(changed));
      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertEquals(original.aggregate(), AstValueAdmission.admit(values));
      assertTrue(error.getMessage().contains("ordinal"), error.toString());
    }));
  }

  private AstResult produce() throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example { ; ; }")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_DECLARATIONS).result().orElseThrow();
  }

  private static SyntaxNode copy(SyntaxNode node, SyntaxValue value, List<SyntaxChild> children) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children,
        node.semanticReference(), node.origin().orElseThrow());
  }

  private static CopiedAstValues withRoot(CopiedAstValues values, SyntaxNode root, List<WrittenSourceAssociation> associations) {
    var nodes = root.descendantsAndSelf();
    var source = new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
        nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(),
        nodes.stream().flatMap(node -> node.semanticEdges().stream()).toList());
    return new CopiedAstValues(values.snapshot(), List.of(source), values.bodies(), values.memberOrders(), associations,
        values.declarationTypes(), values.scopes());
  }

  private static SyntaxAnalysisInput readmit(AstResult original, CopiedAstValues values) {
    return new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(AstValueAdmission.admit(values)),
        original.diagnostics(), original.coverage());
  }
}
