package org.aquilagraph.model;

import java.util.Objects;

/**
 * Durable compile-time semantics of one method or constructor invocation.
 *
 * @param mode
 *          invocation mode selected by the Java language rules
 * @param qualifyingType
 *          qualifying class or interface identity
 * @param form
 *          source invocation and receiver form
 */
public record InvocationSemantics(InvocationMode mode, JavaTypeReference qualifyingType, InvocationForm form) {
  /** Validates the relationship between invocation mode and source form. */
  public InvocationSemantics {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(qualifyingType, "qualifyingType");
    Objects.requireNonNull(form, "form");
    if (!mode.permits(form)) {
      throw new IllegalArgumentException("Invocation mode and source form are inconsistent");
    }
  }
}
