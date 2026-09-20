package org.aquilagraph.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record JavaProgram(List<JavaType> types) {
  public JavaProgram {
    types = List.copyOf(Objects.requireNonNull(types, "types"));
    var typeKeys = new HashSet<String>();
    var methodKeys = new HashSet<String>();
    for (var type : types) {
      if (!typeKeys.add(Objects.requireNonNull(type, "type").typeKey())) {
        throw new IllegalArgumentException("Java type keys must be unique");
      }
      for (var method : type.methods()) {
        if (!methodKeys.add(method.methodKey())) {
          throw new IllegalArgumentException(
              "Java method keys must be unique across the program");
        }
      }
    }
  }

  public List<JavaMethod> methods() {
    return types.stream().flatMap(type -> type.methods().stream()).toList();
  }
}
