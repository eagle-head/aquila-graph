package org.aquilagraph.syntax;

import java.util.List;

/** Copied selected facts waiting for method-scoped admission. */
record MethodDecisionRegistration(List<TypedUseDecision> uses, List<InvocationArityDecision> invocations,
    List<StaticAccessDecision> staticAccesses, List<StringConcatenationDecision> concatenations,
    List<EnumConstructorContextDecision> enumContexts) {
  MethodDecisionRegistration {
    uses = List.copyOf(uses);
    invocations = List.copyOf(invocations);
    staticAccesses = List.copyOf(staticAccesses);
    concatenations = List.copyOf(concatenations);
    enumContexts = List.copyOf(enumContexts);
  }
}
