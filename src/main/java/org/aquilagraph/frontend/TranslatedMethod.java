package org.aquilagraph.frontend;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.syntax.SyntaxNode;

record TranslatedMethod(JavaMethod method, Optional<SyntaxNode> syntax) {
  TranslatedMethod {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(syntax, "syntax");
    if (method.hasBody() != syntax.isPresent()) {
      throw new IllegalArgumentException("Method declaration and syntax body presence must agree");
    }
    syntax.ifPresent(value -> {
      value.descendantsAndSelf();
    });
  }
}
