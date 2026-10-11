package org.aquilagraph.source.api;

import java.util.List;
import org.aquilagraph.identity.api.InputSnapshot;

/** Test-only access to package-private intrinsic construction. */
public final class AggregateFixture {
  private final JavaAstAggregateBuilder validator;

  public static AggregateFixture builder(InputSnapshot snapshot) {
    return new AggregateFixture(snapshot);
  }

  private AggregateFixture(InputSnapshot snapshot) {
    validator = new JavaAstAggregateBuilder(snapshot);
  }

  /**
   * Adds one captured source contribution. Arrival order has no effect on the sealed value.
   *
   * @param contribution
   *          source occurrences and typed relations
   * @return this builder
   */
  public AggregateFixture add(SyntaxSourceContribution contribution) {
    validator.add(contribution);
    return this;
  }

  /**
   * Registers the core-facing body retained for one method. Source bodies must be identical to owned AST occurrences; compiler-added or
   * position-unavailable bodies remain outside the source occurrence graph.
   *
   * @param methodKey
   *          canonical method identity
   * @param root
   *          translated body root
   * @param origin
   *          relationship to captured source occurrences
   * @return this builder
   */
  public AggregateFixture addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin) {
    validator.addMethodBody(methodKey, root, origin);
    return this;
  }

  /**
   * Registers independent copied child-use slots for a fixture body.
   *
   * @param methodKey containing method
   * @param root actual fixture body
   * @param origin relationship to captured source
   * @param uses independent source facts
   * @return this fixture builder
   */
  public AggregateFixture addChildUseBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ChildUseEvidence> uses) {
    validator.addMethodBody(methodKey, root, origin, uses, List.of(), List.of(), List.of(), List.of());
    return this;
  }

  /**
   * Registers a body with copied selected child-use facts.
   *
   * @param methodKey
   *          canonical method identity
   * @param root
   *          translated body root
   * @param origin
   *          relationship to captured source
   * @param uses
   *          selected child-use facts, including explicit no-conversion uses
   * @return this builder
   */
  public AggregateFixture addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ConversionEvidence> uses) {
    return addMethodBody(methodKey, root, origin, uses, List.of(), List.of(), List.of());
  }

  /**
   * Registers a body with copied selected uses and invocation applicability.
   *
   * @param methodKey
   *          canonical method identity
   * @param root
   *          translated body root
   * @param origin
   *          relationship to captured source
   * @param uses
   *          selected child-use facts
   * @param invocations
   *          selected invocation applicability
   * @return this builder
   */
  public AggregateFixture addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ConversionEvidence> uses,
      List<InvocationApplicability> invocations) {
    return addMethodBody(methodKey, root, origin, uses, invocations, List.of(), List.of());
  }

  /**
   * Registers a body with copied selected uses, invocation applicability, and static accesses.
   *
   * @param methodKey
   *          canonical method identity
   * @param root
   *          translated body root
   * @param origin
   *          relationship to captured source
   * @param uses
   *          selected child-use facts
   * @param invocations
   *          selected invocation applicability
   * @param staticAccesses
   *          selected static member facts
   * @return this builder
   */
  public AggregateFixture addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ConversionEvidence> uses,
      List<InvocationApplicability> invocations, List<StaticMemberEvidence> staticAccesses) {
    return addMethodBody(methodKey, root, origin, uses, invocations, staticAccesses, List.of());
  }

  /**
   * Registers a body with copied selected facts for all lowering decision families.
   *
   * @param methodKey
   *          canonical method identity
   * @param root
   *          translated body root
   * @param origin
   *          relationship to captured source
   * @param uses
   *          selected child-use facts
   * @param invocations
   *          selected invocation applicability
   * @param staticAccesses
   *          selected static member facts
   * @param concatenations
   *          selected String concatenation facts
   * @return this builder
   */
  public AggregateFixture addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ConversionEvidence> uses,
      List<InvocationApplicability> invocations, List<StaticMemberEvidence> staticAccesses,
      List<StringExpressionEvidence> concatenations) {
    return addMethodBody(methodKey, root, origin, uses, invocations, staticAccesses, concatenations, List.of());
  }

  /**
   * Registers selected source uses and separately copied incoming enum construction context.
   *
   * @param methodKey
   *          canonical method identity
   * @param root
   *          translated body root
   * @param origin
   *          relationship to captured source
   * @param uses
   *          selected source child-use facts
   * @param invocations
   *          selected invocation applicability
   * @param staticAccesses
   *          selected static member facts
   * @param concatenations
   *          selected String concatenation facts
   * @param constructors
   *          incoming enum name and ordinal bindings, separate from source arguments
   * @return this builder
   */
  public AggregateFixture addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<ConversionEvidence> uses,
      List<InvocationApplicability> invocations, List<StaticMemberEvidence> staticAccesses,
      List<StringExpressionEvidence> concatenations, List<ConstructorEvidence> constructors) {
    var nodes = root.descendantsAndSelf();
    var retainedInvocations = invocations.stream()
        .map(value -> InvocationEvidence.known(value, nodes.stream().filter(node -> node.nodeId().equals(value.consumerNodeId()))
            .findFirst().flatMap(SyntaxNode::origin)))
        .toList();
    var retainedUses = uses.stream().map(value -> {
      var consumer = nodes.stream().filter(node -> node.nodeId().equals(value.consumerNodeId())).findFirst();
      return ChildUseEvidence.known(value, consumer.map(SyntaxNode::syntaxKind).orElse(SyntaxKind.UNSUPPORTED), consumer.flatMap(SyntaxNode::origin));
    }).toList();
    validator.addMethodBody(methodKey, root, origin, retainedUses, retainedInvocations, staticAccesses, concatenations, constructors);
    return this;
  }

  /**
   * Registers complete copied fact families, including unavailable classifications.
   *
   * @param methodKey owning declaration
   * @param root actual copied body
   * @param origin body provenance
   * @param evidence complete copied facts
   * @return this fixture builder
   */
  public AggregateFixture addMethodEvidenceBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, ExecutionEvidence evidence) {
    validator.addMethodBody(methodKey, root, origin, evidence.uses(), evidence.invocations(), evidence.staticMembers(), evidence.stringExpressions(), evidence.constructors());
    return this;
  }

  /**
   * Registers copied canonical direct-member order for one actual translated type.
   *
   * @param order
   *          actual translated order
   * @return this builder
   */
  public AggregateFixture addDeclarationMemberOrder(DeclarationMemberOrder order) {
    validator.addDeclarationMemberOrder(order);
    return this;
  }

  /**
   * Adds original parsed ownership for an actual retained source node.
   *
   * @param association
   *          original parsed source ownership
   * @return this builder
   */
  public AggregateFixture addWrittenSourceAssociation(WrittenSourceAssociation association) {
    validator.addWrittenSourceAssociation(association);
    return this;
  }

  /**
   * Adds copied public type components from actual declaration translation.
   *
   * @param evidence
   *          actual declaration type components
   * @return this builder
   */
  public AggregateFixture addDeclarationTypeEvidence(DeclarationTypeEvidence evidence) {
    validator.addDeclarationTypeEvidence(evidence);
    return this;
  }

  /**
   * Validates all source, graph, identity, and ordering invariants before publication.
   *
   * @return immutable aggregate with canonical queries
   * @throws IllegalArgumentException
   *           if any contribution violates an aggregate invariant
   */
  public JavaAstAggregate seal() {
    return validator.seal();
  }
}
