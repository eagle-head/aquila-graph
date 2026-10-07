package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.List;

/** Immutable source facts for one body owned by a sealed Java AST aggregate. */
public final class MethodSyntax {
  private final JavaAstAggregate aggregate;
  private final String key;

  MethodSyntax(JavaAstAggregate owner, String key) {
    aggregate = Objects.requireNonNull(owner, "owner");
    this.key = Objects.requireNonNull(key, "methodKey");
    if (this.key.isBlank()) {
      throw new IllegalArgumentException("Method syntax identity cannot be blank");
    }
  }

  /**
   * Returns the canonical method identity.
   *
   * @return canonical method key
   */
  public String methodKey() {
    return key;
  }

  /**
   * Returns the identical body root retained by the aggregate.
   *
   * @return owned body root
   */
  public SyntaxNode root() {
    return aggregate.methodBodyRoot(key);
  }

  /**
   * Returns the source aggregate that owns this body and its copied declarations.
   *
   * @return identical immutable source owner
   */
  public JavaAstAggregate source() {
    return aggregate;
  }

  /**
   * Returns actual copied attributed addresses without acquiring a compiler.
   *
   * @return immutable addresses for this exact owned body
   */
  public List<AttributedBodyOccurrence> attributedOccurrences() {
    return aggregate.bodyAttributions(key);
  }

  /**
   * Returns every retained fact in canonical body occurrence order.
   *
   * @return immutable fact families, including explicit missing or erroneous slots
   */
  public MethodEvidence evidence() {
    var order = new HashMap<String, Integer>();
    indexCanonicalOccurrences(root(), order);
    var facts = aggregate.methodDecisionIndex(key);
    return new MethodEvidence(
        facts.uses().values().stream()
            .sorted(Comparator.comparingInt((ChildUseEvidence use) -> positionOf(order, use.consumerNodeId()))
                .thenComparing(use -> use.role().stableId()).thenComparingInt(ChildUseEvidence::position))
            .toList(),
        facts.invocations().values().stream().sorted(Comparator.comparingInt(value -> positionOf(order, value.consumerNodeId()))).toList(),
        facts.staticAccesses().values().stream().sorted(Comparator.comparingInt(value -> positionOf(order, value.accessNodeId()))).toList(),
        facts.concatenations().values().stream().sorted(Comparator.comparingInt(value -> positionOf(order, value.binaryNodeId()))).toList(),
        facts.constructors().values().stream().sorted(Comparator.comparingInt(value -> positionOf(order, value.invocationNodeId())))
            .toList());
  }

  /**
   * Returns the selected use of an eligible direct child, including an explicit no-conversion decision.
   *
   * @param consumerNodeId
   *          consuming body node
   * @param role
   *          direct child role
   * @param position
   *          zero-based role-local child position
   * @return selected use, or empty for an absent, ineligible, or foreign key
   */
  public Optional<ConversionEvidence> useDecision(String consumerNodeId, SyntaxRole role, int position) {
    return useEvidence(consumerNodeId, role, position).flatMap(value -> value.conversion().knownValue());
  }

  /**
   * Returns required child types and the independently retained classification slots.
   *
   * @param consumerNodeId
   *          exact consuming body occurrence
   * @param role
   *          actual direct child role
   * @param position
   *          zero-based role-local position
   * @return retained use, or empty for an absent, ineligible, or foreign key
   */
  public Optional<ChildUseEvidence> useEvidence(String consumerNodeId, SyntaxRole role, int position) {
    requireQueryId(consumerNodeId);
    if (role == null || position < 0) {
      throw new IllegalArgumentException("A selected-use query requires a role and nonnegative position");
    }
    return aggregate.methodDecisionIndex(key).use(consumerNodeId, role, position);
  }

  /**
   * Returns selected invocation applicability for a body invocation.
   *
   * @param consumerNodeId
   *          invocation node identity
   * @return selected applicability, or empty for an absent, ineligible, or foreign node
   */
  public Optional<InvocationApplicability> invocationArity(String consumerNodeId) {
    return invocationEvidence(consumerNodeId).flatMap(value -> value.applicability().knownValue());
  }

  /**
   * Returns the independently retained applicability slot for an actual invocation.
   *
   * @param consumerNodeId
   *          invocation node identity
   * @return copied slot, or empty for an absent, ineligible, or foreign node
   */
  public Optional<InvocationEvidence> invocationEvidence(String consumerNodeId) {
    requireQueryId(consumerNodeId);
    return Optional.ofNullable(aggregate.methodDecisionIndex(key).invocations().get(consumerNodeId));
  }

  /**
   * Returns selected constructor facts and independent compiler-insertion corroboration.
   *
   * @param invocationNodeId
   *          invocation node identity
   * @return copied context, or empty for an absent, ineligible, or foreign node
   */
  public Optional<ConstructorEvidence> constructorEvidence(String invocationNodeId) {
    requireQueryId(invocationNodeId);
    return Optional.ofNullable(aggregate.methodDecisionIndex(key).constructors().get(invocationNodeId));
  }

  /**
   * Returns the selected member and declaring type of a static body access.
   *
   * @param accessNodeId
   *          access or invocation node identity
   * @return selected static access, or empty for an absent, ineligible, or foreign node
   */
  public Optional<StaticMemberEvidence> staticAccess(String accessNodeId) {
    requireQueryId(accessNodeId);
    return Optional.ofNullable(aggregate.methodDecisionIndex(key).staticAccesses().get(accessNodeId));
  }

  /**
   * Returns selected String context for a body PLUS expression.
   *
   * @param binaryNodeId
   *          binary node identity
   * @return selected String context, or empty for an absent, ineligible, or foreign node
   */
  public Optional<StringExpressionEvidence> stringConcatenation(String binaryNodeId) {
    requireQueryId(binaryNodeId);
    return Optional.ofNullable(aggregate.methodDecisionIndex(key).concatenations().get(binaryNodeId));
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof MethodSyntax that && key.equals(that.key) && root().equals(that.root())
        && evidence().equals(that.evidence()) && attributedOccurrences().equals(that.attributedOccurrences()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, root(), evidence(), attributedOccurrences());
  }

  @Override
  public String toString() {
    return "MethodSyntax[methodKey=" + key + ", root=" + root() + ']';
  }

  private static int positionOf(Map<String, Integer> order, String nodeKey) {
    return Objects.requireNonNull(order.get(nodeKey), "fact occurrence");
  }

  private static void indexCanonicalOccurrences(SyntaxNode node, Map<String, Integer> order) {
    order.put(node.nodeId(), order.size());
    node.children().stream()
        .sorted(Comparator.comparing((SyntaxChild child) -> child.role().stableId()).thenComparingInt(SyntaxChild::position))
        .forEach(child -> indexCanonicalOccurrences(child.node(), order));
  }

  private static void requireQueryId(String nodeId) {
    if (nodeId == null || nodeId.isBlank()) {
      throw new IllegalArgumentException("A selected-decision query requires a nonblank node identity");
    }
  }

}
