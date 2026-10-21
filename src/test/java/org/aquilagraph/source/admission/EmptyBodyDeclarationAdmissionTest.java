package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
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
    var values = CopiedAstValues.capture(original.aggregate());
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
    var values = CopiedAstValues.capture(original.aggregate());
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
    var values = CopiedAstValues.capture(original.aggregate());
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
  void givenAnEarlierEnumFormat_whenReencodedAndDecoded_thenOriginalProfileIdentityAndValuesRemainCompatible() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "enum Example { FIRST(1) { int value() { return 2; } }; Example(int value) {} }")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION).result().orElseThrow();
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var historical = AstResultAdmission.admit(SourceAnalysisFixture.historicalInput(input), original.profile(), original.identity().semanticProductionRevision(),
        AstResult.ENUM_SOURCE_MODEL_VERSION);
    // Act
    var bytes = AstCodec.encode(historical);
    var retained = AstCodec.decode(bytes, historical.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(historical.identity(), retained.identity());
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
      var values = CopiedAstValues.capture(original.aggregate());
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

  @Test
  void givenDeclarationValuesWithAnOlderRequestedFormat_whenReadmitted_thenTheNewSourceContractCannotBeMisidentified() throws IOException {
    // Arrange
    var ast = produce();
    var input = new SyntaxAnalysisInput(ast.snapshot(), ast.environment(), ast.program(), Optional.of(ast.aggregate()), ast.diagnostics(),
        ast.coverage());
    // Act
    var error = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(SourceAnalysisFixture.historicalInput(input), ast.profile(),
        ast.identity().semanticProductionRevision(), AstResult.ENUM_SOURCE_MODEL_VERSION));
    // Assert
    assertTrue(error.getMessage().contains("java-source/17"), error.toString());
  }

  private AstResult produce() throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { ; ; }")));
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
