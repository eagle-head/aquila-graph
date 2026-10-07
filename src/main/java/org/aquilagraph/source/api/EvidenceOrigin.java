package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Exact local source address of an independently copied fact.
 *
 * @param methodKey
 *          canonical containing method
 * @param nodeKey
 *          exact containing occurrence or explicit nonwritten body node
 * @param slot
 *          named fact field within that occurrence
 * @param sourceRange
 *          actual source range when available
 */
public record EvidenceOrigin(String methodKey, String nodeKey, String slot, Optional<SourceSpan> sourceRange) {
  /** Validates the copied address without asserting compiler authenticity. */
  public EvidenceOrigin {
    DecisionValueValidation.requireKey(methodKey, "methodKey");
    DecisionValueValidation.requireKey(nodeKey, "nodeKey");
    DecisionValueValidation.requireKey(slot, "slot");
    Objects.requireNonNull(sourceRange, "sourceRange");
  }
}
