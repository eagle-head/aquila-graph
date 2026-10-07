package org.aquilagraph.cfg.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StaticMemberKind;
import org.junit.jupiter.api.Test;

class ClassInitializationPolicyTest {
  @Test
  void givenConstantAndNonconstantStaticReads_whenCfgEffectsAreSelected_thenOnlyConstantReadOmitsInitialization() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));
    var constant = StaticMemberEvidence.known("method", "read", "selected:field", StaticMemberKind.FIELD_READ, owner, true, Optional.empty());
    var nonconstant = StaticMemberEvidence.known("method", "read", "selected:field", StaticMemberKind.FIELD_READ, owner, false, Optional.empty());

    // Act
    var constantRequiresInitialization = ClassInitializationPolicy.required(constant);
    var nonconstantRequiresInitialization = ClassInitializationPolicy.required(nonconstant);

    // Assert
    assertFalse(constantRequiresInitialization);
    assertTrue(nonconstantRequiresInitialization);
  }

  @Test
  void givenStaticWritesAndInvocations_whenCfgEffectsAreSelected_thenBothRequireInitialization() {
    // Arrange
    var owner = declaringType(Optional.of("type:DeclaringOwner"));
    var write = StaticMemberEvidence.known("method", "write", "selected:field", StaticMemberKind.FIELD_WRITE, owner, false, Optional.empty());
    var invocation = StaticMemberEvidence.known("method", "invoke", "selected:method", StaticMemberKind.METHOD_INVOKE, owner, false, Optional.empty());

    // Act
    var writeRequiresInitialization = ClassInitializationPolicy.required(write);
    var invocationRequiresInitialization = ClassInitializationPolicy.required(invocation);

    // Assert
    assertTrue(writeRequiresInitialization);
    assertTrue(invocationRequiresInitialization);
  }

  @Test
  void givenAnAbsentDeclaringIdentity_whenTheEffectIsSelected_thenIdentityAvailabilityDoesNotChangeTheEffect() {
    // Arrange
    var access = StaticMemberEvidence.known("method", "access", "selected:field", StaticMemberKind.FIELD_READ,
        declaringType(Optional.empty()), false, Optional.empty());

    // Act
    var required = ClassInitializationPolicy.required(access);

    // Assert
    assertTrue(required);
    assertTrue(access.declaringType().declarationKey().isEmpty());
  }

  private static JavaTypeReference declaringType(Optional<String> key) {
    return new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "DeclaringOwner", "DeclaringOwner"), key);
  }
}
