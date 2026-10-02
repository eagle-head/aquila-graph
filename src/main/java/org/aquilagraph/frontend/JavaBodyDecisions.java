package org.aquilagraph.frontend;

import java.util.List;
import org.aquilagraph.syntax.EnumConstructorContextDecision;
import org.aquilagraph.syntax.InvocationArityDecision;
import org.aquilagraph.syntax.StaticAccessDecision;
import org.aquilagraph.syntax.StringConcatenationDecision;
import org.aquilagraph.syntax.TypedUseDecision;

/** Copied selected facts for exactly one independently correlated body. */
record JavaBodyDecisions(List<TypedUseDecision> uses, List<InvocationArityDecision> invocations, List<StaticAccessDecision> staticAccesses,
    List<StringConcatenationDecision> concatenations, List<EnumConstructorContextDecision> enumContexts) {
  JavaBodyDecisions(List<TypedUseDecision> uses, List<InvocationArityDecision> invocations, List<StaticAccessDecision> staticAccesses,
      List<StringConcatenationDecision> concatenations) {
    this(uses, invocations, staticAccesses, concatenations, List.of());
  }

  JavaBodyDecisions {
    uses = List.copyOf(uses);
    invocations = List.copyOf(invocations);
    staticAccesses = List.copyOf(staticAccesses);
    concatenations = List.copyOf(concatenations);
    enumContexts = List.copyOf(enumContexts);
  }
}
