package org.aquilagraph.source.api;

import java.util.List;
import java.util.ArrayList;

/**
 * Complete copied fact indexes for one source body in canonical occurrence order.
 *
 * @param uses
 *          direct child-use facts in occurrence, role, and position order
 * @param invocations
 *          selected invocation facts in occurrence order
 * @param staticMembers
 *          selected static-member facts in occurrence order
 * @param stringExpressions
 *          String expression facts in occurrence order
 * @param constructors
 *          copied constructor evidence in occurrence order
 */
public record ExecutionEvidence(List<ChildUseEvidence> uses, List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticMembers,
    List<StringExpressionEvidence> stringExpressions, List<ConstructorEvidence> constructors) {
  /** Defensively copies every fact family without dropping negative evidence. */
  public ExecutionEvidence {
    uses = List.copyOf(uses);
    invocations = List.copyOf(invocations);
    staticMembers = List.copyOf(staticMembers);
    stringExpressions = List.copyOf(stringExpressions);
    constructors = List.copyOf(constructors);
  }

  /**
   * Returns the original source errors associated with independently retained slots.
   *
   * @return immutable diagnostic occurrences in fact-family and slot order
   */
  public List<AnalysisDiagnostic> diagnostics() {
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    uses.forEach(value -> {
      value.conversion().diagnostic().ifPresent(diagnostics::add);
      value.referenceCheck().diagnostic().ifPresent(diagnostics::add);
    });
    invocations.forEach(value -> value.applicability().diagnostic().ifPresent(diagnostics::add));
    staticMembers.forEach(value -> {
      value.declaringOwner().diagnostic().ifPresent(diagnostics::add);
      value.constantVariable().diagnostic().ifPresent(diagnostics::add);
    });
    stringExpressions.forEach(value -> {
      value.leftIsString().diagnostic().ifPresent(diagnostics::add);
      value.rightIsString().diagnostic().ifPresent(diagnostics::add);
      value.compileTimeConstant().diagnostic().ifPresent(diagnostics::add);
    });
    constructors.forEach(value -> {
      value.corroboration().diagnostic().ifPresent(diagnostics::add);
      value.corroboration().knownValue().flatMap(context -> context.insertedInvocation())
          .flatMap(invocation -> invocation.applicability().applicability().diagnostic()).ifPresent(diagnostics::add);
    });
    return List.copyOf(diagnostics);
  }
}
