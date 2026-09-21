package org.aquilagraph.frontend;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.syntax.MethodSyntax;

record TranslatedMethod(JavaMethod method, Optional<MethodSyntax> syntax) {
  TranslatedMethod {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(syntax, "syntax");
    if (method.hasBody() != syntax.isPresent()) {
      throw new IllegalArgumentException("Method declaration and syntax body presence must agree");
    }
    syntax.ifPresent(value -> {
      if (!value.methodKey().equals(method.methodKey())) {
        throw new IllegalArgumentException("Method declaration and syntax must share an identity");
      }
    });
  }
}
