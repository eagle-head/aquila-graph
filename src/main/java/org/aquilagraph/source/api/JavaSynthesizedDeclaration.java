package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Compiler-exposed direct type member without a written source declaration or detailed member aggregate.
 *
 * @param declarationKey
 *          canonical member key
 * @param ownerTypeKey
 *          declaring type key
 * @param kind
 *          compiler declaration family
 */
public record JavaSynthesizedDeclaration(String declarationKey, String ownerTypeKey, Kind kind) {
  /** Compiler declaration family. */
  public enum Kind {
    /** Field. */
    FIELD,
    /** Enum constant. */
    ENUM_CONSTANT,
    /** Method. */
    METHOD,
    /** Constructor. */
    CONSTRUCTOR,
    /** Member class. */
    CLASS,
    /** Member interface. */
    INTERFACE,
    /** Member enum. */
    ENUM,
    /** Member record. */
    RECORD,
    /** Member annotation interface. */
    ANNOTATION_TYPE
  }

  /** Validates synthesized declaration ownership. */
  public JavaSynthesizedDeclaration {
    Objects.requireNonNull(declarationKey, "declarationKey");
    Objects.requireNonNull(ownerTypeKey, "ownerTypeKey");
    Objects.requireNonNull(kind, "kind");
    if (!declarationKey.startsWith(ownerTypeKey + "#") || declarationKey.length() <= ownerTypeKey.length() + 1) {
      throw new IllegalArgumentException("A synthesized declaration key must belong to its type owner");
    }
  }
}
