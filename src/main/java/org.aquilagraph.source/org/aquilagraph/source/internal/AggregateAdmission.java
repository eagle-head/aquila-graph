package org.aquilagraph.source.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.WrittenSourceAssociation;

/** Runs source proofs in dependency order and publishes exactly one complete aggregate. */
final class AggregateAdmission {
  private AggregateAdmission() {
  }

  static JavaAstAggregate seal(InputSnapshot snapshot, Map<String, SourceFile> sources, Map<String, SyntaxSourceContribution> contributions,
      Map<String, ExecutionRegistration> registrations, Map<String, DeclarationMemberOrder> memberOrders,
      Map<String, WrittenSourceAssociation> associations, Map<String, DeclarationTypeEvidence> declarationTypes) {
    var topology = SourceTopology.resolve(snapshot, contributions);
    var declarations = SourceDeclarations.index(topology.orderedNodes());
    var execution = ExecutionOwnership.resolve(topology, declarations, sources, registrations, memberOrders);
    var writtenOccurrences = WrittenSourceValidation.validate(topology.nodes(), topology.parents(), sources, associations);
    declarations.validateEvidence(memberOrders, declarationTypes);
    EnclosingTypeAdmission.validate(topology.orderedNodes());
    var bindings = LexicalBindings.forAdmission(execution.allNodes(), execution.parents(), execution.owners(), declarations);
    bindings.validate();
    var semanticEdges = SourceSemanticValidation.validate(topology.semanticEdges(), topology.nodes(), topology.parents(),
        declarations.executables(), bindings);
    var declarationPaths = new AttributedDeclarationPaths(List.copyOf(memberOrders.values()), declarations, associations);
    ExecutionAttributionValidation.validate(execution.regions(), topology.nodes(), execution.parents(), declarations, writtenOccurrences,
        associations.values(), declarationPaths);

    var decisions = new HashMap<String, ExecutionDecisionIndex>();
    var facts = new HashMap<String, SourceExecutionFacts>();
    for (var region : execution.regions()) {
      var key = region.facts().scope().key();
      decisions.put(key, ExecutionDecisionValidation.validate(region, topology.nodes(), topology.parents(), declarations, memberOrders));
      facts.put(key, region.facts());
    }
    return new AdmittedJavaAstAggregate(snapshot, topology.roots(), topology.orderedNodes(), topology.nodes(), topology.structuralEdges(),
        semanticEdges, facts, memberOrders, decisions, associations, declarationTypes);
  }
}
