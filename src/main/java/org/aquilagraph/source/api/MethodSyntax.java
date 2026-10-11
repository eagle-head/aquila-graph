package org.aquilagraph.source.api;

/** Immutable execution view restricted to an actual method or constructor. */
public final class MethodSyntax extends ExecutionSyntax {
  MethodSyntax(JavaAstAggregate owner, String key) {
    super(owner, key);
  }

  /**
   * Returns the canonical method identity.
   *
   * @return canonical executable declaration key
   */
  public String methodKey() {
    return scopeKey();
  }
}
