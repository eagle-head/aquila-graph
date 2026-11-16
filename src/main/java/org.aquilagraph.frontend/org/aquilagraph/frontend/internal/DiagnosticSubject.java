package org.aquilagraph.frontend.internal;

import java.util.Objects;

/** Semantic declaration correlation independent of a creating event's opaque address. */
sealed interface DiagnosticSubject {
  /** A declaration whose source obligations may be explained by the diagnostic. */
  record Declaration(String declarationKey) implements DiagnosticSubject {
    public Declaration {
      Objects.requireNonNull(declarationKey, "declarationKey");
      if (declarationKey.isBlank()) {
        throw new IllegalArgumentException("A diagnostic declaration subject requires a key");
      }
    }
  }

  /** Explicit absence of declaration correlation; origin evidence remains independent. */
  enum None implements DiagnosticSubject {
    INSTANCE
  }
}
