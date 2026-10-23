package org.aquilagraph.source.api;

import org.aquilagraph.source.internal.values.DecisionValueValidation;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Required source and target types retained independently of optional conversion classification.
 *
 * @param scopeKey
 *          canonical containing execution scope
 * @param consumerNodeId
 *          exact consuming occurrence
 * @param role
 *          actual direct child role
 * @param position
 *          role-local child position
 * @param sourceType
 *          actual attributed child type
 * @param selectedTarget
 *          actual selected use target
 * @param conversion
 *          independent selected conversion classification
 * @param referenceCheck
 *          independent reference-cast classification
 */
public record ChildUseEvidence(String scopeKey, String consumerNodeId, SyntaxRole role, int position, JavaTypeIdentity sourceType,
    JavaTypeIdentity selectedTarget, EvidenceValue<ConversionEvidence> conversion, EvidenceValue<Boolean> referenceCheck) {
  /** Checks exact independent slots and all supplied known correlations. */
  public ChildUseEvidence {
    DecisionValueValidation.requireKey(scopeKey, "scopeKey");
    DecisionValueValidation.requireKey(consumerNodeId, "consumerNodeId");
    Objects.requireNonNull(role, "role");
    if (position < 0) {
      throw new IllegalArgumentException("A child use position cannot be negative");
    }
    DecisionValueValidation.requireValueType(sourceType, "sourceType");
    DecisionValueValidation.requireValueType(selectedTarget, "selectedTarget");
    Objects.requireNonNull(conversion, "conversion");
    Objects.requireNonNull(referenceCheck, "referenceCheck");
    validateOrigin(scopeKey, consumerNodeId, slot("conversion", role, position), conversion.origin());
    validateOrigin(scopeKey, consumerNodeId, slot("reference-check", role, position), referenceCheck.origin());
    if (conversion.state() == EvidenceState.NOT_APPLICABLE) {
      throw new IllegalArgumentException("An actual child use has an applicable conversion classification slot");
    }
    conversion.knownValue().ifPresent(value -> {
      if (!scopeKey.equals(value.scopeKey()) || !consumerNodeId.equals(value.consumerNodeId()) || role != value.role()
          || position != value.position() || !sourceType.equals(value.sourceType()) || !selectedTarget.equals(value.selectedTarget())) {
        throw new IllegalArgumentException("Conversion classification contradicts its exact required child-use facts");
      }
    });
    if (referenceCheck.knownValue().isPresent()) {
      if (role != SyntaxRole.EXPRESSION || !DecisionValueValidation.isReference(selectedTarget)
          || (!DecisionValueValidation.isReference(sourceType) && sourceType.category() != JavaTypeCategory.NULL)) {
        throw new IllegalArgumentException("A reference-cast classification requires a reference expression use");
      }
      if (sourceType.category() == JavaTypeCategory.NULL && referenceCheck.knownValue().orElseThrow()) {
        throw new IllegalArgumentException("A null reference cast cannot fail its validity check");
      }
    }
  }

  /**
   * Copies a known classification and explicitly accounts for reference-check applicability.
   *
   * @param value
   *          actual selected conversion facts
   * @param consumerKind
   *          actual consuming source form
   * @param range
   *          actual optional consumer range
   * @return independently retained child use
   */
  public static ChildUseEvidence known(ConversionEvidence value, SyntaxKind consumerKind, Optional<SourceSpan> range) {
    var conversionOrigin = new EvidenceOrigin(value.scopeKey(), value.consumerNodeId(), slot("conversion", value.role(), value.position()),
        range);
    var checkOrigin = new EvidenceOrigin(value.scopeKey(), value.consumerNodeId(), slot("reference-check", value.role(), value.position()),
        range);
    var applicable = consumerKind == SyntaxKind.TYPE_CAST && DecisionValueValidation.isReference(value.selectedTarget())
        && (DecisionValueValidation.isReference(value.sourceType()) || value.sourceType().category() == JavaTypeCategory.NULL);
    var check = applicable
        ? EvidenceValue.<Boolean>missing(checkOrigin, "The compiler reference-cast classification is unavailable")
        : EvidenceValue.<Boolean>notApplicable(checkOrigin, "This source use has no reference-cast classification");
    return new ChildUseEvidence(value.scopeKey(), value.consumerNodeId(), value.role(), value.position(), value.sourceType(),
        value.selectedTarget(), EvidenceValue.known(value, conversionOrigin), check);
  }

  private static String slot(String field, SyntaxRole role, int position) {
    return "child-use." + field + "/" + role.stableId() + "/" + position;
  }

  private static void validateOrigin(String scopeKey, String nodeKey, String slot, EvidenceOrigin origin) {
    if (!scopeKey.equals(origin.scopeKey()) || !nodeKey.equals(origin.nodeKey()) || !slot.equals(origin.slot())) {
      throw new IllegalArgumentException("Child-use evidence requires its exact creating slot");
    }
  }
}
