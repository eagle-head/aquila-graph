package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Durable semantic relation emitted by a source occurrence.
 *
 * @param sourceKey
 *          source occurrence key
 * @param relation
 *          typed semantic relation
 * @param target
 *          explicit project-owned target endpoint
 */
public record SyntaxSemanticEdge(String sourceKey, SyntaxSemanticRelation relation, SyntaxSemanticTarget target) {
  /** Validates the source, relation, and target. */
  public SyntaxSemanticEdge {
    Objects.requireNonNull(sourceKey, "sourceKey");
    if (sourceKey.isBlank()) {
      throw new IllegalArgumentException("A semantic edge source key cannot be blank");
    }
    Objects.requireNonNull(relation, "relation");
    Objects.requireNonNull(target, "target");
    if ((relation == SyntaxSemanticRelation.ANNOTATED_TARGET) != (target instanceof SyntaxSemanticTarget.AnnotationApplication)) {
      throw new IllegalArgumentException("A semantic relation requires its corresponding target variant");
    }
  }
}
