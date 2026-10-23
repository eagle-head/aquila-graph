package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.ExecutionEvidence;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.List;

/** Immutable source facts for one execution scope owned by a sealed Java AST aggregate. */
sealed class ExecutionView permits AdmittedExecutionSyntax, AdmittedMethodSyntax {
  private final AdmittedJavaAstAggregate aggregate;
  private final String key;

  ExecutionView(AdmittedJavaAstAggregate owner, String key) {
    // The aggregate creates this view only for an intrinsically admitted scope.
    aggregate = owner;
    this.key = key;
  }

  /**
   * Returns the canonical execution scope identity.
   *
   * @return canonical execution scope key
   */
  public String scopeKey() {
    return key;
  }

  /**
   * Returns the actual source owner classification.
   *
   * @return method, field or initializer ownership
   */
  public ExecutionScope scope() {
    return aggregate.executionScope(key);
  }

  /**
   * Returns the identical scope root retained by the aggregate.
   *
   * @return owned scope root
   */
  public SyntaxNode root() {
    return aggregate.executionRoot(key);
  }

  /**
   * Returns the source aggregate that owns this scope and its copied declarations.
   *
   * @return identical immutable source owner
   */
  public JavaAstAggregate source() {
    return aggregate;
  }

  /**
   * Returns actual copied attributed addresses without acquiring a compiler.
   *
   * @return immutable addresses for this exact owned scope
   */
  public List<AttributedBodyOccurrence> attributedOccurrences() {
    return aggregate.executionAttributions(key);
  }

  /**
   * Returns every retained fact in canonical body occurrence order.
   *
   * @return immutable fact families, including explicit missing or erroneous slots
   */
  public ExecutionEvidence evidence() {
    var order = new HashMap<String, Integer>();
    indexCanonicalOccurrences(root(), order);
    var facts = aggregate.executionDecisionIndex(key);
    return new ExecutionEvidence(
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
   *          consuming execution node
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
   *          exact consuming execution occurrence
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
    return aggregate.executionDecisionIndex(key).use(consumerNodeId, role, position);
  }

  /**
   * Returns selected invocation applicability for a execution invocation.
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
    return Optional.ofNullable(aggregate.executionDecisionIndex(key).invocations().get(consumerNodeId));
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
    return Optional.ofNullable(aggregate.executionDecisionIndex(key).constructors().get(invocationNodeId));
  }

  /**
   * Returns the selected member and declaring type of a static execution access.
   *
   * @param accessNodeId
   *          access or invocation node identity
   * @return selected static access, or empty for an absent, ineligible, or foreign node
   */
  public Optional<StaticMemberEvidence> staticAccess(String accessNodeId) {
    requireQueryId(accessNodeId);
    return Optional.ofNullable(aggregate.executionDecisionIndex(key).staticAccesses().get(accessNodeId));
  }

  /**
   * Returns selected String context for a execution PLUS expression.
   *
   * @param binaryNodeId
   *          binary node identity
   * @return selected String context, or empty for an absent, ineligible, or foreign node
   */
  public Optional<StringExpressionEvidence> stringConcatenation(String binaryNodeId) {
    requireQueryId(binaryNodeId);
    return Optional.ofNullable(aggregate.executionDecisionIndex(key).concatenations().get(binaryNodeId));
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof ExecutionView that && key.equals(that.key) && root().equals(that.root())
        && evidence().equals(that.evidence()) && attributedOccurrences().equals(that.attributedOccurrences()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, root(), evidence(), attributedOccurrences());
  }

  @Override
  public String toString() {
    return "ExecutionSyntax[scopeKey=" + key + ", root=" + root() + ']';
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
