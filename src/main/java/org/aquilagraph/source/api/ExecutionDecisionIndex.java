package org.aquilagraph.source.api;

import java.util.Map;
import java.util.Optional;

/** Immutable indexes scoped to one admitted body registration. */
record ExecutionDecisionIndex(Map<UseKey, ChildUseEvidence> uses, Map<String, InvocationEvidence> invocations,
    Map<String, StaticMemberEvidence> staticAccesses, Map<String, StringExpressionEvidence> concatenations,
    Map<String, ConstructorEvidence> constructors) {
  ExecutionDecisionIndex {
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
