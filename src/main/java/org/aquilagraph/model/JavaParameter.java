package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

/**
 * Formal parameter declared by a translated Java method.
 *
 * @param parameterKey
 *          stable parameter identifier
 * @param name
 *          source name
 * @param staticType
 *          compiler-reported type
 * @param origins
 *          declaration source locations
 */
public record JavaParameter(String parameterKey, String name, String staticType, List<SourceSpan> origins) {
  /** Validates and defensively copies parameter data. */
  public JavaParameter {
    Objects.requireNonNull(parameterKey, "parameterKey");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(staticType, "staticType");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
  }
}
