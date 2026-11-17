package org.aquilagraph.source.api;

import java.util.List;
import java.util.Optional;

/** Checks selected invocation relationships using only independently copied source values. */
final class InvocationSyntaxValidation {
  private InvocationSyntaxValidation() {
  }

  static void validate(SyntaxKind kind, SyntaxType result, SyntaxValue.Invocation invocation, List<SyntaxChild> children,
      Optional<SemanticReference> reference) {
    invocation.semantics().ifPresent(semantics -> {
      if (semantics.selection() == InvocationSelection.ARRAY_CLONE) {
        validateArrayClone(kind, result, semantics, children);
      } else if (reference.isEmpty() || reference.orElseThrow()
          .referenceKind() != (semantics.mode() == InvocationMode.CONSTRUCTOR ? ReferenceKind.CONSTRUCTOR : ReferenceKind.METHOD)) {
        throw new IllegalArgumentException("A named invocation requires its matching executable reference");
      } else if ("clone".equals(invocation.name()) && reference.orElseThrow().status() == ResolutionStatus.RESOLVED
          && children.stream().filter(child -> child.role() == SyntaxRole.RECEIVER).anyMatch(
              child -> child.node().type().knownIdentity().filter(type -> type.category() == JavaTypeCategory.ARRAY).isPresent())) {
        throw new IllegalArgumentException("A resolved array clone cannot select a named executable declaration");
      }
    });
  }

  private static void validateArrayClone(SyntaxKind kind, SyntaxType result, InvocationSemantics semantics, List<SyntaxChild> children) {
    if (kind != SyntaxKind.INVOCATION || children.stream().anyMatch(child -> child.role() == SyntaxRole.ARGUMENT)) {
      throw new IllegalArgumentException("Array clone requires an invocation with no arguments");
    }
    var receiver = children.stream().filter(child -> child.role() == SyntaxRole.RECEIVER).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Array clone requires its written receiver"));
    if (!(receiver.node().type() instanceof SyntaxType.Resolved receiverType)
        || !(receiverType.structure().orElse(null) instanceof JavaTypeStructure.Array) || !receiverType.equals(result)
        || !semantics.qualifyingType().identity().equals(receiverType.identity())) {
      throw new IllegalArgumentException("Array clone requires the same exact resolved array receiver, result, and qualification");
    }
  }
}
