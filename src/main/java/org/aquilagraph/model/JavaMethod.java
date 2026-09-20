package org.aquilagraph.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record JavaMethod(
    String methodKey,
    String ownerTypeKey,
    String name,
    String signature,
    String returnType,
    List<JavaParameter> parameters,
    Set<String> modifiers,
    boolean constructor,
    boolean sourceDeclared,
    Optional<MethodBody> body,
    List<SourceSpan> origins) {
  public JavaMethod {
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(ownerTypeKey, "ownerTypeKey");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(signature, "signature");
    Objects.requireNonNull(returnType, "returnType");
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    modifiers =
        Collections.unmodifiableSet(
            new TreeSet<>(Objects.requireNonNull(modifiers, "modifiers")));
    body = Objects.requireNonNull(body, "body");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    if ((modifiers.contains("abstract") || modifiers.contains("native")) && body.isPresent()) {
      throw new IllegalArgumentException("Abstract and native methods cannot own a method body");
    }
  }

  public boolean hasBody() {
    return body.isPresent();
  }
}
