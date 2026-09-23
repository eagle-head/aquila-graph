package org.aquilagraph.frontend;

import java.util.Objects;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;

/** Compiler-neutral body awaiting source-AST contribution assembly. */
record TranslatedMethodBody(String methodKey, SyntaxNode root) {
  TranslatedMethodBody {
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(root, "root");
    if (methodKey.isBlank() || root.syntaxKind() != SyntaxKind.BLOCK) {
      throw new IllegalArgumentException("A translated method body requires a method identity and block root");
    }
  }
}
