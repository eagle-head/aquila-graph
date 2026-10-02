package org.aquilagraph.syntax;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.DeclarationMemberOrder;
import org.aquilagraph.model.DeclarationTypeEvidence;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceSpan;

/** Immutable, compiler-neutral source AST for one captured input snapshot. */
public final class JavaAstAggregate {
  private final InputSnapshot capturedInput;
  private final List<SyntaxNode> sourceRoots;
  private final List<SyntaxNode> orderedOccurrences;
  private final Map<String, SyntaxNode> occurrencesByKey;
  private final List<SyntaxNodeTuple> orderedIdentities;
  private final List<SyntaxRangeTuple> orderedRanges;
  private final List<SyntaxStructuralEdge> orderedStructuralRelations;
  private final List<SyntaxSemanticEdge> orderedSemanticRelations;
  private final List<NodeFacts> orderedFacts;
  private final Map<String, JavaAstAggregateBuilder.BodyRegistration> methodBodies;
  private final Map<String, DeclarationMemberOrder> memberOrdersByType;
  private final Map<String, MethodDecisionIndex> methodDecisions;
  private final Map<String, WrittenSourceAssociation> sourceAssociationsByNode;
  private final Map<String, DeclarationTypeEvidence> declarationTypes;

  private JavaAstAggregate(InputSnapshot inputSnapshot, List<SyntaxNode> sourceRoots, List<SyntaxNode> orderedNodes,
      Map<String, SyntaxNode> ownedNodes, List<SyntaxStructuralEdge> orderedStructuralEdges, List<SyntaxSemanticEdge> orderedSemanticEdges,
      Map<String, JavaAstAggregateBuilder.BodyRegistration> bodies, Map<String, DeclarationMemberOrder> memberOrders,
      Map<String, MethodDecisionIndex> decisions, Map<String, WrittenSourceAssociation> associations,
      Map<String, DeclarationTypeEvidence> types) {
    capturedInput = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
    this.sourceRoots = List.copyOf(sourceRoots);
    orderedOccurrences = List.copyOf(orderedNodes);
    occurrencesByKey = Map.copyOf(ownedNodes);
    orderedStructuralRelations = List.copyOf(orderedStructuralEdges);
    orderedSemanticRelations = List.copyOf(orderedSemanticEdges);
    orderedIdentities = List.copyOf(orderedOccurrences.stream().map(SyntaxNode::nodeTuple).toList());
    orderedRanges = List.copyOf(orderedOccurrences.stream().map(node -> node.rangeTuple().orElseThrow()).toList());
    orderedFacts = orderedOccurrences.stream().map(NodeFacts::from).toList();
    methodBodies = Map.copyOf(bodies);
    memberOrdersByType = Map.copyOf(memberOrders);
    methodDecisions = Map.copyOf(decisions);
    sourceAssociationsByNode = Map.copyOf(associations);
    declarationTypes = Map.copyOf(types);
  }

  /**
   * Starts one controlled construction for the captured source set.
   *
   * @param snapshot
   *          immutable captured input
   * @return mutable construction boundary
   */
  public static Builder builder(InputSnapshot snapshot) {
    return new Builder(snapshot);
  }

  /** The only public construction boundary for one captured source set. */
  public static final class Builder {
    private final JavaAstAggregateBuilder validator;

    private Builder(InputSnapshot snapshot) {
      validator = new JavaAstAggregateBuilder(snapshot);
    }

    /**
     * Adds one captured source contribution. Arrival order has no effect on the sealed value.
     *
     * @param contribution
     *          source occurrences and typed relations
     * @return this builder
     */
    public Builder add(SyntaxSourceContribution contribution) {
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
    public Builder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin) {
      validator.addMethodBody(methodKey, root, origin);
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
    public Builder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<TypedUseDecision> uses) {
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
    public Builder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<TypedUseDecision> uses,
        List<InvocationArityDecision> invocations) {
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
    public Builder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<TypedUseDecision> uses,
        List<InvocationArityDecision> invocations, List<StaticAccessDecision> staticAccesses) {
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
    public Builder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<TypedUseDecision> uses,
        List<InvocationArityDecision> invocations, List<StaticAccessDecision> staticAccesses,
        List<StringConcatenationDecision> concatenations) {
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
     * @param enumContexts
     *          incoming enum name and ordinal bindings, separate from source arguments
     * @return this builder
     */
    public Builder addMethodBody(String methodKey, SyntaxNode root, MethodBodyOrigin origin, List<TypedUseDecision> uses,
        List<InvocationArityDecision> invocations, List<StaticAccessDecision> staticAccesses,
        List<StringConcatenationDecision> concatenations, List<EnumConstructorContextDecision> enumContexts) {
      validator.addMethodBody(methodKey, root, origin, uses, invocations, staticAccesses, concatenations, enumContexts);
      return this;
    }

    /**
     * Registers copied canonical direct-member order for one actual translated type.
     *
     * @param order
     *          actual translated order
     * @return this builder
     */
    public Builder addDeclarationMemberOrder(DeclarationMemberOrder order) {
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
    public Builder addWrittenSourceAssociation(WrittenSourceAssociation association) {
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
    public Builder addDeclarationTypeEvidence(DeclarationTypeEvidence evidence) {
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
      var validated = validator.validate();
      return new JavaAstAggregate(validated.snapshot(), validated.roots(), validated.nodes(), validated.ownedNodes(),
          validated.structuralEdges(), validated.semanticEdges(), validated.methodBodies(), validated.declarationMemberOrders(),
          validated.methodDecisions(), validated.writtenSourceAssociations(), validated.declarationTypes());
    }
  }

  /**
   * Returns the captured input that defines source membership and root order.
   *
   * @return captured input snapshot
   */
  public InputSnapshot snapshot() {
    return capturedInput;
  }

  /**
   * Returns source roots in captured snapshot order.
   *
   * @return immutable ordered source roots
   */
  public List<SyntaxNode> roots() {
    return sourceRoots;
  }

  /**
   * Returns occurrences in source-root order and canonical structural preorder.
   *
   * @return immutable ordered occurrences
   */
  public List<SyntaxNode> nodes() {
    return orderedOccurrences;
  }

  /**
   * Returns exact occurrence keys in the same order as {@link #nodes()}.
   *
   * @return immutable ordered keys
   */
  public List<String> orderedNodeKeys() {
    return orderedIdentities.stream().map(SyntaxNodeTuple::nodeKey).toList();
  }

  /**
   * Returns canonical occurrence identity tuples.
   *
   * @return immutable ordered identity tuples
   */
  public List<SyntaxNodeTuple> nodeTuples() {
    return orderedIdentities;
  }

  /**
   * Returns one exact source range per occurrence in canonical node order.
   *
   * @return immutable ordered range tuples
   */
  public List<SyntaxRangeTuple> rangeTuples() {
    return orderedRanges;
  }

  /**
   * Returns structural edges in canonical parent-preorder, role, and position order.
   *
   * @return immutable ordered structural tuples
   */
  public List<SyntaxStructuralEdge> structuralEdges() {
    return orderedStructuralRelations;
  }

  /**
   * Returns semantic edges in total source, relation, and target order.
   *
   * @return immutable ordered semantic tuples
   */
  public List<SyntaxSemanticEdge> semanticEdges() {
    return orderedSemanticRelations;
  }

  /**
   * Returns the identical owned occurrence, or empty when the key is absent.
   *
   * @param key
   *          occurrence key
   * @return the aggregate-owned occurrence when present
   */
  public Optional<SyntaxNode> node(String key) {
    return Optional.ofNullable(occurrencesByKey.get(Objects.requireNonNull(key, "key")));
  }

  /**
   * Returns a view over a body owned by this aggregate.
   *
   * @param methodKey
   *          canonical method identity
   * @return the core-facing body view, when registered
   */
  public Optional<MethodSyntax> methodSyntax(String methodKey) {
    Objects.requireNonNull(methodKey, "methodKey");
    return methodBodies.containsKey(methodKey) ? Optional.of(new MethodSyntax(this, methodKey)) : Optional.empty();
  }

  SyntaxNode methodBodyRoot(String methodKey) {
    return Objects.requireNonNull(methodBodies.get(methodKey), "method body").root();
  }

  MethodDecisionIndex methodDecisionIndex(String methodKey) {
    return Objects.requireNonNull(methodDecisions.get(methodKey), "method decisions");
  }

  /**
   * Returns how a retained executable body relates to written source.
   *
   * @param methodKey
   *          canonical method identity
   * @return source occurrence or auxiliary execution classification
   */
  public Optional<MethodBodyOrigin> methodBodyOrigin(String methodKey) {
    Objects.requireNonNull(methodKey, "methodKey");
    return Optional.ofNullable(methodBodies.get(methodKey)).map(JavaAstAggregateBuilder.BodyRegistration::origin);
  }

  /**
   * Returns the exact method identities for retained executable bodies.
   *
   * @return immutable method identity set
   */
  public Set<String> methodBodyKeys() {
    return methodBodies.keySet();
  }

  /**
   * Returns a copied compiler member order owned by the aggregate.
   *
   * @param ownerTypeKey
   *          canonical type identity
   * @return actual translated order when retained
   */
  public Optional<DeclarationMemberOrder> declarationMemberOrder(String ownerTypeKey) {
    return Optional.ofNullable(memberOrdersByType.get(Objects.requireNonNull(ownerTypeKey, "ownerTypeKey")));
  }

  /**
   * Returns actual translated member orders sorted by source and canonical owner.
   *
   * @return immutable ordered values
   */
  public List<DeclarationMemberOrder> declarationMemberOrders() {
    return memberOrdersByType.values().stream()
        .sorted(Comparator.comparing(DeclarationMemberOrder::sourceFileKey).thenComparing(DeclarationMemberOrder::ownerTypeKey)).toList();
  }

  /**
   * Returns original parsed ownership of an actual source occurrence.
   *
   * @param nodeKey
   *          exact source node identity
   * @return the retained association, when supplied
   */
  public Optional<WrittenSourceAssociation> writtenSourceAssociation(String nodeKey) {
    return Optional.ofNullable(sourceAssociationsByNode.get(Objects.requireNonNull(nodeKey, "nodeKey")));
  }

  /**
   * Returns original parsed associations in actual structural node order.
   *
   * @return immutable ordered associations
   */
  public List<WrittenSourceAssociation> writtenSourceAssociations() {
    return orderedOccurrences.stream().map(node -> sourceAssociationsByNode.get(node.nodeId())).filter(Objects::nonNull).toList();
  }

  /**
   * Finds copied public type components for an actual canonical declaration.
   *
   * @param ownerTypeKey
   *          canonical type identity
   * @return actual components when supplied
   */
  public Optional<DeclarationTypeEvidence> declarationTypeEvidence(String ownerTypeKey) {
    return Optional.ofNullable(declarationTypes.get(Objects.requireNonNull(ownerTypeKey, "ownerTypeKey")));
  }

  /**
   * Returns copied public declaration types in source and owner order.
   *
   * @return immutable ordered declaration type values
   */
  public List<DeclarationTypeEvidence> declarationTypeEvidence() {
    return declarationTypes.values().stream()
        .sorted(Comparator.comparing(DeclarationTypeEvidence::sourceFileKey).thenComparing(DeclarationTypeEvidence::ownerTypeKey)).toList();
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof JavaAstAggregate that && capturedInput.equals(that.capturedInput)
        && orderedFacts.equals(that.orderedFacts) && orderedStructuralRelations.equals(that.orderedStructuralRelations)
        && orderedSemanticRelations.equals(that.orderedSemanticRelations) && methodBodies.equals(that.methodBodies)
        && memberOrdersByType.equals(that.memberOrdersByType) && sourceAssociationsByNode.equals(that.sourceAssociationsByNode)
        && declarationTypes.equals(that.declarationTypes));
  }

  @Override
  public int hashCode() {
    return Objects.hash(capturedInput, orderedFacts, orderedStructuralRelations, orderedSemanticRelations, methodBodies, memberOrdersByType,
        sourceAssociationsByNode, declarationTypes);
  }

  private record NodeFacts(SyntaxNodeTuple identity, SourceSpan range, SyntaxKind syntaxKind, SyntaxType type, SyntaxValue value,
      Optional<SemanticReference> reference) {
    private static NodeFacts from(SyntaxNode node) {
      return new NodeFacts(node.nodeTuple(), node.origin().orElseThrow(), node.syntaxKind(), node.type(), node.value(),
          node.semanticReference());
    }
  }
}
