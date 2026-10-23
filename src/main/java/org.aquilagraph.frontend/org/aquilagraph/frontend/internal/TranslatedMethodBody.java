package org.aquilagraph.frontend.internal;

import java.util.Objects;
import org.aquilagraph.frontend.internal.JavaMethodBodyTranslator.BodyProjection;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;

/** Session-local body projection awaiting independent executable correlation. */
record TranslatedMethodBody(String methodKey, SyntaxNode root, BodyProjection projection) {
  TranslatedMethodBody {
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(projection, "projection");
    if (methodKey.isBlank() || root.syntaxKind() != SyntaxKind.BLOCK) {
      throw new IllegalArgumentException("A translated method body requires a method identity and block root");
    }
  }

  JavaBodyDecisions decisions() {
    return projection.decisions();
  }
}
