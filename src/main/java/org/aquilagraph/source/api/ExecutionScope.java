package org.aquilagraph.source.api;

import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;

/** A source owner whose executable descendants share copied fact addresses. */
public sealed interface ExecutionScope {
  /**
   * Returns the canonical scope identity.
   *
   * @return actual declaration key or owner and written initializer coordinates
   */
  String key();

  /**
   * An actual method or constructor body.
   *
   * @param key
   *          canonical executable declaration identity
   */
  record Method(String key) implements ExecutionScope {
    /** Requires a nonblank executable identity. */
    public Method {
      DecisionValueValidation.requireKey(key, "key");
    }
  }

  /**
   * A written field and its optional initializer.
   *
   * @param key
   *          canonical field declaration identity
   * @param ownerKey
   *          canonical containing type identity
   */
  record Field(String key, String ownerKey) implements ExecutionScope {
    /** Validates the field's actual type owner. */
    public Field {
      DecisionValueValidation.requireKey(key, "key");
      DecisionValueValidation.requireKey(ownerKey, "ownerKey");
      if (!key.startsWith(ownerKey + "#field:") || key.length() == ownerKey.length() + 7) {
        throw new IllegalArgumentException("A field execution scope requires its canonical declaration owner");
      }
    }
  }

  /**
   * A written static or instance initializer occurrence.
   *
   * @param key
   *          canonical owner and written block coordinates
   * @param ownerKey
   *          canonical containing type identity
   * @param origin
   *          exact written initializer range
   * @param kind
   *          written static or instance block form
   */
  record Initializer(String key, String ownerKey, SourceSpan origin, SyntaxKind kind) implements ExecutionScope {
    /** Requires an actual initializer form and explicit owner. */
    public Initializer {
      DecisionValueValidation.requireKey(key, "key");
      DecisionValueValidation.requireKey(ownerKey, "ownerKey");
      Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(kind, "kind");
      if (!key.equals(ownerKey + ":initializer:" + origin.startOffset() + ':' + origin.endOffsetExclusive())) {
        throw new IllegalArgumentException("An initializer identity must select its owner's exact written block range");
      }
      if (kind != SyntaxKind.STATIC_INITIALIZER_BLOCK && kind != SyntaxKind.INSTANCE_INITIALIZER_BLOCK) {
        throw new IllegalArgumentException("An initializer scope requires a written static or instance block");
      }
    }
  }
}
