package org.aquilagraph.source.internal;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CopiedAstValues.Body;
import org.aquilagraph.source.admission.CopiedAstValues.Scope;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxSourceContribution;

/** Copies admitted source ownership into complete retention values. */
public final class SourceValueCapture {
  private SourceValueCapture() {
  }

  /**
   * Copies an admitted result into the transient projection used for retention and readmission.
   *
   * @param result
   *          admitted immutable result
   * @return complete copied projection
   */
  public static SyntaxAnalysisInput capture(AstResult result) {
    Objects.requireNonNull(result, "result");
    return new SyntaxAnalysisInput(result.snapshot(), result.environment(), result.program(), Optional.of(result.aggregate()),
        result.diagnostics(), result.coverage());
  }

  /**
   * Captures every retained aggregate value through immutable read queries.
   *
   * @param aggregate
   *          admitted source owner
   * @return complete copied source values, preserving occurrence sharing
   */
  public static CopiedAstValues capture(JavaAstAggregate aggregate) {
    Objects.requireNonNull(aggregate, "aggregate");
    var sources = aggregate.roots().stream()
        .map(root -> new SyntaxSourceContribution(root.sourceFileKey(), root,
            aggregate.nodes().stream().filter(node -> node.sourceFileKey().equals(root.sourceFileKey())).toList(),
            aggregate.structuralEdges().stream()
                .filter(edge -> aggregate.node(edge.parentKey()).orElseThrow().sourceFileKey().equals(root.sourceFileKey())).toList(),
            aggregate.semanticEdges().stream()
                .filter(edge -> aggregate.node(edge.sourceKey()).orElseThrow().sourceFileKey().equals(root.sourceFileKey())).toList()))
        .toList();
    var bodies = aggregate.methodBodyKeys().stream().sorted().map(methodKey -> {
      var syntax = aggregate.methodSyntax(methodKey).orElseThrow();
      var facts = syntax.evidence();
      return new Body(methodKey, syntax.root(), aggregate.methodBodyOrigin(methodKey).orElseThrow(), facts.uses(), facts.invocations(),
          facts.staticMembers(), facts.stringExpressions(), facts.constructors(), aggregate.bodyAttributions(methodKey));
    }).toList();
    var scopes = aggregate.executionScopes().stream().filter(scope -> !(scope instanceof ExecutionScope.Method)).map(scope -> {
      var syntax = aggregate.executionSyntax(scope.key()).orElseThrow();
      var facts = syntax.evidence();
      return new Scope(scope, syntax.root(), facts.uses(), facts.invocations(), facts.staticMembers(), facts.stringExpressions(),
          syntax.attributedOccurrences());
    }).toList();
    return new CopiedAstValues(aggregate.snapshot(), sources, bodies, aggregate.declarationMemberOrders(),
        aggregate.writtenSourceAssociations(), aggregate.declarationTypeEvidence(), scopes);
  }
}
