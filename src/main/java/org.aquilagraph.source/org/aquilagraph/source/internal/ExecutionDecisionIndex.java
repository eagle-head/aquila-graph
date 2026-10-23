package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxRole;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable indexes scoped to one admitted body registration.
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
public record ExecutionDecisionIndex(Map<UseKey, ChildUseEvidence> uses, Map<String, InvocationEvidence> invocations,
    Map<String, StaticMemberEvidence> staticAccesses, Map<String, StringExpressionEvidence> concatenations,
    Map<String, ConstructorEvidence> constructors) {
  /**
   * Copies each decision index into immutable storage.
   */
  public ExecutionDecisionIndex {
    uses = Map.copyOf(uses);
    invocations = Map.copyOf(invocations);
    staticAccesses = Map.copyOf(staticAccesses);
    concatenations = Map.copyOf(concatenations);
    constructors = Map.copyOf(constructors);
  }

  Optional<ChildUseEvidence> use(String consumerNodeId, SyntaxRole role, int position) {
    return Optional.ofNullable(uses.get(UseKey.from(consumerNodeId, role, position)));
  }

  record UseKey(String consumerNodeId, SyntaxRole role, int position) {
    static UseKey from(String consumerNodeId, SyntaxRole role, int position) {
      return new UseKey(consumerNodeId, role, position);
    }
  }
}
