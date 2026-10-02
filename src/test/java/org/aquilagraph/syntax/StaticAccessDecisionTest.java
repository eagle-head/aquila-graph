package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeReference;
import org.junit.jupiter.api.Test;

class StaticAccessDecisionTest {
  @Test
  void givenConstantAndNonconstantFieldReads_whenDecisionsAreCreated_thenOnlyConstantReadOmitsInitialization() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var constant = new StaticAccessDecision("method", "read", "selected:field", StaticAccessKind.FIELD_READ, owner, true);
    var nonconstant = new StaticAccessDecision("method", "read", "selected:field", StaticAccessKind.FIELD_READ, owner, false);

    // Assert
    assertFalse(constant.triggersInitialization());
    assertTrue(nonconstant.triggersInitialization());
    assertTrue(constant.constantVariableRead());
    assertFalse(nonconstant.constantVariableRead());
    assertEquals(owner, constant.declaringType());
    assertEquals("selected:field", constant.selectedMemberKey());
  }

  @Test
  void givenStaticWritesAndInvocations_whenDecisionsAreCreated_thenBothKindsTriggerInitialization() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var write = new StaticAccessDecision("method", "write", "selected:field", StaticAccessKind.FIELD_WRITE, owner, false);
    var invoke = new StaticAccessDecision("method", "invoke", "selected:method", StaticAccessKind.METHOD_INVOKE, owner, false);

    // Assert
    assertTrue(write.triggersInitialization());
    assertTrue(invoke.triggersInitialization());
    assertEquals(StaticAccessKind.FIELD_WRITE, write.kind());
    assertEquals(StaticAccessKind.METHOD_INVOKE, invoke.kind());
  }

  @Test
  void givenOpaqueMemberKeyAndDeclaringType_whenDecisionIsCreated_thenOwnerIsRetainedWithoutKeyParsing() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var decision = new StaticAccessDecision("method", "access", "opaque:selected-member", StaticAccessKind.FIELD_READ, owner, false);

    // Assert
    assertEquals("method", decision.methodKey());
    assertEquals("access", decision.accessNodeId());
    assertEquals("opaque:selected-member", decision.selectedMemberKey());
    assertEquals(owner, decision.declaringType());
  }

  @Test
  void givenAbsentDeclaringIdentity_whenDecisionIsCreated_thenUnknownIdentityRemainsAbsentForLaterAdmission() {
    // Arrange
    var owner = declaringType(Optional.empty());

    // Act
    var decision = new StaticAccessDecision("method", "access", "selected:field", StaticAccessKind.FIELD_READ, owner, false);

    // Assert
    assertTrue(decision.declaringType().declarationKey().isEmpty());
    assertEquals(owner, decision.declaringType());
    assertTrue(decision.triggersInitialization());
  }

  @Test
  void givenConstantFlagOnWriteOrInvocation_whenDecisionIsCreated_thenEachImpossibleFlagIsRejected() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var write = assertThrows(IllegalArgumentException.class,
        () -> new StaticAccessDecision("method", "access", "selected:field", StaticAccessKind.FIELD_WRITE, owner, true));
    var invoke = assertThrows(IllegalArgumentException.class,
        () -> new StaticAccessDecision("method", "access", "selected:method", StaticAccessKind.METHOD_INVOKE, owner, true));

    // Assert
    for (var exception : List.of(write, invoke)) {
      assertEquals("Only a static field read may be a constant-variable read", exception.getMessage());
    }
  }

  @Test
  void givenNondeclaredOwner_whenDecisionIsCreated_thenInitializationTargetCategoryIsRejected() {
    // Arrange
    var owner = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), Optional.empty());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new StaticAccessDecision("method", "access", "selected:field", StaticAccessKind.FIELD_READ, owner, false));

    // Assert
    assertEquals("A static access declaring type must identify a class or interface", exception.getMessage());
  }

  @Test
  void givenBlankKeysOrNullRequiredField_whenDecisionIsCreated_thenInvalidLocalInputIsRejected() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var method = assertThrows(IllegalArgumentException.class,
        () -> new StaticAccessDecision(" ", "access", "selected:field", StaticAccessKind.FIELD_READ, owner, false));
    var access = assertThrows(IllegalArgumentException.class,
        () -> new StaticAccessDecision("method", " ", "selected:field", StaticAccessKind.FIELD_READ, owner, false));
    var member = assertThrows(IllegalArgumentException.class,
        () -> new StaticAccessDecision("method", "access", " ", StaticAccessKind.FIELD_READ, owner, false));

    // Assert
    assertEquals("methodKey cannot be blank", method.getMessage());
    assertEquals("accessNodeId cannot be blank", access.getMessage());
    assertEquals("selectedMemberKey cannot be blank", member.getMessage());
    assertThrows(NullPointerException.class,
        () -> new StaticAccessDecision("method", "access", "selected:field", null, owner, false));
    assertThrows(NullPointerException.class,
        () -> new StaticAccessDecision("method", "access", "selected:field", StaticAccessKind.FIELD_READ, null, false));
  }

  private static JavaTypeReference declaringType(Optional<String> key) {
    return new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "DeclaringOwner", "DeclaringOwner"), key);
  }
}
