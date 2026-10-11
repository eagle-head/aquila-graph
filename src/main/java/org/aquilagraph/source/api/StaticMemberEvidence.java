package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Selected static member, actual declaring owner, and independent constant classification.
 *
 * @param scopeKey
 *          canonical owning execution scope key
 * @param accessNodeId
 *          access syntax identity
 * @param selectedMemberKey
 *          canonical selected member key
 * @param kind
 *          selected access kind
 * @param declaringOwner
 *          independently retained selected declaring class or interface
 * @param constantVariable
 *          attributed constant-variable read classification
 */
public record StaticMemberEvidence(String scopeKey, String accessNodeId, String selectedMemberKey, StaticMemberKind kind,
    EvidenceValue<JavaTypeReference> declaringOwner, EvidenceValue<Boolean> constantVariable) {
  /** Exact required compiler-selected owner slot. */
  public static final String OWNER_SLOT = "static-member.declaring-owner";
  /** Exact independent compiler classification slot. */
  public static final String CONSTANT_SLOT = "static-member.constant-variable";
  /** Validates supplied shape without inferring a declaring owner from the member key. */
  public StaticMemberEvidence {
    DecisionValueValidation.requireKey(scopeKey, "scopeKey");
    DecisionValueValidation.requireKey(accessNodeId, "accessNodeId");
    DecisionValueValidation.requireKey(selectedMemberKey, "selectedMemberKey");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(declaringOwner, "declaringOwner");
    Objects.requireNonNull(constantVariable, "constantVariable");
    var origin = constantVariable.origin();
    if (!scopeKey.equals(origin.scopeKey()) || !accessNodeId.equals(origin.nodeKey()) || !CONSTANT_SLOT.equals(origin.slot())) {
      throw new IllegalArgumentException("Static constant evidence must retain its exact creating slot");
    }
    var ownerOrigin = declaringOwner.origin();
    if (!scopeKey.equals(ownerOrigin.scopeKey()) || !accessNodeId.equals(ownerOrigin.nodeKey()) || !OWNER_SLOT.equals(ownerOrigin.slot())
        || !ownerOrigin.sourceRange().equals(origin.sourceRange()) || declaringOwner.state() == EvidenceState.NOT_APPLICABLE) {
      throw new IllegalArgumentException("A static member requires its exact applicable declaring-owner slot");
    }
    declaringOwner.knownValue().ifPresent(owner -> {
      if (owner.identity().category() != JavaTypeCategory.DECLARED) {
        throw new IllegalArgumentException("A static access declaring type must identify a class or interface");
      }
    });
    if (constantVariable.knownValue().orElse(false) && kind != StaticMemberKind.FIELD_READ) {
      throw new IllegalArgumentException("Only a static field read may be a constant-variable read");
    }
    if ((kind == StaticMemberKind.FIELD_READ) == (constantVariable.state() == EvidenceState.NOT_APPLICABLE)) {
      throw new IllegalArgumentException("Constant-variable classification applies exactly to a static field read");
    }
  }

  /**
   * Retains a supplied known owner and an independent classification.
   *
   * @param scopeKey
   *          canonical containing execution scope
   * @param accessNodeId
   *          actual member occurrence
   * @param selectedMemberKey
   *          selected member
   * @param kind
   *          source access kind
   * @param declaringType
   *          known declaring type
   * @param constantVariable
   *          independently retained classification
   */
  public StaticMemberEvidence(String scopeKey, String accessNodeId, String selectedMemberKey, StaticMemberKind kind,
      JavaTypeReference declaringType, EvidenceValue<Boolean> constantVariable) {
    this(scopeKey, accessNodeId, selectedMemberKey, kind,
        EvidenceValue.known(declaringType, new EvidenceOrigin(scopeKey, accessNodeId, OWNER_SLOT, constantVariable.origin().sourceRange())),
        constantVariable);
  }

  /**
   * Returns the known declaring type after a consumer has checked the owner slot.
   *
   * @return actual known declaring type
   * @throws IllegalStateException
   *           if the owner is missing or erroneous
   */
  public JavaTypeReference declaringType() {
    return declaringOwner.knownValue().orElseThrow(() -> new IllegalStateException("The selected declaring owner is unavailable"));
  }

  /**
   * Copies an actual compiler classification and explicitly accounts for inapplicable access forms.
   *
   * @param scopeKey
   *          canonical containing execution scope
   * @param accessNodeId
   *          actual access occurrence
   * @param selectedMemberKey
   *          selected member
   * @param kind
   *          actual access kind
   * @param declaringType
   *          actual declaring owner
   * @param constantVariableRead
   *          actual compiler constant-variable read classification
   * @param range
   *          actual optional source range
   * @return independently retained member facts
   */
  public static StaticMemberEvidence known(String scopeKey, String accessNodeId, String selectedMemberKey, StaticMemberKind kind,
      JavaTypeReference declaringType, boolean constantVariableRead, Optional<SourceSpan> range) {
    DecisionValueValidation.requireKey(scopeKey, "scopeKey");
    DecisionValueValidation.requireKey(accessNodeId, "accessNodeId");
    var origin = new EvidenceOrigin(scopeKey, accessNodeId, CONSTANT_SLOT, range);
    var classification = kind == StaticMemberKind.FIELD_READ || constantVariableRead
        ? EvidenceValue.known(constantVariableRead, origin)
        : EvidenceValue.<Boolean>notApplicable(origin, "This access form does not read a constant variable");
    return new StaticMemberEvidence(scopeKey, accessNodeId, selectedMemberKey, kind, declaringType, classification);
  }

}
