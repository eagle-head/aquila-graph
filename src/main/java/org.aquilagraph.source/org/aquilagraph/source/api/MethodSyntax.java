package org.aquilagraph.source.api;

import org.aquilagraph.source.internal.AdmittedMethodSyntax;

/** Immutable execution view restricted to an actual method or constructor. */
public sealed interface MethodSyntax extends ExecutionSyntax permits AdmittedMethodSyntax {
  /**
   * Returns the canonical method identity.
   *
   * @return canonical executable declaration key
   */
  String methodKey();
}
