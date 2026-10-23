package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import java.util.List;

/**
 * Copied selected facts waiting for execution-scoped admission.
 *
 * @param uses
 *          copied child-use decisions
 * @param invocations
 *          copied invocation decisions
 * @param staticAccesses
 *          copied static-member decisions
 * @param concatenations
 *          copied string-expression decisions
 * @param constructors
 *          copied constructor decisions
 */
public record ExecutionDecisionRegistration(List<ChildUseEvidence> uses, List<InvocationEvidence> invocations,
    List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations, List<ConstructorEvidence> constructors) {
  /**
   * Copies the decision evidence registered for one execution scope.
   */
  public ExecutionDecisionRegistration {
    uses = List.copyOf(uses);
    invocations = List.copyOf(invocations);
    staticAccesses = List.copyOf(staticAccesses);
    concatenations = List.copyOf(concatenations);
    constructors = List.copyOf(constructors);
  }
}
