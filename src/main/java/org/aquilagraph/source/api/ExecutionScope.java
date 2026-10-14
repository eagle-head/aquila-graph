package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
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
      DecisionValueValidation.requireKey(key, "method key");
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
      DecisionValueValidation.requireKey(key, "field key");
      DecisionValueValidation.requireKey(ownerKey, "ownerKey");
      if (!key.startsWith(ownerKey + "#field:") || key.length() == ownerKey.length() + 7) {
        throw new IllegalArgumentException("A field execution scope requires its canonical declaration owner");
      }
    }
  }

  /**
   * A written enum constant and its constructor arguments.
   *
   * @param key
   *          canonical constant declaration identity
   * @param ownerKey
   *          canonical containing enum identity
   * @param selectedConstructor
   *          copied source constructor target, absent when attribution is unavailable
   */
  record EnumConstant(String key, String ownerKey, Optional<SemanticReference> selectedConstructor) implements ExecutionScope {
    /** Requires the constant's actual enum owner. */
    public EnumConstant {
      DecisionValueValidation.requireKey(key, "enum constant key");
      DecisionValueValidation.requireKey(ownerKey, "ownerKey");
      Objects.requireNonNull(selectedConstructor, "selectedConstructor").ifPresent(reference -> {
        if (reference.referenceKind() != ReferenceKind.CONSTRUCTOR
            || reference.symbolKey().filter(selected -> !selected.startsWith(ownerKey + "#<init>(")).isPresent()) {
          throw new IllegalArgumentException("An enum constant requires a source constructor of its containing enum");
        }
      });
      if (!key.startsWith(ownerKey + "#enum:") || key.length() == ownerKey.length() + 6) {
        throw new IllegalArgumentException("An enum execution scope requires its canonical constant owner");
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
      DecisionValueValidation.requireKey(key, "initializer key");
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
