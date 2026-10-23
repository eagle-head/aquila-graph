package org.aquilagraph.frontend.internal;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.JavaMethod;

record TranslatedMethod(JavaMethod method, Optional<TranslatedMethodBody> body) {
  TranslatedMethod {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(body, "body");
    if (method.hasBody() != body.isPresent()) {
      throw new IllegalArgumentException("Method declaration and syntax body presence must agree");
    }
    body.ifPresent(value -> {
      if (!method.methodKey().equals(value.methodKey())) {
        throw new IllegalArgumentException("Method declaration and body identity must agree");
      }
      value.root().descendantsAndSelf();
    });
  }
}
