package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Durable compile-time semantics of one method or constructor invocation.
 *
 * @param mode
 *          invocation mode selected by the Java language rules
 * @param qualifyingType
 *          qualifying class, interface, or exact intrinsic array identity
 * @param form
 *          source invocation and receiver form
 * @param selection
 *          selected named executable or intrinsic operation
 */
public record InvocationSemantics(InvocationMode mode, JavaTypeReference qualifyingType, InvocationForm form,
    InvocationSelection selection) {
  /** Validates the relationship between invocation mode and source form. */
  public InvocationSemantics {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(qualifyingType, "qualifyingType");
    Objects.requireNonNull(form, "form");
    Objects.requireNonNull(selection, "selection");
    if (!mode.permits(form)) {
      throw new IllegalArgumentException("Invocation mode and source form are inconsistent");
    }
    if (selection == InvocationSelection.ARRAY_CLONE && (mode != InvocationMode.VIRTUAL || form != InvocationForm.INSTANCE_EXPRESSION
        || qualifyingType.identity().category() != JavaTypeCategory.ARRAY || qualifyingType.declarationKey().isPresent())) {
      throw new IllegalArgumentException("Array clone requires a virtual expression receiver and an array without a declaration key");
    }
  }
}
