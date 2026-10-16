package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.WrittenSourceAssociation;

/** Complete detached unit payload, ready for ordered program accumulation. */
record FinalizedUnit(Optional<SyntaxSourceContribution> contribution, List<CopiedAstValues.Body> bodies, DiagnosticEmissions emissions,
    List<WrittenSourceAssociation> sourceAssociations, List<CopiedAstValues.Scope> scopes, List<DeclarationTypeEvidence> declarationTypes,
    List<DeclarationMemberOrder> memberOrders) {
  FinalizedUnit {
    Objects.requireNonNull(contribution, "contribution");
    bodies = List.copyOf(bodies);
    Objects.requireNonNull(emissions, "emissions");
    sourceAssociations = List.copyOf(sourceAssociations);
    scopes = List.copyOf(scopes);
    declarationTypes = List.copyOf(declarationTypes);
    memberOrders = List.copyOf(memberOrders);
    if (contribution.isEmpty() && !(bodies.isEmpty() && sourceAssociations.isEmpty() && scopes.isEmpty() && declarationTypes.isEmpty()
        && memberOrders.isEmpty())) {
      throw new IllegalArgumentException("An absent unit contribution cannot retain source payload");
    }
  }

  List<AnalysisDiagnostic> diagnostics() {
    return emissions.diagnostics();
  }
}
