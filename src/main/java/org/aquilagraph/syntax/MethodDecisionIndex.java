package org.aquilagraph.syntax;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable indexes scoped to one admitted body registration. */
record MethodDecisionIndex(Map<UseKey, TypedUseDecision> uses, Map<String, InvocationArityDecision> invocations,
    Map<String, StaticAccessDecision> staticAccesses, Map<String, StringConcatenationDecision> concatenations,
    Map<String, EnumConstructorContextDecision> enumContexts, Set<String> requiredEnumContextCalls) {
  MethodDecisionIndex {
    uses = Map.copyOf(uses);
    invocations = Map.copyOf(invocations);
    staticAccesses = Map.copyOf(staticAccesses);
    concatenations = Map.copyOf(concatenations);
    enumContexts = Map.copyOf(enumContexts);
    requiredEnumContextCalls = Set.copyOf(requiredEnumContextCalls);
  }

  Optional<TypedUseDecision> use(String consumerNodeId, SyntaxRole role, int position) {
    return Optional.ofNullable(uses.get(UseKey.from(consumerNodeId, role, position)));
  }

  record UseKey(String consumerNodeId, SyntaxRole role, int position) {
    static UseKey from(String consumerNodeId, SyntaxRole role, int position) {
      return new UseKey(consumerNodeId, role, position);
    }
  }
}
