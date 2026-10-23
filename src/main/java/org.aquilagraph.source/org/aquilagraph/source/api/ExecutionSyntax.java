package org.aquilagraph.source.api;

import java.util.Optional;
import java.util.List;

import org.aquilagraph.source.internal.AdmittedExecutionSyntax;
/** Immutable source facts for one execution scope owned by a sealed Java AST aggregate. */
public sealed interface ExecutionSyntax permits MethodSyntax, AdmittedExecutionSyntax {

  /**
   * Returns the canonical execution scope identity.
   *
   * @return canonical execution scope key
   */
  String scopeKey();

  /**
   * Returns the actual source owner classification.
   *
   * @return method, field or initializer ownership
   */
  ExecutionScope scope();

  /**
   * Returns the identical scope root retained by the aggregate.
   *
   * @return owned scope root
   */
  SyntaxNode root();

  /**
   * Returns the source aggregate that owns this scope and its copied declarations.
   *
   * @return identical immutable source owner
   */
  JavaAstAggregate source();

  /**
   * Returns actual copied attributed addresses without acquiring a compiler.
   *
   * @return immutable addresses for this exact owned scope
   */
  List<AttributedBodyOccurrence> attributedOccurrences();

  /**
   * Returns every retained fact in canonical body occurrence order.
   *
   * @return immutable fact families, including explicit missing or erroneous slots
   */
  ExecutionEvidence evidence();

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
  Optional<ConversionEvidence> useDecision(String consumerNodeId, SyntaxRole role, int position);

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
  Optional<ChildUseEvidence> useEvidence(String consumerNodeId, SyntaxRole role, int position);

  /**
   * Returns selected invocation applicability for a execution invocation.
   *
   * @param consumerNodeId
   *          invocation node identity
   * @return selected applicability, or empty for an absent, ineligible, or foreign node
   */
  Optional<InvocationApplicability> invocationArity(String consumerNodeId);

  /**
   * Returns the independently retained applicability slot for an actual invocation.
   *
   * @param consumerNodeId
   *          invocation node identity
   * @return copied slot, or empty for an absent, ineligible, or foreign node
   */
  Optional<InvocationEvidence> invocationEvidence(String consumerNodeId);

  /**
   * Returns selected constructor facts and independent compiler-insertion corroboration.
   *
   * @param invocationNodeId
   *          invocation node identity
   * @return copied context, or empty for an absent, ineligible, or foreign node
   */
  Optional<ConstructorEvidence> constructorEvidence(String invocationNodeId);

  /**
   * Returns the selected member and declaring type of a static execution access.
   *
   * @param accessNodeId
   *          access or invocation node identity
   * @return selected static access, or empty for an absent, ineligible, or foreign node
   */
  Optional<StaticMemberEvidence> staticAccess(String accessNodeId);

  /**
   * Returns selected String context for a execution PLUS expression.
   *
   * @param binaryNodeId
   *          binary node identity
   * @return selected String context, or empty for an absent, ineligible, or foreign node
   */
  Optional<StringExpressionEvidence> stringConcatenation(String binaryNodeId);

}
