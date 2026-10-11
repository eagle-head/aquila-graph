package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Selected ordinary conversion at one execution-scope-owned direct child use.
 *
 * @param scopeKey
 *          canonical owning execution scope key
 * @param consumerNodeId
 *          consuming syntax node identity
 * @param role
 *          direct child role
 * @param position
 *          zero-based position within the role
 * @param sourceType
 *          resolved child value type
 * @param selectedTarget
 *          selected use target
 * @param checkedReferenceTarget
 *          checked wrapper target preceding unboxing in a primitive cast
 * @param unboxedType
 *          selected primitive unboxing result
 * @param effectivePrimitiveType
 *          primitive result after optional widening
 * @param preBoxPrimitiveType
 *          primitive value type immediately before boxing
 * @param boxedType
 *          selected wrapper type
 */
public record ConversionEvidence(String scopeKey, String consumerNodeId, SyntaxRole role, int position, JavaTypeIdentity sourceType,
    JavaTypeIdentity selectedTarget, Optional<JavaTypeIdentity> checkedReferenceTarget, Optional<JavaTypeIdentity> unboxedType,
    Optional<JavaTypeIdentity> effectivePrimitiveType, Optional<JavaTypeIdentity> preBoxPrimitiveType,
    Optional<JavaTypeIdentity> boxedType) {
  /** Validates the supplied value shape without authenticating compiler selection. */
  public ConversionEvidence {
    DecisionValueValidation.requireKey(scopeKey, "scopeKey");
    DecisionValueValidation.requireKey(consumerNodeId, "consumerNodeId");
    Objects.requireNonNull(role, "role");
    if (position < 0) {
      throw new IllegalArgumentException("A typed use position cannot be negative");
    }
    DecisionValueValidation.requireValueType(sourceType, "sourceType");
    DecisionValueValidation.requireValueType(selectedTarget, "selectedTarget");
    Objects.requireNonNull(checkedReferenceTarget, "checkedReferenceTarget");
    Objects.requireNonNull(unboxedType, "unboxedType");
    Objects.requireNonNull(effectivePrimitiveType, "effectivePrimitiveType");
    Objects.requireNonNull(preBoxPrimitiveType, "preBoxPrimitiveType");
    Objects.requireNonNull(boxedType, "boxedType");
    if (unboxedType.isPresent() != effectivePrimitiveType.isPresent() || preBoxPrimitiveType.isPresent() != boxedType.isPresent()) {
      throw new IllegalArgumentException("Each selected conversion requires its paired type fields");
    }
    if (unboxedType.isPresent() && preBoxPrimitiveType.isPresent()) {
      throw new IllegalArgumentException("Boxing and unboxing are mutually exclusive for one use");
    }
    if (checkedReferenceTarget.isPresent() && unboxedType.isEmpty()) {
      throw new IllegalArgumentException("A checked wrapper target requires selected unboxing");
    }
    if (unboxedType.isPresent()) {
      validateUnboxing(sourceType, selectedTarget, role, checkedReferenceTarget, unboxedType.orElseThrow(),
          effectivePrimitiveType.orElseThrow());
    } else if (preBoxPrimitiveType.isPresent()) {
      validateBoxing(sourceType, selectedTarget, preBoxPrimitiveType.orElseThrow(), boxedType.orElseThrow());
    } else if (DecisionValueValidation.isPrimitive(sourceType) != DecisionValueValidation.isPrimitive(selectedTarget)) {
      throw new IllegalArgumentException("A primitive and reference use requires a selected conversion");
    } else if (DecisionValueValidation.isPrimitive(sourceType)
        && (sourceType.category() == JavaTypeCategory.BOOLEAN) != (selectedTarget.category() == JavaTypeCategory.BOOLEAN)) {
      throw new IllegalArgumentException("Boolean and numeric primitives cannot share a conversion");
    }
  }

  private static void validateUnboxing(JavaTypeIdentity source, JavaTypeIdentity target, SyntaxRole role,
      Optional<JavaTypeIdentity> checked, JavaTypeIdentity unboxed, JavaTypeIdentity effective) {
    if (!DecisionValueValidation.isReference(source) || !DecisionValueValidation.isPrimitive(unboxed)
        || !DecisionValueValidation.isWideningPrimitive(unboxed, effective) || !target.equals(effective)) {
      throw new IllegalArgumentException("Unboxing requires consistent reference, primitive and selected target types");
    }
    if (checked.isPresent()) {
      if (role != SyntaxRole.EXPRESSION || !DecisionValueValidation.isWrapper(checked.orElseThrow(), unboxed)) {
        throw new IllegalArgumentException("A checked unboxing step requires the matching cast wrapper target");
      }
    } else if (source.category() == JavaTypeCategory.DECLARED && !DecisionValueValidation.isWrapper(source, unboxed)) {
      throw new IllegalArgumentException("A declared unboxing source must be the matching wrapper or have a checked wrapper target");
    }
  }

  private static void validateBoxing(JavaTypeIdentity source, JavaTypeIdentity target, JavaTypeIdentity preBox, JavaTypeIdentity boxed) {
    if (!DecisionValueValidation.isPrimitive(source) || !DecisionValueValidation.isPrimitive(preBox)
        || !DecisionValueValidation.isPreBoxType(source, preBox) || !DecisionValueValidation.isWrapper(boxed, preBox)
        || !DecisionValueValidation.isReference(target)) {
      throw new IllegalArgumentException("Boxing requires consistent primitive, wrapper and selected reference target types");
    }
  }
}
