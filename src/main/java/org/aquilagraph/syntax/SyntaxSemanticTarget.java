package org.aquilagraph.syntax;

import java.util.Objects;
import org.aquilagraph.model.SemanticReference;

/** Explicit project-owned endpoint of a semantic relation. */
public sealed interface SyntaxSemanticTarget {
  /**
   * A source occurrence owned by the same durable AST.
   *
   * @param nodeKey
   *          target source-occurrence key
   */
  record SourceOccurrence(String nodeKey) implements SyntaxSemanticTarget {
    /** Validates the target occurrence key. */
    public SourceOccurrence {
      Objects.requireNonNull(nodeKey, "nodeKey");
      if (nodeKey.isBlank()) {
        throw new IllegalArgumentException("A semantic source-occurrence target key cannot be blank");
      }
    }
  }

  /**
   * An explicit compiler-derived declaration reference, including external declarations.
   *
   * @param reference
   *          project-owned reference value
   */
  record Declaration(SemanticReference reference) implements SyntaxSemanticTarget {
    /** Validates the declaration reference. */
    public Declaration {
      Objects.requireNonNull(reference, "reference");
    }
  }
}
