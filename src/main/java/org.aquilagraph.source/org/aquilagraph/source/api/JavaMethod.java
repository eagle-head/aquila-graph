package org.aquilagraph.source.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Translated Java method declaration.
 *
 * @param methodKey
 *          stable method identifier
 * @param ownerTypeKey
 *          declaring type identifier
 * @param name
 *          source or constructor name
 * @param signature
 *          canonical method signature
 * @param returnType
 *          compiler-reported return type
 * @param parameters
 *          ordered formal parameters
 * @param modifiers
 *          declaration modifiers
 * @param constructor
 *          whether this declaration is a constructor
 * @param sourceDeclared
 *          whether the declaration has a source origin
 * @param hasBody
 *          whether the source declaration has a body
 * @param origins
 *          declaration source locations
 */
public record JavaMethod(String methodKey, String ownerTypeKey, String name, String signature, String returnType,
    List<JavaParameter> parameters, Set<String> modifiers, boolean constructor, boolean sourceDeclared, boolean hasBody,
    List<SourceSpan> origins) {
  /** Validates and defensively copies method data. */
  public JavaMethod {
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(ownerTypeKey, "ownerTypeKey");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(signature, "signature");
    Objects.requireNonNull(returnType, "returnType");
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    modifiers = Collections.unmodifiableSet(new TreeSet<>(Objects.requireNonNull(modifiers, "modifiers")));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    if ((modifiers.contains("abstract") || modifiers.contains("native")) && hasBody) {
      throw new IllegalArgumentException("Abstract and native methods cannot own a method body");
    }
  }
}
