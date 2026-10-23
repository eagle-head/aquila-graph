package org.aquilagraph.source.api;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Intrinsic header ordering and clause consistency for written iteration statements. */
final class IterationSyntax {
  private IterationSyntax() {
  }

  static void validate(SyntaxKind kind, List<SyntaxChild> children) {
    var order = switch (kind) {
      case WHILE_LOOP -> List.of(SyntaxRole.CONDITION, SyntaxRole.BODY);
      case DO_WHILE_LOOP -> List.of(SyntaxRole.BODY, SyntaxRole.CONDITION);
      case FOR_LOOP -> List.of(SyntaxRole.INITIALIZER, SyntaxRole.CONDITION, SyntaxRole.UPDATE, SyntaxRole.BODY);
      case ENHANCED_FOR_LOOP -> List.of(SyntaxRole.PARAMETER, SyntaxRole.ITERABLE, SyntaxRole.BODY);
      default -> List.<SyntaxRole>of();
    };
    if (order.isEmpty()) {
      return;
    }
    var previous = -1;
    @Nullable
    SyntaxKind initializerKind = null;
    for (var child : children) {
      var position = order.indexOf(child.role());
      if (position < previous) {
        throw new IllegalArgumentException("Iteration children must retain written clause order");
      }
      previous = position;
      if (child.role() == SyntaxRole.INITIALIZER) {
        if (initializerKind != null && initializerKind != child.node().syntaxKind()) {
          throw new IllegalArgumentException("Basic-for initialization cannot mix declarations and expression statements");
        }
        initializerKind = child.node().syntaxKind();
      }
    }
  }
}
