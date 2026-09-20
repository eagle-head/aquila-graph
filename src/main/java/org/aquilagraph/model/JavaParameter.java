package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

public record JavaParameter(
    String parameterKey, String name, String staticType, List<SourceSpan> origins) {
  public JavaParameter {
    Objects.requireNonNull(parameterKey, "parameterKey");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(staticType, "staticType");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
  }
}
