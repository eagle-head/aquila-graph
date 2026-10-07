package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Independently retained applicability slot for an actual method-owned invocation.
 *
 * @param applicability
 *          typed copied classification and exact creating provenance
 */
public record InvocationEvidence(EvidenceValue<InvocationApplicability> applicability) {
  /** Exact neutral fact-slot identity. */
  public static final String SLOT = "invocation-applicability";

  /** Keeps optional classification independent of the invocation's required source target and types. */
  public InvocationEvidence {
    Objects.requireNonNull(applicability, "applicability");
    var origin = applicability.origin();
    if (!SLOT.equals(origin.slot()) || applicability.state() == EvidenceState.NOT_APPLICABLE) {
      throw new IllegalArgumentException("An invocation has one applicable, explicitly accounted classification slot");
    }
    applicability.knownValue().ifPresent(value -> {
      if (!value.methodKey().equals(origin.methodKey()) || !value.consumerNodeId().equals(origin.nodeKey())) {
        throw new IllegalArgumentException("Invocation classification differs from its exact creating occurrence");
      }
    });
  }

  /**
   * Returns the exact containing method.
   *
   * @return canonical method key
   */
  public String methodKey() {
    return applicability.origin().methodKey();
  }

  /**
   * Returns the actual invocation occurrence.
   *
   * @return owned consumer node key
   */
  public String consumerNodeId() {
    return applicability.origin().nodeKey();
  }

  /**
   * Retains a known compiler applicability classification without changing its source values.
   *
   * @param value
   *          copied selected classification
   * @param range
   *          actual optional source range
   * @return independently known slot
   */
  public static InvocationEvidence known(InvocationApplicability value, Optional<SourceSpan> range) {
    Objects.requireNonNull(value, "value");
    return new InvocationEvidence(EvidenceValue.known(value, new EvidenceOrigin(value.methodKey(), value.consumerNodeId(), SLOT, range)));
  }
}
