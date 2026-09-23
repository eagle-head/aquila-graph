package org.aquilagraph.syntax;

import java.util.Objects;
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
    if (nodes.stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED)) {
      return SyntaxReadiness.UNSUPPORTED;
    }
    if (nodes.stream().anyMatch(MethodSyntax::hasErroneousSemantics)) {
      return SyntaxReadiness.ERRONEOUS_SEMANTICS;
    }
    if (nodes.stream().anyMatch(MethodSyntax::hasUnresolvedSemantics)) {
      return SyntaxReadiness.UNRESOLVED_SEMANTICS;
    }
    return SyntaxReadiness.LOWERABLE;
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

  private static boolean hasUnresolvedSemantics(SyntaxNode node) {
    return node.type() == SyntaxType.Unresolved.INSTANCE
        || (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.UNRESOLVED)
        || (node.value() instanceof SyntaxValue.Invocation invocation && invocation.semantics().isEmpty());
  }
}
