package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StaticMemberEvidenceTest {
  @Test
  void givenConstantAndNonconstantFieldReads_whenDecisionsAreCreated_thenSelectedSourceClassificationsRemainDistinct() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var constant = StaticMemberEvidence.known("method", "read", "selected:field", StaticMemberKind.FIELD_READ, owner, true, Optional.empty());
    var nonconstant = StaticMemberEvidence.known("method", "read", "selected:field", StaticMemberKind.FIELD_READ, owner, false, Optional.empty());

    // Assert
    assertTrue(constant.constantVariable().knownValue().orElseThrow());
    assertFalse(nonconstant.constantVariable().knownValue().orElseThrow());
    assertEquals(owner, constant.declaringType());
    assertEquals("selected:field", constant.selectedMemberKey());
  }

  @Test
  void givenStaticWritesAndInvocations_whenDecisionsAreCreated_thenSelectedAccessKindsRemainDistinct() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var write = StaticMemberEvidence.known("method", "write", "selected:field", StaticMemberKind.FIELD_WRITE, owner, false, Optional.empty());
    var invoke = StaticMemberEvidence.known("method", "invoke", "selected:method", StaticMemberKind.METHOD_INVOKE, owner, false, Optional.empty());

    // Assert
    assertEquals(StaticMemberKind.FIELD_WRITE, write.kind());
    assertEquals(StaticMemberKind.METHOD_INVOKE, invoke.kind());
  }

  @Test
  void givenOpaqueMemberKeyAndDeclaringType_whenDecisionIsCreated_thenOwnerIsRetainedWithoutKeyParsing() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var decision = StaticMemberEvidence.known("method", "access", "opaque:selected-member", StaticMemberKind.FIELD_READ, owner, false, Optional.empty());

    // Assert
    assertEquals("method", decision.scopeKey());
    assertEquals("access", decision.accessNodeId());
    assertEquals("opaque:selected-member", decision.selectedMemberKey());
    assertEquals(owner, decision.declaringType());
  }

  @Test
  void givenAbsentDeclaringIdentity_whenDecisionIsCreated_thenUnknownIdentityRemainsAbsentForLaterAdmission() {
    // Arrange
    var owner = declaringType(Optional.empty());

    // Act
    var decision = StaticMemberEvidence.known("method", "access", "selected:field", StaticMemberKind.FIELD_READ, owner, false, Optional.empty());

    // Assert
    assertTrue(decision.declaringType().declarationKey().isEmpty());
    assertEquals(owner, decision.declaringType());
  }

  @Test
  void givenConstantFlagOnWriteOrInvocation_whenDecisionIsCreated_thenEachImpossibleFlagIsRejected() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var write = assertThrows(IllegalArgumentException.class,
        () -> StaticMemberEvidence.known("method", "access", "selected:field", StaticMemberKind.FIELD_WRITE, owner, true, Optional.empty()));
    var invoke = assertThrows(IllegalArgumentException.class,
        () -> StaticMemberEvidence.known("method", "access", "selected:method", StaticMemberKind.METHOD_INVOKE, owner, true, Optional.empty()));

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
        () -> StaticMemberEvidence.known("method", "access", "selected:field", StaticMemberKind.FIELD_READ, owner, false, Optional.empty()));

    // Assert
    assertEquals("A static access declaring type must identify a class or interface", exception.getMessage());
  }

  @Test
  void givenBlankKeysOrNullRequiredField_whenDecisionIsCreated_thenInvalidLocalInputIsRejected() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));

    // Act
    var method = assertThrows(IllegalArgumentException.class,
        () -> StaticMemberEvidence.known(" ", "access", "selected:field", StaticMemberKind.FIELD_READ, owner, false, Optional.empty()));
    var access = assertThrows(IllegalArgumentException.class,
        () -> StaticMemberEvidence.known("method", " ", "selected:field", StaticMemberKind.FIELD_READ, owner, false, Optional.empty()));
    var member = assertThrows(IllegalArgumentException.class,
        () -> StaticMemberEvidence.known("method", "access", " ", StaticMemberKind.FIELD_READ, owner, false, Optional.empty()));

    // Assert
    assertEquals("scopeKey cannot be blank", method.getMessage());
    assertEquals("accessNodeId cannot be blank", access.getMessage());
    assertEquals("selectedMemberKey cannot be blank", member.getMessage());
    assertThrows(NullPointerException.class,
        () -> StaticMemberEvidence.known("method", "access", "selected:field", null, owner, false, Optional.empty()));
    assertThrows(NullPointerException.class,
        () -> StaticMemberEvidence.known("method", "access", "selected:field", StaticMemberKind.FIELD_READ, null, false, Optional.empty()));
  }

  private static JavaTypeReference declaringType(Optional<String> key) {
    return new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "DeclaringOwner", "DeclaringOwner"), key);
  }
}
