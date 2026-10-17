package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaType;

/** Detached declaration metadata inspected by the program before source projection. */
record UnitDeclarations(List<JavaType> types, List<DeclarationMemberOrder> memberOrders, List<DeclarationTypeEvidence> declarationTypes,
    boolean complete, ProjectionEligibility eligibility) {
  UnitDeclarations {
    types = List.copyOf(types);
    memberOrders = List.copyOf(memberOrders);
    declarationTypes = List.copyOf(declarationTypes);
    Objects.requireNonNull(eligibility, "eligibility");
  }
}
