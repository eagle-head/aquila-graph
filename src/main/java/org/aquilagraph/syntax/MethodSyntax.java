package org.aquilagraph.syntax;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.ResolutionStatus;

/** Core-facing view of one body owned by a sealed Java AST aggregate. */
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
   * Derives whether syntax is accepted and has every semantic prerequisite required by graph lowering.
   *
   * @return derived readiness for direct graph lowering
   */
  public SyntaxReadiness readiness() {
    var nodes = root().descendantsAndSelf();
    if (nodes.stream().anyMatch(node -> !node.syntaxKind().isMethodBodySyntax())) {
      return SyntaxReadiness.UNSUPPORTED;
    }
    if (nodes.stream().anyMatch(MethodSyntax::hasErroneousSemantics)) {
      return SyntaxReadiness.ERRONEOUS_SEMANTICS;
    }
    var decisions = aggregate.methodDecisionIndex(key);
    if (nodes.stream().anyMatch(node -> hasUnresolvedSemantics(node) || MethodDecisionValidation.hasMissingDecisions(node, decisions)
        || MethodDecisionValidation.hasMissingEnumContext(node, decisions))) {
      return SyntaxReadiness.UNRESOLVED_SEMANTICS;
    }
    return SyntaxReadiness.LOWERABLE;
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
  public Optional<TypedUseDecision> useDecision(String consumerNodeId, SyntaxRole role, int position) {
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
  public Optional<InvocationArityDecision> invocationArity(String consumerNodeId) {
    requireQueryId(consumerNodeId);
    return Optional.ofNullable(aggregate.methodDecisionIndex(key).invocations().get(consumerNodeId));
  }

  /**
   * Returns incoming enum name and ordinal bindings for a constructor invocation.
   *
   * @param invocationNodeId
   *          invocation node identity
   * @return copied context, or empty for an absent, ineligible, or foreign node
   */
  public Optional<EnumConstructorContextDecision> enumConstructorContext(String invocationNodeId) {
    requireQueryId(invocationNodeId);
    return Optional.ofNullable(aggregate.methodDecisionIndex(key).enumContexts().get(invocationNodeId));
  }

  /**
   * Returns the selected member and declaring type of a static body access.
   *
   * @param accessNodeId
   *          access or invocation node identity
   * @return selected static access, or empty for an absent, ineligible, or foreign node
   */
  public Optional<StaticAccessDecision> staticAccess(String accessNodeId) {
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
  public Optional<StringConcatenationDecision> stringConcatenation(String binaryNodeId) {
    requireQueryId(binaryNodeId);
    return Optional.ofNullable(aggregate.methodDecisionIndex(key).concatenations().get(binaryNodeId));
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof MethodSyntax that && key.equals(that.key) && root().equals(that.root()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, root());
  }

  @Override
  public String toString() {
    return "MethodSyntax[methodKey=" + key + ", root=" + root() + ']';
  }

  private static boolean hasErroneousSemantics(SyntaxNode node) {
    return node.type() == SyntaxType.Erroneous.INSTANCE
        || (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.ERROR);
  }

  private static void requireQueryId(String nodeId) {
    if (nodeId == null || nodeId.isBlank()) {
      throw new IllegalArgumentException("A selected-decision query requires a nonblank node identity");
    }
  }

  private static boolean hasUnresolvedSemantics(SyntaxNode node) {
    return (node.type() == SyntaxType.Unresolved.INSTANCE || node.type() instanceof SyntaxType.NoType
        || node.type() == SyntaxType.Unsupported.INSTANCE || node.type() instanceof SyntaxType.StructureUnavailable)
        || (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.UNRESOLVED)
        || (node.value() instanceof SyntaxValue.Invocation invocation && invocation.semantics().isEmpty())
        || (node.syntaxKind() == SyntaxKind.CATCH && hasUnresolvedCatch(node));
  }

  private static boolean hasUnresolvedCatch(SyntaxNode node) {
    var parameter = node.requiredChild(SyntaxRole.PARAMETER);
    return parameter.resolvedType().isEmpty()
        || parameter
            .semanticReference().map(reference -> reference.status() != ResolutionStatus.RESOLVED
                || reference.typeAlternativeNames().isEmpty() || reference.typeAlternativeNames().stream().anyMatch(String::isBlank))
            .orElse(true);
  }
}
