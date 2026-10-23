package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.MethodSyntax;

/** Immutable method view over the canonical admitted source state. */
public final class AdmittedMethodSyntax extends ExecutionView implements MethodSyntax {
  AdmittedMethodSyntax(AdmittedJavaAstAggregate owner, String key) {
    super(owner, key);
  }

  @Override
  public String methodKey() {
    return scopeKey();
  }
}
