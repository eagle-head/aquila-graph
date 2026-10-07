package org.aquilagraph.source.api;

import java.util.List;

/** Copied selected facts waiting for method-scoped admission. */
record MethodDecisionRegistration(List<ChildUseEvidence> uses, List<InvocationEvidence> invocations,
    List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations, List<ConstructorEvidence> constructors) {
  MethodDecisionRegistration {
    uses = List.copyOf(uses);
    invocations = List.copyOf(invocations);
    staticAccesses = List.copyOf(staticAccesses);
    concatenations = List.copyOf(concatenations);
    constructors = List.copyOf(constructors);
  }
}
