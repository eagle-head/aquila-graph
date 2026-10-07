package org.aquilagraph.source.admission;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.WrittenSourceAssociation;

/**
 * Copied neutral values submitted together to intrinsic source admission.
 *
 * @param snapshot
 *          captured source membership and content
 * @param sources
 *          complete copied source contributions
 * @param bodies
 *          copied method body associations and facts
 * @param memberOrders
 *          canonical direct declaration order
 * @param associations
 *          original written occurrence ownership
 * @param declarationTypes
 *          copied declaration type components
 */
public record CopiedAstValues(InputSnapshot snapshot, List<SyntaxSourceContribution> sources, List<Body> bodies,
    List<DeclarationMemberOrder> memberOrders, List<WrittenSourceAssociation> associations,
    List<DeclarationTypeEvidence> declarationTypes) {
  /** Defensively copies every submitted collection. */
  public CopiedAstValues {
    Objects.requireNonNull(snapshot, "snapshot");
    sources = List.copyOf(sources);
    bodies = List.copyOf(bodies);
    memberOrders = List.copyOf(memberOrders);
    associations = List.copyOf(associations);
    declarationTypes = List.copyOf(declarationTypes);
  }

  /**
   * Returns copied empty source values for an empty captured input.
   *
   * @param snapshot
   *          captured input
   * @return empty copied values, subject to admission of source membership
   */
  public static CopiedAstValues empty(InputSnapshot snapshot) {
    return new CopiedAstValues(snapshot, List.of(), List.of(), List.of(), List.of(), List.of());
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
    return new CopiedAstValues(aggregate.snapshot(), sources, bodies, aggregate.declarationMemberOrders(),
        aggregate.writtenSourceAssociations(), aggregate.declarationTypeEvidence());
  }

  /**
   * One copied body registration with independently retained fact families.
   *
   * @param methodKey
   *          canonical containing method
   * @param root
   *          copied body root
   * @param origin
   *          body relationship to written source
   * @param uses
   *          copied direct child use facts
   * @param invocations
   *          copied selected applicability
   * @param staticAccesses
   *          copied selected static members
   * @param concatenations
   *          copied String expression facts
   * @param constructors
   *          copied incoming enum context
   * @param attributedOccurrences
   *          actual copied compiler addresses of retained body occurrences
   */
  public record Body(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses,
      List<InvocationEvidence> invocations, List<StaticMemberEvidence> staticAccesses, List<StringExpressionEvidence> concatenations,
      List<ConstructorEvidence> constructors, List<AttributedBodyOccurrence> attributedOccurrences) {
    /** Validates required values and defensively copies each fact family. */
    public Body {
      Objects.requireNonNull(methodKey, "methodKey");
      Objects.requireNonNull(root, "root");
      Objects.requireNonNull(origin, "origin");
      uses = List.copyOf(uses);
      invocations = List.copyOf(invocations);
      staticAccesses = List.copyOf(staticAccesses);
      concatenations = List.copyOf(concatenations);
      constructors = List.copyOf(constructors);
      attributedOccurrences = List.copyOf(attributedOccurrences);
    }
  }
}
