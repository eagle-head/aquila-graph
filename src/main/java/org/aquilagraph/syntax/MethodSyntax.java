package org.aquilagraph.syntax;

import java.util.Objects;
import org.aquilagraph.model.ResolutionStatus;

/**
 * Current method-level owner of durable in-memory source occurrences for one method body.
 *
 * <p>
 * Aggregate ownership is introduced by the later foundation tasks; this record currently supplies the existing zero-copy semantic lowering
 * view.
 *
 * @param methodKey
 *          stable identity of the method whose body was translated
 * @param root
 *          root block of the retained syntax tree
 */
public record MethodSyntax(String methodKey, SyntaxNode root) {
  /** Validates method identity and tree ownership. */
  public MethodSyntax {
    Objects.requireNonNull(methodKey, "methodKey");
    if (methodKey.isBlank()) {
      throw new IllegalArgumentException("Method syntax identity cannot be blank");
    }
    Objects.requireNonNull(root, "root").descendantsAndSelf();
    if (root.syntaxKind() != SyntaxKind.BLOCK) {
      throw new IllegalArgumentException("A method syntax root must be a block");
    }
  }

  /**
   * Derives whether syntax is accepted and has every semantic prerequisite required by graph lowering.
   *
   * @return derived readiness for direct graph lowering
   */
  public SyntaxReadiness readiness() {
    var nodes = root.descendantsAndSelf();
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
