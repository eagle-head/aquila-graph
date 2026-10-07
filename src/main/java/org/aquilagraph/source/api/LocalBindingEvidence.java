package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Copied local binding identity and written declaration shape.
 *
 * @param declarationKey
 *          compiler-established binding identity, absent when attribution failed
 * @param typeForm
 *          written type form
 * @param context
 *          enclosing binding context
 * @param initialized
 *          whether an initializer was written
 * @param annotations
 *          physically owned declaration annotation count
 * @param sharedAnnotationKeys
 *          annotation occurrences owned by an earlier declarator of the same declaration
 */
public record LocalBindingEvidence(Optional<String> declarationKey, TypeForm typeForm, Context context, boolean initialized,
    int annotations, List<String> sharedAnnotationKeys) {
  /** Written local type forms, independent of compiler-established type. */
  public enum TypeForm {
    /** Explicitly written type. */
    DECLARED,
    /** Written {@code var} requests inference. */
    INFERRED,
    /** Enclosing syntax supplies the type without a written type. */
    IMPLICIT
  }

  /** Source binding contexts with their own declaration applicability. */
  public enum Context {
    /** Ordinary local or iteration binding. */
    LOCAL,
    /** Enhanced-for control variable, whose type may be inferred without an initializer. */
    ENHANCED_FOR,
    /** Exception handler parameter. */
    CATCH,
    /** Resource declaration. */
    RESOURCE,
    /** Functional parameter. */
    LAMBDA,
    /** Pattern binding. */
    PATTERN
  }

  /** Validates copied identity and source shape. */
  public LocalBindingEvidence {
    Objects.requireNonNull(declarationKey, "declarationKey");
    declarationKey.ifPresent(key -> SyntaxValueSupport.requireText(key, "declarationKey"));
    Objects.requireNonNull(typeForm, "typeForm");
    Objects.requireNonNull(context, "context");
    sharedAnnotationKeys = List.copyOf(Objects.requireNonNull(sharedAnnotationKeys, "sharedAnnotationKeys"));
    sharedAnnotationKeys.forEach(key -> SyntaxValueSupport.requireText(key, "shared annotation key"));
    if (annotations < 0 || sharedAnnotationKeys.stream().distinct().count() != sharedAnnotationKeys.size()) {
      throw new IllegalArgumentException("Invalid local binding source shape");
    }
  }
}
