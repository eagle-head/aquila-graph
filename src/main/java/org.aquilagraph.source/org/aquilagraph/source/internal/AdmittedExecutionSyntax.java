package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.ExecutionSyntax;

/** Immutable scope view over the canonical admitted source state. */
public final class AdmittedExecutionSyntax extends ExecutionView implements ExecutionSyntax {
  AdmittedExecutionSyntax(AdmittedJavaAstAggregate owner, String key) {
    super(owner, key);
  }
}
