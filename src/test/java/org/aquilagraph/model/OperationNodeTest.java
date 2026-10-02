package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationNodeTest {
  @Test
  void givenSelectedInitializationTarget_whenOperationIsCreated_thenOnlyItsCopiedTypeReferenceIsRetained() {
    // Arrange
    var target = reference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of("type:Owner"), Optional.of("example.Owner"),
        List.of(), List.of());
    var references = new ArrayList<>(List.of(target));

    // Act
    var operation = initialization(List.of(), Optional.empty(), references);
    references.clear();

    // Assert
    assertEquals(OperationKind.CLASS_INITIALIZATION, operation.kind());
    assertEquals(List.of(target), operation.semanticReferences());
    assertEquals(List.of(), operation.operands());
    assertEquals(Optional.empty(), operation.definedValue());
    assertEquals(Optional.empty(), operation.invocationSemantics());
    assertThrows(UnsupportedOperationException.class, () -> operation.semanticReferences().clear());
  }

  @Test
  void givenMissingOrRepeatedInitializationTargets_whenOperationIsCreated_thenItsExactReferenceCardinalityIsRequired() {
    // Arrange
    var target = target();

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> initialization(List.of(), Optional.empty(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> initialization(List.of(), Optional.empty(), List.of(target, target)));
  }

  @Test
  void givenWrongOrUnresolvedInitializationReference_whenOperationIsCreated_thenItRejectsTheSuppliedSelection() {
    // Arrange
    var references = List.of(
        reference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, Optional.of("selected-method"), Optional.of("example.Owner"), List.of(), List.of()),
        reference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.of("type:Owner"), Optional.of("example.Owner"), List.of(), List.of()),
        reference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of("type:Owner"), Optional.empty(), List.of(), List.of()),
        reference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of("type:Owner"), Optional.of(" "), List.of(), List.of()),
        reference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of("type:Owner"), Optional.of("example.Owner"), List.of("example.Owner"), List.of()),
        reference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of("type:Owner"), Optional.of("example.Owner"), List.of(), List.of("java.lang.Object")));

    // Act and Assert
    for (var reference : references) {
      assertThrows(IllegalArgumentException.class, () -> initialization(List.of(), Optional.empty(), List.of(reference)));
    }
  }

  @Test
  void givenValueInputsOrDefinitionAtInitialization_whenOperationIsCreated_thenNoValueShapeIsAdmitted() {
    // Arrange
    var value = new FlowValue("value", "graph", ValueKind.TEMPORARY, Optional.of("instance"), Optional.empty(), Optional.empty());

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> initialization(List.of(new OperationOperand(0, value)), Optional.empty(), List.of(target())));
    assertThrows(IllegalArgumentException.class, () -> initialization(List.of(), Optional.of(value), List.of(target())));
  }

  @Test
  void givenExistingReadWithSuppliedMemberReferences_whenOperationIsCreated_thenItsBoundedPublicShapeRemainsAvailable() {
    // Arrange
    var reference = reference(ReferenceKind.FIELD, ResolutionStatus.RESOLVED, Optional.of("field"), Optional.of("int"), List.of(), List.of());

    // Act
    var operation = new OperationNode("read", "graph", OperationKind.READ, "read", List.of(), Optional.empty(), Optional.empty(),
        List.of(reference, reference), List.of(), List.of());

    // Assert
    assertEquals(List.of(reference, reference), operation.semanticReferences());
  }

  private static OperationNode initialization(List<OperationOperand> operands, Optional<FlowValue> definition,
      List<SemanticReference> references) {
    return new OperationNode("initialize", "graph", OperationKind.CLASS_INITIALIZATION, "initialize", operands, definition,
        Optional.empty(), references, List.of("unchecked-class-initialization"), List.of());
  }

  private static SemanticReference target() {
    return reference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of("type:Owner"), Optional.of("example.Owner"), List.of(), List.of());
  }

  private static SemanticReference reference(ReferenceKind kind, ResolutionStatus status, Optional<String> key, Optional<String> name,
      List<String> alternatives, List<String> hierarchy) {
    return new SemanticReference(kind, status, key, name, alternatives, hierarchy);
  }
}
